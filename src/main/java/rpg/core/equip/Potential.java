package rpg.core.equip;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.item.ModItems;

/**
 * 잠재능력 (메이플식): 레어·에픽·유니크·레전드리, 3줄. 부여 주문서로 처음 부여.
 * 재설정 = 골드 또는 수상한 큐브 → 새 옵션이 나오면 [새 옵션 / 기존 유지] 중 고름.
 * 재설정할 때 등급 상승 (upChance%), 천장 (ceiling 번째는 확정, 플레이어마다 등급별 카운트).
 * 장비 NBT rpgPot {g, l[{k,v}], new{g, l}} · 플레이어 PERSISTED_NBT rpgPotCeil [레어, 에픽, 유니크]
 * 수치는 equipment.json "potential"
 */
public final class Potential {
    private Potential() {}

    public static final String[] GRADE = {"레어", "에픽", "유니크", "레전드리"};
    public static final int[] COLOR = {0x55AAFF, 0xB070FF, 0xFFCC33, 0x66EE66};
    public static double[] upChance = {15, 3.5, 1.4};
    public static int[] ceiling = {10, 42, 107};
    public static double[] costMul = {100, 150, 400, 1000};
    public static double line2Same = 20, line3Same = 10;
    /** 부위 분류(weapon/armor/accessory) → 옵션 키 → 등급별 수치 (0 = 그 등급엔 안 나옴) */
    public static final Map<String, Map<String, double[]>> POOLS = new LinkedHashMap<>();

    /** 2번째 값이 필요한 옵션 (확률 무적: 무적 초) — 등급별 */
    public static final Map<String, double[]> V2 = new LinkedHashMap<>();
    /** 피격 시 피해 무시가 터질 확률 % */
    public static double ignoreChance = 20;

    static {
        defaults();
    }

    private static void defaults() {
        POOLS.clear();
        V2.clear();
        double[] pct = {3, 6, 9, 12};
        Map<String, double[]> w = new LinkedHashMap<>(), a = new LinkedHashMap<>(), c = new LinkedHashMap<>();
        for (String s : new String[]{"strPct", "dexPct", "intPct", "lukPct"}) { w.put(s, pct); a.put(s, pct); c.put(s, pct); }
        for (String s : new String[]{"str", "dex", "int", "luk"}) w.put(s, new double[]{8, 0, 0, 0});
        w.put("atk", new double[]{6, 0, 0, 0}); w.put("matk", new double[]{6, 0, 0, 0});
        w.put("atkPct", pct); w.put("critChance", new double[]{4, 6, 8, 10}); w.put("bossPct", new double[]{0, 0, 30, 40});
        a.put("hpPct", pct); a.put("defPct", pct); a.put("allPct", new double[]{0, 3, 6, 9}); a.put("ignorePct", new double[]{0, 0, 20, 40});
        for (String s : new String[]{"strPerLv", "dexPerLv", "intPerLv", "lukPerLv"}) { a.put(s, new double[]{0, 0, 1, 2}); c.put(s, new double[]{0, 0, 1, 2}); }
        c.put("hpPct", pct); c.put("speedPct", new double[]{2, 4, 6, 8}); c.put("goldPct", new double[]{0, 5, 8, 12}); c.put("dropPct", new double[]{0, 5, 8, 12});
        POOLS.put("weapon", w); POOLS.put("armor", a); POOLS.put("accessory", c);
        POOLS.put("gloves", new LinkedHashMap<>(Map.of("critDmg", new double[]{0, 0, 0, 8})));
        POOLS.put("hat", new LinkedHashMap<>(Map.of("cdPct", new double[]{0, 0, 0, 10})));
        Map<String, double[]> top = new LinkedHashMap<>();
        top.put("iframe", new double[]{0, 0.5, 1, 1.5});
        top.put("invulProc", new double[]{0, 0, 5, 7});
        POOLS.put("top", top);
        V2.put("invulProc", new double[]{0, 0, 2, 3});
    }

