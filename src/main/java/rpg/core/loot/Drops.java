package rpg.core.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.RpgCore;
import rpg.core.combat.MobData;
import rpg.core.data.PlayerData;
import rpg.core.equip.Gear;
import rpg.core.equip.GearDefs;
import rpg.core.item.ModItems;

/** 몹 드롭 (drops.json). 확률은 % 이고 운(LUK) 드롭률 보너스가 곱해짐. 골드 주머니는 보너스 없이 항상 */
public final class Drops {
    private Drops() {}

    public record Entry(String item, double chance) {}
    public record MobDrops(List<Entry> items, double potion, double gear, double enhance, List<Entry> uniques) {}

    public static double ownerSeconds = 30;
    public static double goldChance = 100;
    public static double potionChance = 8, potionRed = 70;
    /** 몹 레벨별 물약 등급 (minLevel 이상 중 가장 높은 것): HP 물약 id · MP 물약 id */
    public record PotionTier(int minLevel, String hp, String mp) {}
    public static final List<PotionTier> POTION_TIERS = new ArrayList<>();
    public static double gearChance = 2;
    public static int gearLevelRange = 10;
    public static double enhanceChance = 1.5;
    /** 귀환 주문서 드롭 확률 % (drops.json returnScroll) */
    public static double returnChance = 0.5;
    /** 잠재능력 부여 주문서 드롭 확률 % (drops.json potentialScroll) */
    public static double potentialChance = 0.2;
    public static final Map<String, Double> ENHANCE = new LinkedHashMap<>();
    public static final Map<String, MobDrops> MOBS = new HashMap<>();

