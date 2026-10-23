package rpg.core.equip;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.item.ModItems;

/**
 * 추가 옵션 (메이플 환생의 불꽃식). equipment.json "options".
 * 옵션 하나 = 종류 + 단계(1~7). 수치 = 단계 × (a + b × 장비 레벨) 내림, 최소 1. 무기 공격력·마력 = 무기 기본 공격력 × weaponAtkPct% × 단계.
 * 줄 수: 일반 장비 1~4줄 (lineChances), 보스 장비 4줄. 처음 얻을 때 단계 dropTiers (1~4단계).
 * 불꽃: 강력한 3~6 · 영원한 4~7 · 검은 = 영원한 + 이전/새 선택. 비용 = 불꽃 1개 + (장비 레벨+10) × costMul 골드.
 * 저장: rpgOpts {키: 수치}, rpgOptT {키: 단계}, 검은 불꽃 대기 rpgOptNew {o, t}. 이중 스탯 키는 "str+dex" (양쪽에 같은 수치).
 */
public final class Flame {
    private Flame() {}

    public record Kind(String item, int min, int[] w) {}
    public static final Map<String, Kind> KINDS = new LinkedHashMap<>();
    public static int[] lineChances = {25, 25, 25, 25};
    public static int[] dropTiers = {25, 25, 25, 25};
    public static double costMul = 50, weaponAtkPct = 3;
    public static final Set<String> BOSS = new HashSet<>();
    public static final Map<String, List<String>> POOLS = new LinkedHashMap<>();
    public static final Map<String, double[]> VALUES = new LinkedHashMap<>();

    static { defaults(); }

    private static void defaults() {
        KINDS.clear(); POOLS.clear(); VALUES.clear(); BOSS.clear();
        KINDS.put("powerful", new Kind("powerful_flame", 3, new int[]{20, 30, 36, 14}));
        KINDS.put("eternal", new Kind("eternal_flame", 4, new int[]{29, 45, 25, 1}));
        KINDS.put("black", new Kind("black_flame", 4, new int[]{29, 45, 25, 1}));
        lineChances = new int[]{25, 25, 25, 25};
        dropTiers = new int[]{25, 25, 25, 25};
        costMul = 50; weaponAtkPct = 3;
        BOSS.addAll(List.of("forest_lord_ring", "guardian_ring", "forest_lord_belt", "guardian_pendant", "guardian_cape"));
        List<String> common = new ArrayList<>(List.of("str", "dex", "int", "luk", "vit",
                "str+dex", "str+int", "str+luk", "dex+int", "dex+luk", "int+luk", "hp", "mp", "def", "atk", "matk", "reqLv", "allPct"));
        List<String> w = new ArrayList<>(common); w.add("bossPct"); w.add("atkPct");
        List<String> a = new ArrayList<>(common); a.add("speedPct");
        POOLS.put("weapon", w); POOLS.put("armor", a);
        for (String s : new String[]{"str", "dex", "int", "luk", "vit"}) VALUES.put(s, new double[]{0.5, 0.025});
        for (String s : new String[]{"str+dex", "str+int", "str+luk", "dex+int", "dex+luk", "int+luk"}) VALUES.put(s, new double[]{0.25, 0.0125});
        VALUES.put("hp", new double[]{2, 0.5});
        VALUES.put("mp", new double[]{1, 0.25});
        VALUES.put("def", new double[]{0.5, 0.05});
        VALUES.put("atk", new double[]{0.6, 0});
        VALUES.put("matk", new double[]{0.6, 0});
        VALUES.put("reqLv", new double[]{1, 0});
        VALUES.put("allPct", new double[]{1, 0});
        VALUES.put("bossPct", new double[]{2, 0});
        VALUES.put("atkPct", new double[]{1, 0});
        VALUES.put("speedPct", new double[]{1, 0});
    }