    public static void parse(JsonObject o) {
        defaults();
        if (o == null) return;
        try {
            if (o.has("upChance")) { var x = o.getAsJsonArray("upChance"); for (int i = 0; i < 3 && i < x.size(); i++) upChance[i] = x.get(i).getAsDouble(); }
            if (o.has("ceiling")) { var x = o.getAsJsonArray("ceiling"); for (int i = 0; i < 3 && i < x.size(); i++) ceiling[i] = x.get(i).getAsInt(); }
            if (o.has("costMul")) { var x = o.getAsJsonArray("costMul"); for (int i = 0; i < 4 && i < x.size(); i++) costMul[i] = x.get(i).getAsDouble(); }
            if (o.has("line2Same")) line2Same = o.get("line2Same").getAsDouble();
            if (o.has("line3Same")) line3Same = o.get("line3Same").getAsDouble();
            if (o.has("ignoreChance")) ignoreChance = o.get("ignoreChance").getAsDouble();
            if (o.has("pools")) {
                POOLS.clear();
                for (var e : o.getAsJsonObject("pools").entrySet()) {
                    Map<String, double[]> m = new LinkedHashMap<>();
                    for (var k : e.getValue().getAsJsonObject().entrySet()) {
                        var arr = k.getValue().getAsJsonArray();
                        double[] v = new double[4];
                        for (int i = 0; i < 4 && i < arr.size(); i++) v[i] = arr.get(i).getAsDouble();
                        m.put(k.getKey(), v);
                    }
                    POOLS.put(e.getKey(), m);
                }
            }
            if (o.has("v2")) {
                V2.clear();
                for (var k : o.getAsJsonObject("v2").entrySet()) {
                    var arr = k.getValue().getAsJsonArray();
                    double[] v = new double[4];
                    for (int i = 0; i < 4 && i < arr.size(); i++) v[i] = arr.get(i).getAsDouble();
                    V2.put(k.getKey(), v);
                }
            }
        } catch (Exception ex) {
            rpg.core.RpgCore.LOG.error("[rpgcore] equipment.json potential 읽기 실패", ex);
        }
    }

    // ───────── 장비 상태 ─────────
    public static boolean can(GearDefs.GearDef d) {
        return d != null && !d.slot().equals("medal") && POOLS.containsKey(Enhance.cat(d.slot()));
    }

    public static boolean has(ItemStack s) { return s.hasTag() && s.getTag().contains("rpgPot"); }
    public static CompoundTag pot(ItemStack s) { return s.hasTag() ? s.getTag().getCompound("rpgPot") : new CompoundTag(); }
    public static int grade(ItemStack s) { return pot(s).getInt("g"); }
    public static boolean pending(ItemStack s) { return pot(s).contains("new"); }

    public static long cost(GearDefs.GearDef d, int grade) {
        return Math.round((d.level() + 10) * costMul[Math.max(0, Math.min(3, grade))]);
    }

    /** 장비 합산에 더할 잠재능력 수치 */
    public static void addBonus(ItemStack s, Map<String, Double> m) {
        if (!has(s)) return;
        ListTag l = pot(s).getList("l", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag x = l.getCompound(i);
            m.merge(x.getString("k"), x.getDouble("v"), Double::sum);
            if (x.contains("v2")) m.merge(x.getString("k") + "Sec", x.getDouble("v2"), Math::max);   // 확률 무적 시간: 가장 긴 것
        }
    }

    public static String label(String k) {
        return switch (k) {
            case "strPct" -> "힘"; case "dexPct" -> "민첩"; case "intPct" -> "지력"; case "lukPct" -> "운";
            case "atkPct" -> "공격력/마력"; case "critChance" -> "치명타 확률"; case "bossPct" -> "보스 공격 시 피해";
            case "hpPct" -> "최대 HP"; case "defPct" -> "방어력"; case "allPct" -> "올스탯";
            case "speedPct" -> "이동속도"; case "goldPct" -> "골드 획득"; case "dropPct" -> "아이템 드롭률";
            case "critDmg" -> "크리티컬 데미지"; case "cdPct" -> "모든 스킬 재사용 대기시간";
            default -> k;
        };
    }

