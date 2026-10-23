package rpg.core.equip;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 세트 효과 (equipment.json "sets"): 세트 안의 서로 다른 아이템을 몇 개 착용했나 → 그 수 이하 단계 효과를 전부 더함.
 * 같은 아이템 두 개(반지 등)는 1부위로 셈. 착용 조건 못 맞춘 장비는 안 셈.
 * 효과 키는 장비 스탯과 같음 (str dex int luk atk matk hp mp def + atkPct hpPct bossPct critChance critDmg cdPct speedPct defPct)
 */
public final class Sets {
    private Sets() {}

    public record SetDef(String id, String name, List<String> items, int total, TreeMap<Integer, Map<String, Double>> bonus) {}
    public static final Map<String, SetDef> SETS = new LinkedHashMap<>();
    /** 아이템 → 세트 */
    public static final Map<String, String> ITEM_SET = new HashMap<>();

    public static void parse(JsonObject o) {
        SETS.clear();
        ITEM_SET.clear();
        if (o == null) return;
        try {
            for (var e : o.entrySet()) {
                if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) continue;
                JsonObject s = e.getValue().getAsJsonObject();
                List<String> items = new ArrayList<>();
                for (var x : s.getAsJsonArray("items")) items.add(x.getAsString());
                TreeMap<Integer, Map<String, Double>> b = new TreeMap<>();
                for (var t : s.getAsJsonObject("bonus").entrySet()) {
                    Map<String, Double> m = new LinkedHashMap<>();
                    for (var kv : t.getValue().getAsJsonObject().entrySet()) m.put(kv.getKey(), kv.getValue().getAsDouble());
                    b.put(Integer.parseInt(t.getKey()), m);
                }
                SetDef d = new SetDef(e.getKey(), s.has("name") ? s.get("name").getAsString() : e.getKey(), items,
                        s.has("max") ? s.get("max").getAsInt() : items.size(), b);
                SETS.put(d.id(), d);
                for (String it : items) ITEM_SET.put(it, d.id());
            }
        } catch (Exception ex) {
            rpg.core.RpgCore.LOG.warn("[rpgcore] equipment.json sets 읽기 실패: {}", ex.toString());
        }
    }

    /** 착용 중인 (착용 조건 맞는) 장비 id 모음 */
    public static Set<String> worn(Player p) {
        Set<String> ids = new LinkedHashSet<>();
        PlayerEquip eq = PlayerEquip.get(p);
        List<ItemStack> all = new ArrayList<>();
        for (int i = 1; i < eq.items.getSlots(); i++) all.add(eq.items.getStackInSlot(i));
        all.add(PlayerEquip.weapon(p));
        for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) all.add(p.getItemBySlot(es));
        for (ItemStack s : all) {
            GearDefs.GearDef d = Gear.def(s);
            if (d != null && Gear.canEquip(p, s)) ids.add(d.id());
        }
        return ids;
    }

    public static int count(SetDef s, Set<String> worn) {
        int n = 0;
        for (String it : s.items()) if (worn.contains(it)) n++;
        return Math.min(n, s.total());
    }

    /** 세트 효과를 장비 합계에 더함 */
    public static void addBonus(Player p, Map<String, Double> m) {
        Set<String> worn = worn(p);
        for (SetDef s : SETS.values()) {
            int n = count(s, worn);
            if (n < 2) continue;
            for (var t : s.bonus().headMap(n, true).values()) t.forEach((k, v) -> m.merge(k, v, Double::sum));
        }
    }

    private static String fmt(double v) { return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v); }

    /** 효과 한 단계 글자: "올스탯 +3 · 최대 HP +3%" (str dex int luk 같으면 올스탯) */
    public static String text(Map<String, Double> b) {
        Map<String, Double> m = new LinkedHashMap<>(b);
        List<String> out = new ArrayList<>();
        Double s = m.get("str");
        if (s != null && s.equals(m.get("dex")) && s.equals(m.get("int")) && s.equals(m.get("luk"))) {
            out.add("올스탯 +" + fmt(s));
            m.remove("str"); m.remove("dex"); m.remove("int"); m.remove("luk");
        }
        if (m.containsKey("atk") && m.get("atk").equals(m.get("matk"))) { out.add("공·마 +" + fmt(m.get("atk"))); m.remove("atk"); m.remove("matk"); }
        for (var e : m.entrySet()) {
            String k = e.getKey();
            String label = switch (k) {
                case "atkPct" -> "데미지"; case "hpPct" -> "최대 HP"; case "bossPct" -> "보스 데미지"; case "critChance" -> "크리 확률";
                case "critDmg" -> "크리 데미지"; case "cdPct" -> "쿨타임 감소"; case "speedPct" -> "이동속도"; case "defPct" -> "방어";
                default -> GearDefs.label(k);
            };
            out.add(label + " +" + fmt(e.getValue()) + (k.endsWith("Pct") || k.equals("critChance") || k.equals("critDmg") ? "%" : ""));
        }
        return String.join(" · ", out);
    }

    /** 툴팁: 세트 이름 (n/전체) + 단계별 효과 (켜진 줄 초록) */
    public static void tooltip(ItemStack stack, GearDefs.GearDef d, Player p, List<Component> tip) {
        String sid = ITEM_SET.get(d.id());
        SetDef s = sid == null ? null : SETS.get(sid);
        if (s == null) return;
        int n = p == null ? 0 : count(s, worn(p));
        tip.add(Component.literal("[" + s.name() + "] (" + n + "/" + s.total() + ")").withStyle(ChatFormatting.GOLD));
        for (var t : s.bonus().entrySet()) {
            boolean on = n >= t.getKey();
            tip.add(Component.literal(" " + t.getKey() + "세트: " + text(t.getValue())).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }
    }
}