    private static int[] ints(JsonArray a) {
        int[] r = new int[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsInt();
        return r;
    }

    /** equipment.json "options" (없는 칸은 기본값) */
    public static void parse(JsonObject op) {
        defaults();
        if (op == null) return;
        try {
            if (op.has("lineChances")) lineChances = ints(op.getAsJsonArray("lineChances"));
            if (op.has("dropTiers")) dropTiers = ints(op.getAsJsonArray("dropTiers"));
            if (op.has("costMul")) costMul = op.get("costMul").getAsDouble();
            if (op.has("weaponAtkPct")) weaponAtkPct = op.get("weaponAtkPct").getAsDouble();
            if (op.has("bossItems")) { BOSS.clear(); for (var e : op.getAsJsonArray("bossItems")) BOSS.add(e.getAsString()); }
            if (op.has("flames")) for (var e : op.getAsJsonObject("flames").entrySet()) {
                JsonObject k = e.getValue().getAsJsonObject();
                KINDS.put(e.getKey(), new Kind(k.get("item").getAsString(), k.get("min").getAsInt(), ints(k.getAsJsonArray("w"))));
            }
            if (op.has("pools")) for (var e : op.getAsJsonObject("pools").entrySet()) {
                List<String> l = new ArrayList<>();
                for (var x : e.getValue().getAsJsonArray()) l.add(x.getAsString());
                POOLS.put(e.getKey(), l);
            }
            if (op.has("values")) for (var e : op.getAsJsonObject("values").entrySet()) {
                JsonArray v = e.getValue().getAsJsonArray();
                VALUES.put(e.getKey(), new double[]{v.get(0).getAsDouble(), v.get(1).getAsDouble()});
            }
        } catch (Exception ex) {
            rpg.core.RpgCore.LOG.warn("[rpgcore] equipment.json options 읽기 실패: {}", ex.toString());
        }
    }

    public static boolean can(GearDefs.GearDef d) {
        return d != null && !"medal".equals(d.slot());
    }

    private static List<String> pool(GearDefs.GearDef d) {
        return POOLS.getOrDefault("weapon".equals(d.slot()) ? "weapon" : "armor", List.of());
    }

    public static int value(GearDefs.GearDef d, String k, int n) {
        if ("weapon".equals(d.slot()) && (k.equals("atk") || k.equals("matk"))) {
            double base = Math.max(d.stats().getOrDefault("atk", 0.0), d.stats().getOrDefault("matk", 0.0));
            return (int) Math.max(1, Math.floor(base * weaponAtkPct / 100.0 * n));
        }
        double[] v = VALUES.getOrDefault(k, new double[]{1, 0});
        return (int) Math.max(1, Math.floor(n * (v[0] + v[1] * d.level())));
    }

    private static int pick(int[] w, ThreadLocalRandom r) {
        int t = 0;
        for (int x : w) t += x;
        int p = r.nextInt(Math.max(1, t));
        for (int i = 0; i < w.length; i++) { p -= w[i]; if (p < 0) return i; }
        return w.length - 1;
    }

    /** 새 옵션 한 세트: {o:{키:수치}, t:{키:단계}} */
    public static CompoundTag roll(GearDefs.GearDef d, String id, int minTier, int[] tierW) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int lines = BOSS.contains(id) ? 4 : 1 + pick(lineChances, r);
        List<String> pool = new ArrayList<>(pool(d));
        CompoundTag o = new CompoundTag(), t = new CompoundTag();
        for (int i = 0; i < lines && !pool.isEmpty(); i++) {
            String k = pool.remove(r.nextInt(pool.size()));
            int n = Math.min(7, minTier + pick(tierW, r));
            o.putInt(k, value(d, k, n));
            t.putInt(k, n);
        }
        CompoundTag x = new CompoundTag();
        x.put("o", o);
        x.put("t", t);
        return x;
    }

    /** 장비에 옵션 적용 */
    public static void apply(ItemStack s, CompoundTag x) {
        s.getOrCreateTag().put("rpgOpts", x.getCompound("o"));
        s.getOrCreateTag().put("rpgOptT", x.getCompound("t"));
        s.getOrCreateTag().putBoolean("rpgRolled", true);
    }

    public static CompoundTag tiers(ItemStack s) {
        return s.hasTag() ? s.getTag().getCompound("rpgOptT") : new CompoundTag();
    }