    private static String n(double v) { return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v); }

    /** 옵션 한 줄 글자 */
    public static String text(String k, double v, double v2) {
        return switch (k) {
            case "str", "dex", "int", "luk", "atk", "matk" -> GearDefs.label(k) + " +" + n(v);
            case "strPerLv", "dexPerLv", "intPerLv", "lukPerLv" -> "캐릭터 9레벨당 " + GearDefs.label(k.substring(0, 3)) + " +" + n(v);
            case "cdPct" -> "모든 스킬 재사용 대기시간 -" + n(v) + "%";
            case "ignorePct" -> "피격 시 " + n(ignoreChance) + "% 확률로 피해 " + n(v) + "% 무시";
            case "iframe" -> "피격 후 무적 시간 +" + n(v) + "초";
            case "invulProc" -> "피격 시 " + n(v) + "% 확률로 " + n(v2) + "초 무적";
            default -> label(k) + " +" + n(v) + "%";
        };
    }

    public static List<Component> lines(CompoundTag p) {
        List<Component> out = new ArrayList<>();
        ListTag l = p.getList("l", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag x = l.getCompound(i);
            out.add(Component.literal(" " + text(x.getString("k"), x.getDouble("v"), x.getDouble("v2")))
                    .withStyle(st -> st.withColor(COLOR[Math.max(0, Math.min(3, x.getInt("g")))])));
        }
        return out;
    }

    // ───────── 굴리기 ─────────
    /** 장비의 옵션 목록 = 분류(무기·방어구·장신구) + 부위 전용 (장갑·모자·상의 등) */
    public static Map<String, double[]> pool(GearDefs.GearDef d) {
        Map<String, double[]> m = new LinkedHashMap<>(POOLS.getOrDefault(Enhance.cat(d.slot()), Map.of()));
        if (!d.slot().equals(Enhance.cat(d.slot()))) m.putAll(POOLS.getOrDefault(d.slot(), Map.of()));
        return m;
    }

    static CompoundTag roll(GearDefs.GearDef d, int grade) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Map<String, double[]> pool = pool(d);
        CompoundTag p = new CompoundTag();
        p.putInt("g", grade);
        ListTag l = new ListTag();
        for (int i = 0; i < 3; i++) {
            int g = grade;
            if (i == 1 && r.nextDouble() * 100 >= line2Same) g = Math.max(0, grade - 1);
            if (i == 2 && r.nextDouble() * 100 >= line3Same) g = Math.max(0, grade - 1);
            List<String> keys = new ArrayList<>();
            for (var e : pool.entrySet()) if (e.getValue()[g] > 0) keys.add(e.getKey());
            if (keys.isEmpty()) continue;
            String k = keys.get(r.nextInt(keys.size()));
            CompoundTag x = new CompoundTag();
            x.putString("k", k);
            x.putDouble("v", pool.get(k)[g]);
            if (V2.containsKey(k)) x.putDouble("v2", V2.get(k)[g]);
            x.putInt("g", g);
            l.add(x);
        }
        p.put("l", l);
        return p;
    }

    private static int[] ceil(Player p) {
        int[] c = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getIntArray("rpgPotCeil");
        return c.length == 3 ? c : new int[3];
    }

    private static void saveCeil(Player p, int[] c) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        keep.putIntArray("rpgPotCeil", c);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
    }

    public static int[] ceilCounts(Player p) { return ceil(p); }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }

    private static boolean take(ServerPlayer p, Item it) {
        for (ItemStack s : p.getInventory().items) if (s.is(it)) { s.shrink(1); return true; }
        return false;
    }

    /** 부여 주문서 (epic = 에픽 부여) */
    public static void grant(ServerPlayer p, ItemStack s, boolean epic) {
        GearDefs.GearDef d = Gear.def(s);
        if (!can(d)) { msg(p, "이 장비에는 잠재능력을 부여할 수 없어요", ChatFormatting.RED); return; }
        if (has(s)) { msg(p, "이미 잠재능력이 있어요 (재설정을 쓰세요)", ChatFormatting.RED); return; }
        if (!take(p, (epic ? ModItems.EPIC_POTENTIAL_SCROLL : ModItems.POTENTIAL_SCROLL).get())) { msg(p, "부여 주문서가 없어요", ChatFormatting.RED); return; }
        s.getOrCreateTag().put("rpgPot", roll(d, epic ? 1 : 0));
        msg(p, GRADE[epic ? 1 : 0] + " 잠재능력 부여!", ChatFormatting.LIGHT_PURPLE);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
    }

    /** 재설정 (cube = 수상한 큐브 사용, 아니면 골드) → 새 옵션은 대기 상태, 고르기 전엔 다시 못 돌림 */
    public static void reset(ServerPlayer p, ItemStack s, boolean cube) {
        GearDefs.GearDef d = Gear.def(s);
        if (d == null || !has(s)) return;
        if (pending(s)) { msg(p, "먼저 새 옵션 / 기존 유지를 골라주세요", ChatFormatting.RED); return; }
        int g = grade(s);
        if (cube) {
            if (!take(p, ModItems.CUBE.get())) { msg(p, "수상한 큐브가 없어요", ChatFormatting.RED); return; }
        } else {
            PlayerData pd = RpgData.get(p);
            long c = cost(d, g);
            if (pd.gold < c) { msg(p, "골드가 부족해요 (" + String.format("%,d", c) + " G)", ChatFormatting.RED); return; }
            pd.gold -= c;
            RpgData.sync(p);
        }
        int ng = g;
        if (g < 3) {
            int[] c = ceil(p);
            c[g]++;
            boolean up = c[g] >= ceiling[g] || ThreadLocalRandom.current().nextDouble() * 100 < upChance[g];
            if (up) { ng = g + 1; c[g] = 0; }
            saveCeil(p, c);
        }
        s.getOrCreateTag().getCompound("rpgPot").put("new", roll(d, ng));
        if (ng > g) {
            msg(p, "등급 상승! " + GRADE[ng], ChatFormatting.GOLD);
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
        } else p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 1.0f);
    }

    /** 새 옵션 적용 (keep=false) 또는 기존 유지 (keep=true) */
    public static void choose(ServerPlayer p, ItemStack s, boolean keep) {
        if (!pending(s)) return;
        CompoundTag pt = s.getOrCreateTag().getCompound("rpgPot");
        CompoundTag nw = pt.getCompound("new");
        if (keep) pt.remove("new");
        else s.getOrCreateTag().put("rpgPot", nw);
        msg(p, keep ? "기존 옵션을 유지했어요" : "새 옵션을 적용했어요", ChatFormatting.GREEN);
    }
}
