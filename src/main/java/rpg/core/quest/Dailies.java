package rpg.core.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.combat.MobData;
import rpg.core.data.PlayerData;
import rpg.core.data.Progress;
import rpg.core.data.RpgData;
import rpg.core.item.ItemIds;
import rpg.core.item.ModItems;
import rpg.core.net.Net;

/**
 * 일일 퀘스트 (dailies.json): 매일 resetHour시(한국 시간) 초기화, 하루 count개, 내 레벨 사냥터 기준.
 * 종류: kill · collect · elite · boss. 메뉴(M) → 일일 퀘스트 창에서 완료·다시 뽑기.
 * 저장: 플레이어 영구 데이터 PERSISTED_NBT "rpgDaily" {day, rerolls, bonus, list[{k, label, t[], need, prog, done}]}
 */
public final class Dailies {
    private Dailies() {}

    public record Kind(int need, int weight, int minLevel) {}
    public static final Map<String, Kind> KINDS = new LinkedHashMap<>();
    public static int resetHour = 6, count = 3, rerolls = 1;
    public static double expPct = 15, goldMobs = 30, bossMul = 2;
    public static int fragments = 3, bonusFragments = 5, bonusPotions = 10, bonusReturn = 1;

    public static void parse(String text) {
        KINDS.clear();
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            resetHour = o.has("resetHour") ? o.get("resetHour").getAsInt() : 6;
            count = o.has("count") ? o.get("count").getAsInt() : 3;
            rerolls = o.has("rerolls") ? o.get("rerolls").getAsInt() : 1;
            if (o.has("kinds")) for (var e : o.getAsJsonObject("kinds").entrySet()) {
                JsonObject k = e.getValue().getAsJsonObject();
                KINDS.put(e.getKey(), new Kind(k.has("need") ? k.get("need").getAsInt() : 1, k.has("weight") ? k.get("weight").getAsInt() : 1,
                        k.has("minLevel") ? k.get("minLevel").getAsInt() : 1));
            }
            if (o.has("reward")) {
                JsonObject r = o.getAsJsonObject("reward");
                expPct = r.has("expPct") ? r.get("expPct").getAsDouble() : expPct;
                goldMobs = r.has("goldMobs") ? r.get("goldMobs").getAsDouble() : goldMobs;
                fragments = r.has("fragments") ? r.get("fragments").getAsInt() : fragments;
                bossMul = r.has("bossMul") ? r.get("bossMul").getAsDouble() : bossMul;
            }
            if (o.has("bonus")) {
                JsonObject b = o.getAsJsonObject("bonus");
                bonusFragments = b.has("fragments") ? b.get("fragments").getAsInt() : bonusFragments;
                bonusPotions = b.has("potions") ? b.get("potions").getAsInt() : bonusPotions;
                bonusReturn = b.has("returnScroll") ? b.get("returnScroll").getAsInt() : bonusReturn;
            }
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] dailies.json 읽기 실패: {}", ex.toString());
        }
        if (KINDS.isEmpty()) KINDS.put("kill", new Kind(40, 1, 1));
    }

    // ───────── 저장 ─────────
    private static CompoundTag data(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("rpgDaily");
    }

    private static void save(Player p, CompoundTag d) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        keep.put("rpgDaily", d);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
    }

    public static long today() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(resetHour).toLocalDate().toEpochDay();
    }

    /** 오늘 것이 아니면 새로 뽑음 */
    public static CompoundTag ensure(ServerPlayer p) {
        CompoundTag d = data(p);
        long day = today();
        if (d.getLong("day") == day && d.contains("list")) return d;
        d = new CompoundTag();
        d.putLong("day", day);
        d.putInt("rerolls", rerolls);
        d.putBoolean("bonus", false);
        ListTag list = new ListTag();
        boolean boss = false;
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            CompoundTag q = roll(p, boss, used);
            if (q.getString("k").equals("boss")) boss = true;
            used.add(q.getString("k"));
            list.add(q);
        }
        d.put("list", list);
        save(p, d);
        return d;
    }

    /** 내 레벨 사냥터 */
    private static Balance.Ground ground(int level) {
        Balance.Ground best = null;
        for (Balance.Ground g : Balance.GROUNDS.values()) {
            if (level >= g.min() && level <= g.max()) return g;
            if (g.min() <= level && (best == null || g.min() > best.min())) best = g;
        }
        if (best == null && !Balance.GROUNDS.isEmpty()) best = Balance.GROUNDS.values().iterator().next();
        return best;
    }

    /** 퀘스트 하나 뽑기 (boss 는 하루 1개, 가능하면 종류가 겹치지 않게) */
    private static CompoundTag roll(ServerPlayer p, boolean bossTaken, java.util.Set<String> used) {
        int level = RpgData.get(p).level;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<String> pool = new ArrayList<>();
        for (var e : KINDS.entrySet()) {
            if (e.getKey().equals("boss") && (bossTaken || level < e.getValue().minLevel())) continue;
            for (int i = 0; i < e.getValue().weight(); i++) pool.add(e.getKey());
        }
        List<String> fresh = new ArrayList<>(pool);
        fresh.removeIf(used::contains);
        String k = !fresh.isEmpty() ? fresh.get(r.nextInt(fresh.size())) : pool.isEmpty() ? "kill" : pool.get(r.nextInt(pool.size()));
        Kind kind = KINDS.getOrDefault(k, new Kind(40, 1, 1));
        Balance.Ground g = ground(level);
        CompoundTag q = new CompoundTag();
        q.putString("k", k);
        q.putInt("need", kind.need());
        ListTag t = new ListTag();
        String label;
        String gname = g == null ? "사냥터" : g.name();
        switch (k) {
            case "collect" -> {
                String mob = g == null || g.mobs().isEmpty() ? "pig" : g.mobs().get(r.nextInt(g.mobs().size()));
                var md = rpg.core.loot.Drops.MOBS.get(mob);
                String item = md == null || md.items().isEmpty() ? "rpgcore:pig_hide" : md.items().get(0).item();
                if (!item.contains(":")) item = RpgCore.MODID + ":" + item;
                t.add(StringTag.valueOf(item));
                label = ItemIds.stack(item, 1).getHoverName().getString() + " " + kind.need() + "개 납품";
            }
            case "elite" -> {
                if (g != null) for (String m : g.mobs()) t.add(StringTag.valueOf(m));
                label = gname + " 엘리트 " + kind.need() + "마리";
            }
            case "boss" -> label = "보스 " + kind.need() + "번 처치";
            default -> {
                if (g != null) for (String m : g.mobs()) t.add(StringTag.valueOf(m));
                label = gname + " 몹 " + kind.need() + "마리";
            }
        }
        q.put("t", t);
        q.putString("label", label);
        q.putString("g", g == null ? "" : g.mobs().isEmpty() ? "" : g.mobs().get(0));
        q.putInt("prog", 0);
        q.putBoolean("done", false);
        return q;
    }

    private static boolean targets(CompoundTag q, String mob) {
        ListTag t = q.getList("t", Tag.TAG_STRING);
        for (int i = 0; i < t.size(); i++) if (t.getString(i).equals(mob)) return true;
        return false;
    }

    private static int have(ServerPlayer p, String item) {
        Item it = ItemIds.item(item);
        if (it == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
        return n;
    }

    private static int progress(ServerPlayer p, CompoundTag q) {
        if (q.getString("k").equals("collect")) return Math.min(q.getInt("need"), have(p, q.getList("t", Tag.TAG_STRING).getString(0)));
        return q.getInt("prog");
    }

    // ───────── 처치 (QuestEvents.onKill 에서) ─────────
    public static void onKill(List<ServerPlayer> members, MobData m) {
        boolean boss = rpg.core.combat.Boss.isBoss(m);
        for (ServerPlayer p : members) {
            CompoundTag d = ensure(p);
            ListTag list = d.getList("list", Tag.TAG_COMPOUND);
            boolean changed = false;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag q = list.getCompound(i);
                if (q.getBoolean("done") || q.getInt("prog") >= q.getInt("need")) continue;
                String k = q.getString("k");
                boolean ok = switch (k) {
                    case "kill" -> targets(q, m.key);
                    case "elite" -> m.elite && targets(q, m.key);
                    case "boss" -> boss;
                    default -> false;
                };
                if (!ok) continue;
                q.putInt("prog", q.getInt("prog") + 1);
                changed = true;
                Net.itemNote(p, "§d일일 " + q.getString("label") + " " + q.getInt("prog") + "/" + q.getInt("need")
                        + (q.getInt("prog") >= q.getInt("need") ? " §a완료 가능 (M → 일일)" : ""));
            }
            if (changed) save(p, d);
        }
    }

    // ───────── 창 · 완료 · 다시 뽑기 ─────────
    public static void sync(ServerPlayer p) {
        CompoundTag d = ensure(p);
        ListTag list = d.getList("list", Tag.TAG_COMPOUND);
        List<Net.DailyRow> rows = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag q = list.getCompound(i);
            rows.add(new Net.DailyRow(q.getString("label"), progress(p, q), q.getInt("need"), q.getBoolean("done")));
        }
        long mins = java.time.Duration.between(LocalDateTime.now(ZoneId.of("Asia/Seoul")),
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusHours(resetHour).toLocalDate().plusDays(1).atTime(resetHour, 0)).toMinutes();
        Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Net.DailySync(rows, d.getInt("rerolls"), d.getBoolean("bonus"), (int) Math.max(0, mins)));
    }

    public static void action(ServerPlayer p, int op, int idx) {
        CompoundTag d = ensure(p);
        ListTag list = d.getList("list", Tag.TAG_COMPOUND);
        if (op == 1 && idx >= 0 && idx < list.size()) claim(p, d, list.getCompound(idx));
        if (op == 2 && idx >= 0 && idx < list.size() && d.getInt("rerolls") > 0 && !list.getCompound(idx).getBoolean("done")) {
            java.util.Set<String> used = new java.util.HashSet<>();
            boolean boss = false;
            for (int i = 0; i < list.size(); i++) if (i != idx) {
                used.add(list.getCompound(i).getString("k"));
                if (list.getCompound(i).getString("k").equals("boss")) boss = true;
            }
            list.set(idx, roll(p, boss, used));
            d.putInt("rerolls", d.getInt("rerolls") - 1);
            save(p, d);
        }
        sync(p);
    }

    private static void claim(ServerPlayer p, CompoundTag d, CompoundTag q) {
        if (q.getBoolean("done")) return;
        int need = q.getInt("need");
        if (progress(p, q) < need) { p.displayClientMessage(Component.literal("아직 다 못 했어요").withStyle(ChatFormatting.GRAY), true); return; }
        if (q.getString("k").equals("collect")) {   // 납품: 재료 가져감
            Item it = ItemIds.item(q.getList("t", Tag.TAG_STRING).getString(0));
            int left = need;
            for (ItemStack s : p.getInventory().items) {
                if (left <= 0) break;
                if (!s.is(it)) continue;
                int k = Math.min(left, s.getCount());
                s.shrink(k);
                left -= k;
            }
        }
        q.putBoolean("done", true);
        PlayerData pd = RpgData.get(p);
        double mul = q.getString("k").equals("boss") ? bossMul : 1;
        long exp = Math.max(1, Math.round(Balance.expNeed(pd.level) * expPct / 100.0 * mul));
        Balance.MobDef md = Balance.MOBS.get(q.getString("g"));
        long gold = Math.max(1, Math.round((md == null ? 10 : md.at(pd.level).gold()) * goldMobs * mul));
        pd.gold += gold;
        Progress.giveExp(p, exp);
        give(p, new ItemStack(ModItems.SCROLL_FRAGMENT.get(), (int) Math.round(fragments * mul)));
        Net.reward(p, exp, gold);
        p.sendSystemMessage(Component.literal("[일일] " + q.getString("label") + " 완료!  경험치 " + String.format("%,d", exp) + " · " + String.format("%,d", gold) + " G · 주문서 조각 "
                + Math.round(fragments * mul)).withStyle(ChatFormatting.LIGHT_PURPLE));
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.5f);
        // 전부 완료 보너스
        ListTag list = d.getList("list", Tag.TAG_COMPOUND);
        boolean all = true;
        for (int i = 0; i < list.size(); i++) if (!list.getCompound(i).getBoolean("done")) all = false;
        if (all && !d.getBoolean("bonus")) {
            d.putBoolean("bonus", true);
            give(p, new ItemStack(ModItems.SCROLL_FRAGMENT.get(), bonusFragments));
            Item pot = pd.level >= 35 ? ModItems.WHITE_POTION.get() : pd.level >= 20 ? ModItems.ORANGE_POTION.get() : ModItems.RED_POTION.get();
            give(p, new ItemStack(pot, bonusPotions));
            if (bonusReturn > 0) give(p, new ItemStack(ModItems.RETURN_SCROLL.get(), bonusReturn));
            p.sendSystemMessage(Component.literal("[일일] 오늘 일일 퀘스트를 다 했어요! 보너스: 주문서 조각 " + bonusFragments + " · 물약 " + bonusPotions
                    + (bonusReturn > 0 ? " · 귀환 주문서 " + bonusReturn : "")).withStyle(ChatFormatting.GOLD));
        }
        save(p, d);
        RpgData.sync(p);
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (s.isEmpty()) return;
        if (!p.getInventory().add(s)) p.drop(s, false);
    }
}