    public static boolean pending(ItemStack s) {
        return s.hasTag() && s.getTag().contains("rpgOptNew");
    }

    public static CompoundTag pendingTag(ItemStack s) {
        return s.hasTag() ? s.getTag().getCompound("rpgOptNew") : new CompoundTag();
    }

    public static int reqLv(ItemStack s) {
        return s.hasTag() ? s.getTag().getCompound("rpgOpts").getInt("reqLv") : 0;
    }

    public static long cost(GearDefs.GearDef d) {
        return Math.round((d.level() + 10) * costMul);
    }

    public static Item item(String kind) {
        return switch (kind) {
            case "eternal" -> ModItems.ETERNAL_FLAME.get();
            case "black" -> ModItems.BLACK_FLAME.get();
            default -> ModItems.POWERFUL_FLAME.get();
        };
    }

    /** 옵션 수치를 스탯별로 펼침 (이중 스탯은 양쪽에) */
    public static Map<String, Integer> expand(CompoundTag o) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String k : o.getAllKeys()) {
            int v = o.getInt(k);
            if (k.contains("+")) for (String p : k.split("\\+")) m.merge(p, v, Integer::sum);
            else m.merge(k, v, Integer::sum);
        }
        return m;
    }

    /** 한 줄 표시: "힘+민첩 +2 (4단계)" */
    public static String text(String k, int v, int n) {
        String unit = k.endsWith("Pct") ? "%" : "";
        return GearDefs.label(k) + (k.equals("reqLv") ? " -" : " +") + v + unit + (n > 0 ? " (" + n + "단계)" : "");
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }

    /** 불꽃 사용 (kind = powerful/eternal/black) */
    public static void use(ServerPlayer p, ItemStack s, String kind) {
        GearDefs.GearDef d = Gear.def(s);
        Kind k = KINDS.get(kind);
        if (k == null || d == null) return;
        if (!can(d)) { msg(p, "이 장비는 추가 옵션이 없어요", ChatFormatting.RED); return; }
        if (pending(s)) { msg(p, "먼저 이전 / 새 옵션을 골라주세요", ChatFormatting.RED); return; }
        if (Enhance.broken(s)) { msg(p, "파괴된 장비예요", ChatFormatting.RED); return; }
        Item it = item(kind);
        int slot = -1;
        for (int i = 0; i < p.getInventory().items.size(); i++) if (p.getInventory().items.get(i).is(it)) { slot = i; break; }
        if (slot < 0) { msg(p, "불꽃이 없어요", ChatFormatting.RED); return; }
        PlayerData pd = RpgData.get(p);
        long c = cost(d);
        if (pd.gold < c) { msg(p, "골드가 부족해요 (" + String.format("%,d", c) + " G)", ChatFormatting.RED); return; }
        pd.gold -= c;
        p.getInventory().items.get(slot).shrink(1);
        RpgData.sync(p);
        String id = d.id();
        CompoundTag nw = roll(d, id, k.min(), k.w());
        if (kind.equals("black")) {
            s.getOrCreateTag().put("rpgOptNew", nw);
            msg(p, "새 옵션이 나왔어요 — 이전 / 새 옵션 중 고르세요", ChatFormatting.LIGHT_PURPLE);
        } else {
            long before = Power.of(p);
            apply(s, nw);
            long after = Power.of(p), dif = after - before;
            msg(p, "추가 옵션 변경! 전투력 " + String.format("%,d → %,d", before, after) + (dif == 0 ? "" : String.format(" (%+,d)", dif)),
                    dif > 0 ? ChatFormatting.GREEN : dif < 0 ? ChatFormatting.RED : ChatFormatting.GRAY);
        }
        p.level().playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /** 검은 불꽃: keep = 이전 유지 */
    public static void choose(ServerPlayer p, ItemStack s, boolean keep) {
        if (!pending(s)) return;
        CompoundTag nw = pendingTag(s);
        s.getOrCreateTag().remove("rpgOptNew");
        if (!keep) apply(s, nw);
        msg(p, keep ? "이전 옵션을 유지했어요" : "새 옵션을 적용했어요", ChatFormatting.GREEN);
    }
}
