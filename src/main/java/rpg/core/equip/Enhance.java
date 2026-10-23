package rpg.core.equip;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.item.ModItems;
import rpg.core.item.Scroll;

/**
 * 장비 강화: 스타포스 · 주문서 · 파괴 복구 · 주문서 조각 합성.
 * 수치는 equipment.json "enhance" (클라이언트도 같은 값을 받아서 미리보기 계산).
 * 장비 NBT: rpgStar(★ 수) · rpgBroken(파괴됨) · rpgUpg(남은 업그레이드 횟수) · rpgUpgFail(실패 횟수) · rpgScr(주문서로 오른 수치)
 * 스타포스 수치는 저장하지 않고 ★ 수로 매번 계산 (기본 수치 기준, 주문서와 무관)
 */
public final class Enhance {
    private Enhance() {}

    // ───────── 설정 (기본값 = 초안) ─────────
    public static int[][] maxStars = {{1, 5}, {10, 8}, {20, 10}, {30, 12}, {40, 15}};
    public static double[] rates = {95, 90, 85, 85, 80, 75, 70, 65, 60, 55, 50, 45, 40, 35, 30};
    public static double[] destroy = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.6, 1.3, 1.4};
    public static int[] starStat = {1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    public static double costBase = 30, safeguardMul = 3, restoreMul = 5;
    public static int restoreStar = 12, safeguardFrom = 12;
    public static double weaponPct = 2, weaponFlat = 1, defPct = 5;
    public static int[] gloveAtkStars = {5, 7, 9, 11, 13, 14, 15};
    public static final Map<String, Integer> UPGRADES = new LinkedHashMap<>(Map.of("weapon", 7, "armor", 5, "accessory", 3, "medal", 0));
    /** 등급별 [성공률, 공격력/마력, 주 스탯] (무기) · [성공률, 스탯] (방어구·장신구) */
    public static int[][] weaponScroll = {{100, 1, 1}, {60, 2, 1}, {30, 3, 2}, {10, 5, 3}};
    public static int[][] statScroll = {{100, 1}, {60, 2}, {30, 3}, {10, 5}};
    public static double whiteRate = 30;
    /** 드롭·합성 등급 비중 · 등급이 나오기 시작하는 레벨 */
    public static int[] gradeWeight = {50, 30, 15, 5};
    public static int[] gradeMinLevel = {1, 1, 20, 30};
    public static int whiteMinLevel = 30;
    public static double whiteChance = 3;
    public static int fragments = 10;

    public static final String[] STATS4 = {"str", "dex", "int", "luk"};

    public static void parse(JsonObject o) {
        if (o == null) return;
        try {
            if (o.has("maxStars")) {
                JsonArray a = o.getAsJsonArray("maxStars");
                maxStars = new int[a.size()][2];
                for (int i = 0; i < a.size(); i++) {
                    maxStars[i][0] = a.get(i).getAsJsonArray().get(0).getAsInt();
                    maxStars[i][1] = a.get(i).getAsJsonArray().get(1).getAsInt();
                }
            }
            if (o.has("rates")) rates = dArr(o.getAsJsonArray("rates"));
            if (o.has("destroy")) destroy = dArr(o.getAsJsonArray("destroy"));
            if (o.has("starStat")) starStat = iArr(o.getAsJsonArray("starStat"));
            if (o.has("gloveAtkStars")) gloveAtkStars = iArr(o.getAsJsonArray("gloveAtkStars"));
            if (o.has("weaponScroll")) weaponScroll = iArr2(o.getAsJsonArray("weaponScroll"));
            if (o.has("statScroll")) statScroll = iArr2(o.getAsJsonArray("statScroll"));
            if (o.has("gradeWeight")) gradeWeight = iArr(o.getAsJsonArray("gradeWeight"));
            if (o.has("gradeMinLevel")) gradeMinLevel = iArr(o.getAsJsonArray("gradeMinLevel"));
            if (o.has("upgrades")) for (var e : o.getAsJsonObject("upgrades").entrySet()) UPGRADES.put(e.getKey(), e.getValue().getAsInt());
            costBase = d(o, "costBase", costBase);
            safeguardMul = d(o, "safeguardMul", safeguardMul);
            restoreMul = d(o, "restoreMul", restoreMul);
            restoreStar = (int) d(o, "restoreStar", restoreStar);
            safeguardFrom = (int) d(o, "safeguardFrom", safeguardFrom);
            weaponPct = d(o, "weaponPct", weaponPct);
            weaponFlat = d(o, "weaponFlat", weaponFlat);
            defPct = d(o, "defPct", defPct);
            whiteRate = d(o, "whiteRate", whiteRate);
            whiteMinLevel = (int) d(o, "whiteMinLevel", whiteMinLevel);
            whiteChance = d(o, "whiteChance", whiteChance);
            fragments = (int) d(o, "fragments", fragments);
        } catch (Exception ex) {
            rpg.core.RpgCore.LOG.error("[rpgcore] equipment.json enhance 읽기 실패", ex);
        }
    }

    private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }
    private static double[] dArr(JsonArray a) { double[] r = new double[a.size()]; for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsDouble(); return r; }
    private static int[] iArr(JsonArray a) { int[] r = new int[a.size()]; for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt(); return r; }
    private static int[][] iArr2(JsonArray a) { int[][] r = new int[a.size()][]; for (int i = 0; i < r.length; i++) r[i] = iArr(a.get(i).getAsJsonArray()); return r; }

    // ───────── 장비 상태 ─────────
    public static String cat(String slot) {
        return switch (slot) {
            case "weapon" -> "weapon";
            case "earring", "ring", "pendant" -> "accessory";
            case "medal" -> "medal";
            default -> "armor";
        };
    }

    public static int star(ItemStack s) { return s.hasTag() ? s.getTag().getInt("rpgStar") : 0; }
    public static boolean broken(ItemStack s) { return s.hasTag() && s.getTag().getBoolean("rpgBroken"); }

    public static int maxStar(GearDefs.GearDef d) {
        if (d == null || cat(d.slot()).equals("medal")) return 0;
        int m = 0;
        for (int[] e : maxStars) if (d.level() >= e[0]) m = e[1];
        return Math.min(m, rates.length);
    }

    public static int upgMax(GearDefs.GearDef d) { return d == null ? 0 : UPGRADES.getOrDefault(cat(d.slot()), 0); }

    public static int upgLeft(ItemStack s) {
        GearDefs.GearDef d = Gear.def(s);
        if (d == null) return 0;
        return s.hasTag() && s.getTag().contains("rpgUpg") ? s.getTag().getInt("rpgUpg") : upgMax(d);
    }

    public static int upgFail(ItemStack s) { return s.hasTag() ? s.getTag().getInt("rpgUpgFail") : 0; }

    public static CompoundTag scr(ItemStack s) { return s.hasTag() ? s.getTag().getCompound("rpgScr") : new CompoundTag(); }

    public static double rate(int star) { return star < rates.length ? rates[star] : 0; }
    public static double destroyRate(int star) { return star < destroy.length ? destroy[star] : 0; }

    public static long cost(GearDefs.GearDef d, int star, boolean safeguard) {
        double c = (costBase + (double) d.level() * d.level()) * (star + 1);
        if (safeguard && destroyRate(star) > 0) c *= safeguardMul;
        return Math.round(c / 10.0) * 10;
    }

    public static long restoreCost(GearDefs.GearDef d) {
        return Math.round(cost(d, restoreStar, false) * restoreMul / 10.0) * 10;
    }

    /** 무기 공격 키: 기본 마력이 있으면 마력, 아니면 공격력 */
    public static String weaponKey(GearDefs.GearDef d) {
        return d.stats().getOrDefault("matk", 0.0) > 0 ? "matk" : "atk";
    }

    /** 무기 주 스탯 (직업 기준) */
    public static String mainStat(int job) {
        return switch (job) { case 2 -> "dex"; case 3 -> "int"; case 4 -> "luk"; default -> "str"; };
    }

    /** ★ 수만큼 오른 수치 (기본 수치 기준으로 누적 계산) */
    public static Map<String, Integer> starStats(GearDefs.GearDef d, CompoundTag scr, int stars) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (d == null || stars <= 0) return m;
        String c = cat(d.slot());
        String wk = weaponKey(d);
        for (int i = 0; i < stars && i < starStat.length; i++) {
            int n = i + 1;   // 이번에 얻는 ★
            for (String k : STATS4) m.merge(k, starStat[i], Integer::sum);
            if (c.equals("weapon")) {
                double cur = d.stats().getOrDefault(wk, 0.0) + m.getOrDefault(wk, 0);   // 주문서 수치는 안 넣음 (기본 + 앞의 ★만)
                if (cur > 0) m.merge(wk, (int) Math.floor(cur * weaponPct / 100.0 + weaponFlat), Integer::sum);
            } else if (c.equals("armor")) {
                double cur = d.stats().getOrDefault("def", 0.0) + m.getOrDefault("def", 0);
                m.merge("def", Math.max(1, (int) Math.floor(cur * defPct / 100.0)), Integer::sum);
                if (d.slot().equals("gloves")) for (int g : gloveAtkStars) if (g == n) { m.merge("atk", 1, Integer::sum); m.merge("matk", 1, Integer::sum); }
            }
        }
        return m;
    }

    public static Map<String, Integer> starStats(ItemStack s) {
        return starStats(Gear.def(s), scr(s), star(s));
    }

    /** 장비 합산에 더할 강화 수치 (주문서 + 스타포스) */
    public static void addBonus(ItemStack s, Map<String, Double> m) {
        CompoundTag sc = scr(s);
        for (String k : sc.getAllKeys()) m.merge(k, (double) sc.getInt(k), Double::sum);
        starStats(s).forEach((k, v) -> m.merge(k, (double) v, Double::sum));
    }

    // ───────── 위치 (0~35 인벤 · 100~103 갑옷 칸 · 201~210 장비 칸) ─────────
    public static ItemStack at(ServerPlayer p, int loc) {
        if (loc >= 0 && loc < 36) return p.getInventory().items.get(loc);
        if (loc >= 100 && loc < 104) return p.getInventory().armor.get(loc - 100);
        if (loc > 200 && loc < 200 + PlayerEquip.SLOT_TYPES.length) return PlayerEquip.get(p).items.getStackInSlot(loc - 200);
        return ItemStack.EMPTY;
    }

    /** 강화 창에 보여줄 장비 목록 (장착 → 인벤 순) */
    public static List<Integer> gearLocs(ServerPlayer p) {
        List<Integer> l = new ArrayList<>();
        int sel = p.getInventory().selected;
        if (Gear.def(at(p, sel)) != null) l.add(sel);
        for (int i = 3; i >= 0; i--) if (Gear.def(at(p, 100 + i)) != null) l.add(100 + i);
        for (int i = 1; i < PlayerEquip.SLOT_TYPES.length; i++) if (Gear.def(at(p, 200 + i)) != null) l.add(200 + i);
        for (int i = 0; i < 36; i++) if (i != sel && Gear.def(at(p, i)) != null) l.add(i);
        return l;
    }

    private static void changed(ServerPlayer p) {
        PlayerEquip.get(p).dirty = true;
        p.getInventory().setChanged();
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }

    private static void sound(ServerPlayer p, SoundEvent s, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, 0.8f, pitch);
    }

    private static boolean pay(ServerPlayer p, PlayerData d, long gold) {
        if (d.gold < gold) { msg(p, "골드가 부족해요 (" + String.format("%,d", gold) + " G 필요)", ChatFormatting.RED); return false; }
        d.gold -= gold;
        return true;
    }

    // ───────── 스타포스 ─────────
    public static void starforce(ServerPlayer p, int loc, boolean safeguard) {
        ItemStack s = at(p, loc);
        GearDefs.GearDef d = Gear.def(s);
        if (d == null) return;
        if (broken(s)) { msg(p, "파괴된 장비는 먼저 복구해야 해요", ChatFormatting.RED); return; }
        int st = star(s), max = maxStar(d);
        if (st >= max) { msg(p, max == 0 ? "이 장비는 스타포스 강화를 할 수 없어요" : "이미 최대 ★" + max + "이에요", ChatFormatting.RED); return; }
        boolean guard = safeguard && destroyRate(st) > 0;
        PlayerData pd = RpgData.get(p);
        if (!pay(p, pd, cost(d, st, guard))) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double roll = r.nextDouble() * 100;
        if (roll < rate(st)) {
            s.getOrCreateTag().putInt("rpgStar", st + 1);
            msg(p, "강화 성공! ★" + (st + 1), ChatFormatting.GOLD);
            rpg.core.quest.Achievements.onStar(p, st + 1);
            sound(p, SoundEvents.PLAYER_LEVELUP, 1.4f);
        } else if (!guard && roll < rate(st) + destroyRate(st)) {
            s.getOrCreateTag().putBoolean("rpgBroken", true);
            msg(p, "장비가 파괴됐어요… (같은 장비로 복구 가능)", ChatFormatting.DARK_RED);
            rpg.core.quest.Achievements.onDestroy(p);
            sound(p, SoundEvents.ITEM_BREAK, 0.8f);
        } else {
            msg(p, "강화 실패 (★" + st + " 유지)", ChatFormatting.GRAY);
            sound(p, SoundEvents.ANVIL_LAND, 1.2f);
        }
        changed(p);
        RpgData.sync(p);
    }

    // ───────── 파괴 복구 (같은 장비 1개 + 골드 → ★12) ─────────
    public static void restore(ServerPlayer p, int loc, int matLoc) {
        ItemStack s = at(p, loc);
        GearDefs.GearDef d = Gear.def(s);
        if (d == null || !broken(s)) return;
        if (matLoc == loc || matLoc < 0 || matLoc >= 36) { msg(p, "복구 재료로 쓸 같은 장비를 인벤에서 골라주세요", ChatFormatting.RED); return; }
        ItemStack m = at(p, matLoc);
        if (m.isEmpty() || m.getItem() != s.getItem() || broken(m)) { msg(p, "같은 장비(파괴되지 않은 것)가 필요해요", ChatFormatting.RED); return; }
        PlayerData pd = RpgData.get(p);
        if (!pay(p, pd, restoreCost(d))) return;
        m.shrink(1);
        s.getOrCreateTag().remove("rpgBroken");
        s.getOrCreateTag().putInt("rpgStar", Math.min(star(s), restoreStar));
        msg(p, "복구 완료! ★" + star(s), ChatFormatting.GREEN);
        sound(p, SoundEvents.ANVIL_USE, 1.0f);
        changed(p);
        RpgData.sync(p);
    }

    /** 복구 재료 후보 (인벤에서 같은 아이템, 파괴 안 된 것) */
    public static int findMaterial(ServerPlayer p, int loc) {
        ItemStack s = at(p, loc);
        for (int i = 0; i < 36; i++) {
            if (i == loc) continue;
            ItemStack m = p.getInventory().items.get(i);
            if (!m.isEmpty() && m.getItem() == s.getItem() && !broken(m)) return i;
        }
        return -1;
    }

    // ───────── 주문서 ─────────
    /** 이 주문서를 이 장비에 쓸 수 있으면 null, 아니면 이유 */
    public static String scrollBlock(ItemStack gear, ItemStack scroll) {
        GearDefs.GearDef d = Gear.def(gear);
        if (d == null) return "장비가 아니에요";
        String kind = Scroll.kind(scroll);
        if (kind.isEmpty()) return "주문서가 아니에요";
        if (broken(gear)) return "파괴된 장비예요";
        if (upgMax(d) == 0) return "이 장비는 주문서를 쓸 수 없어요";
        if (kind.equals("white")) return upgFail(gear) > 0 ? null : "실패한 업그레이드 횟수가 없어요";
        if (upgLeft(gear) <= 0) return "업그레이드 가능 횟수가 없어요";
        boolean weapon = cat(d.slot()).equals("weapon");
        if (kind.equals("watk") || kind.equals("wmatk")) {
            if (!weapon) return "무기에만 쓸 수 있어요";
            if (!weaponKey(d).equals(kind.equals("wmatk") ? "matk" : "atk")) return kind.equals("wmatk") ? "마력 무기(지팡이)에만 쓸 수 있어요" : "공격력 무기에만 쓸 수 있어요";
            return null;
        }
        return weapon ? "방어구·장신구에만 쓸 수 있어요" : null;
    }

    public static void applyScroll(ServerPlayer p, int loc, int slot) {
        ItemStack s = at(p, loc);
        GearDefs.GearDef d = Gear.def(s);
        if (d == null || slot < 0 || slot >= 36) return;
        ItemStack sc = p.getInventory().items.get(slot);
        String why = scrollBlock(s, sc);
        if (why != null) { msg(p, why, ChatFormatting.RED); return; }
        String kind = Scroll.kind(sc);
        int grade = Scroll.grade(sc);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        CompoundTag t = s.getOrCreateTag();
        int left = upgLeft(s);
        sc.shrink(1);
        if (kind.equals("white")) {
            if (r.nextDouble() * 100 < whiteRate) {
                t.putInt("rpgUpgFail", upgFail(s) - 1);
                t.putInt("rpgUpg", left + 1);
                msg(p, "순백의 주문서 성공! 업그레이드 횟수 +1", ChatFormatting.GOLD);
                sound(p, SoundEvents.PLAYER_LEVELUP, 1.6f);
            } else {
                msg(p, "순백의 주문서 실패", ChatFormatting.GRAY);
                sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f);
            }
        } else {
            t.putInt("rpgUpg", left - 1);
            int[] g = Scroll.isWeapon(kind) ? weaponScroll[Math.min(grade, weaponScroll.length - 1)] : statScroll[Math.min(grade, statScroll.length - 1)];
            if (r.nextDouble() * 100 < g[0]) {
                CompoundTag add = t.getCompound("rpgScr");
                if (Scroll.isWeapon(kind)) {
                    add.putInt(weaponKey(d), add.getInt(weaponKey(d)) + g[1]);
                    String ms = mainStat(d.job());
                    add.putInt(ms, add.getInt(ms) + g[2]);
                } else {
                    add.putInt(kind, add.getInt(kind) + g[1]);
                }
                t.put("rpgScr", add);
                msg(p, "주문서 성공!", ChatFormatting.GOLD);
                sound(p, SoundEvents.PLAYER_LEVELUP, 1.6f);
            } else {
                t.putInt("rpgUpgFail", upgFail(s) + 1);
                msg(p, "주문서 실패 (업그레이드 횟수만 줄었어요)", ChatFormatting.GRAY);
                sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f);
            }
        }
        changed(p);
        RpgData.sync(p);
    }

    // ───────── 주문서 조각 합성 ─────────
    public static int count(ServerPlayer p, net.minecraft.world.item.Item it) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.getItem() == it) n += s.getCount();
        return n;
    }

    public static void synth(ServerPlayer p) {
        var frag = ModItems.SCROLL_FRAGMENT.get();
        if (count(p, frag) < fragments) { msg(p, "주문서 조각이 " + fragments + "개 필요해요", ChatFormatting.RED); return; }
        int need = fragments;
        for (ItemStack s : p.getInventory().items) {
            if (need <= 0) break;
            if (s.getItem() != frag) continue;
            int take = Math.min(need, s.getCount());
            s.shrink(take);
            need -= take;
        }
        ItemStack out = randomScroll(RpgData.get(p).level, ThreadLocalRandom.current());
        msg(p, out.getHoverName().getString() + " 획득!", ChatFormatting.AQUA);
        sound(p, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f);
        if (!p.getInventory().add(out)) p.drop(out, false);
        p.getInventory().setChanged();
    }

    /** 레벨에 맞는 무작위 주문서 (드롭·합성) */
    public static ItemStack randomScroll(int level, ThreadLocalRandom r) {
        if (level >= whiteMinLevel && r.nextDouble() * 100 < whiteChance) return Scroll.make("white", 0);
        int total = 0;
        for (int i = 0; i < gradeWeight.length; i++) if (level >= gradeMinLevel[Math.min(i, gradeMinLevel.length - 1)]) total += gradeWeight[i];
        int pick = r.nextInt(Math.max(1, total)), grade = 0;
        for (int i = 0; i < gradeWeight.length; i++) {
            if (level < gradeMinLevel[Math.min(i, gradeMinLevel.length - 1)]) continue;
            pick -= gradeWeight[i];
            if (pick < 0) { grade = i; break; }
        }
        String kind = Scroll.KINDS[r.nextInt(Scroll.KINDS.length)];
        return Scroll.make(kind, grade);
    }
}