    public static void parse(String text) {
        MOBS.clear();
        ENHANCE.clear();
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            ownerSeconds = d(o, "ownerSeconds", 30);
            returnChance = d(o, "returnScroll", 0.5);
            potentialChance = d(o, "potentialScroll", 0.2);
            goldChance = d(o, "goldChance", 100);
            if (o.has("potion")) {
                JsonObject p = o.getAsJsonObject("potion"); potionChance = d(p, "chance", 8); potionRed = d(p, "redRatio", 70);
                POTION_TIERS.clear();
                if (p.has("tiers")) for (var te : p.getAsJsonArray("tiers")) {
                    JsonObject t = te.getAsJsonObject();
                    POTION_TIERS.add(new PotionTier((int) d(t, "minLevel", 1), t.get("hp").getAsString(), t.get("mp").getAsString()));
                }
            }
            if (o.has("gear")) { JsonObject g = o.getAsJsonObject("gear"); gearChance = d(g, "chance", 2); gearLevelRange = (int) d(g, "levelRange", 10); }
            if (o.has("enhance")) {
                JsonObject en = o.getAsJsonObject("enhance");
                enhanceChance = d(en, "chance", 1.5);
                if (en.has("items")) for (var e : en.getAsJsonObject("items").entrySet()) ENHANCE.put(e.getKey(), e.getValue().getAsDouble());
            }
            if (o.has("mobs")) for (var e : o.getAsJsonObject("mobs").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject m = e.getValue().getAsJsonObject();
                List<Entry> list = new ArrayList<>();
                if (m.has("items")) for (var x : m.getAsJsonArray("items")) {
                    JsonArray a = x.getAsJsonArray();
                    list.add(new Entry(a.get(0).getAsString(), a.get(1).getAsDouble()));
                }
                List<Entry> uq = new ArrayList<>();
                if (m.has("uniques")) for (var x : m.getAsJsonArray("uniques")) {
                    JsonArray a = x.getAsJsonArray();
                    uq.add(new Entry(a.get(0).getAsString(), a.get(1).getAsDouble()));
                }
                MOBS.put(e.getKey(), new MobDrops(list, d(m, "potion", -1), d(m, "gear", -1), d(m, "enhance", -1), uq));
            }
            RpgCore.LOG.info("[rpgcore] 드롭표 {}종 읽음", MOBS.size());
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] drops.json 읽기 실패: {}", ex.toString());
        }
    }

    private static double d(JsonObject o, String k, double def) {
        return o.has(k) ? o.get(k).getAsDouble() : def;
    }

    /** 몹 레벨에 맞는 물약 (drops.json potion.tiers). 없으면 빨강·파랑 */
    private static net.minecraft.world.item.Item potionFor(int level, boolean hp) {
        PotionTier best = null;
        for (PotionTier t : POTION_TIERS) if (t.minLevel() <= level && (best == null || t.minLevel() > best.minLevel())) best = t;
        if (best != null) {
            var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(RpgCore.MODID, hp ? best.hp() : best.mp()));
            if (it != null && it != net.minecraft.world.item.Items.AIR) return it;
        }
        return hp ? ModItems.RED_POTION.get() : ModItems.BLUE_POTION.get();
    }

    /** 처치 시 막타 플레이어 기준으로 굴림 */
    public static void roll(Mob mob, MobData m, ServerPlayer killer, PlayerData d) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double mul = 1.0 + d.dropBonus() / 100.0;
        List<ItemStack> out = new ArrayList<>();

        // 골드 주머니
        long gold = Math.round(m.gold * (1.0 + d.goldBonus() / 100.0) * (0.85 + r.nextDouble() * 0.3));
        if (gold > 0 && r.nextDouble() * 100 < goldChance) out.add(GoldPouch.of(gold));

        MobDrops md = MOBS.get(m.key);
        int rolls = m.elite ? 2 : 1;   // 엘리트는 재료 두 번 굴림
        for (int k = 0; k < rolls; k++) if (md != null) for (Entry e : md.items()) if (hit(r, e.chance() * mul)) add(out, e.item());

        if (returnChance > 0 && hit(r, returnChance * mul)) out.add(new ItemStack(ModItems.RETURN_SCROLL.get()));
        if (potentialChance > 0 && hit(r, potentialChance * mul)) out.add(new ItemStack(ModItems.POTENTIAL_SCROLL.get()));
        double pc = md != null && md.potion() >= 0 ? md.potion() : potionChance;
        if (hit(r, pc * mul)) out.add(new ItemStack(potionFor(m.level, r.nextDouble() * 100 < potionRed)));

        double gc = (md != null && md.gear() >= 0 ? md.gear() : gearChance) * (m.elite ? rpg.core.balance.Balance.eliteGear : 1);
        if (gc > 0 && hit(r, gc * mul)) {
            ItemStack g = randomGear(m.level, r);
            if (!g.isEmpty()) out.add(g);
        }

        // 몹 전용 장비: 확률 × 드롭률 × 엘리트 배수, 추가 옵션 4줄
        if (md != null) for (Entry u : md.uniques()) {
            if (!hit(r, u.chance() * mul * (m.elite ? rpg.core.balance.Balance.eliteGear : 1))) continue;
            List<ItemStack> one = new ArrayList<>();
            add(one, u.item());
            for (ItemStack g : one) { Gear.roll(g); out.add(g); }
        }

        double ec = md != null && md.enhance() >= 0 ? md.enhance() : enhanceChance;
        if (!ENHANCE.isEmpty() && hit(r, ec * mul)) {
            String pick = weighted(ENHANCE, r);
            if (pick.equals("scroll")) out.add(rpg.core.equip.Enhance.randomScroll(m.level, r));   // 주문서: 몹 레벨로 등급 제한
            else add(out, pick);
        }

        for (ItemStack s : out) spawn(mob, s, killer);
    }

    private static boolean hit(ThreadLocalRandom r, double pct) {
        return r.nextDouble() * 100 < pct;
    }

    private static void add(List<ItemStack> out, String id) {
        ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) : RpgCore.id(id);
        Item it = ForgeRegistries.ITEMS.getValue(rl);
        if (it != null && it != net.minecraft.world.item.Items.AIR) out.add(new ItemStack(it));
        else RpgCore.LOG.warn("[rpgcore] 드롭 아이템 없음: {}", id);
    }

    /** 보스 보상: 그 몹의 재료 드롭표를 times 번 굴림 (확률 × mul) */
    public static void rollMats(String mobKey, int times, double mul, List<ItemStack> out) {
        MobDrops md = MOBS.get(mobKey);
        if (md == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < times; k++) for (Entry e : md.items()) if (hit(r, e.chance() * mul)) add(out, e.item());
    }

    private static String weighted(Map<String, Double> w, ThreadLocalRandom r) {
        double total = 0;
        for (double v : w.values()) total += v;
        double pick = r.nextDouble() * total;
        for (var e : w.entrySet()) { pick -= e.getValue(); if (pick < 0) return e.getKey(); }
        return w.keySet().iterator().next();
    }

    /** 몹 레벨 이하, levelRange 안쪽 장비 중 무작위 (추가 옵션 굴림) */
    private static ItemStack randomGear(int lv, ThreadLocalRandom r) {
        List<String> pool = new ArrayList<>();
        for (GearDefs.GearDef g : GearDefs.ITEMS.values()) if (g.level() <= lv && g.level() > lv - gearLevelRange) pool.add(g.id());
        if (pool.isEmpty()) for (GearDefs.GearDef g : GearDefs.ITEMS.values()) if (g.level() <= lv) pool.add(g.id());
        if (pool.isEmpty()) return ItemStack.EMPTY;
        Item it = ForgeRegistries.ITEMS.getValue(RpgCore.id(pool.get(r.nextInt(pool.size()))));
        if (it == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(it);
        Gear.roll(s);
        return s;
    }

    private static void spawn(Mob mob, ItemStack s, ServerPlayer owner) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ItemEntity ie = new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.5, mob.getZ(), s,
                (r.nextDouble() - 0.5) * 0.25, 0.25, (r.nextDouble() - 0.5) * 0.25);
        ie.setPickUpDelay(10);
        Loot.claim(ie, owner, ownerSeconds);
        mob.level().addFreshEntity(ie);
    }

    public static Component ownerTag(ServerPlayer p) {
        return Component.literal("◆ " + p.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW);
    }
}
