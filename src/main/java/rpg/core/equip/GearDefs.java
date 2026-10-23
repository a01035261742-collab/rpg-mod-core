package rpg.core.equip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 장비 정의 (equipment.json). 서버가 읽고 클라이언트에도 원문을 보낸다 */
public final class GearDefs {
    private GearDefs() {}

    public record GearDef(String id, String slot, int job, int level, Map<String, Double> stats, double speed, String title, int titleColor) {}

    public static final Map<String, GearDef> ITEMS = new LinkedHashMap<>();
    public static int[] lineChances = {20, 30, 25, 15, 10};
    /** 추가 옵션 고정 줄 수 (-1 이면 lineChances 확률) */
    public static int fixedLines = 4;
    public static final Map<String, List<String>> POOLS = new HashMap<>();
    public static final Map<String, double[]> RANGES = new HashMap<>();
    public static boolean keepInventory = true;
    public static String json = "{}";

    public static final String[] STAT_KEYS = {"str", "dex", "int", "luk", "vit", "atk", "matk", "hp", "mp", "def", "allPct"};

    public static String label(String k) {
        return switch (k) {
            case "str" -> "힘";
            case "dex" -> "민첩";
            case "int" -> "지력";
            case "luk" -> "운";
            case "vit" -> "체력";
            case "sta" -> "스태미나";
            case "atk" -> "공격력";
            case "matk" -> "마력";
            case "hp" -> "최대 HP";
            case "mp" -> "최대 MP";
            case "def" -> "방어";
            case "allPct" -> "올스탯%";
            case "bossPct" -> "보스 데미지";
            case "atkPct" -> "데미지";
            case "speedPct" -> "이동속도";
            case "reqLv" -> "착용 레벨 감소";
            case "str+dex" -> "힘+민첩";
            case "str+int" -> "힘+지력";
            case "str+luk" -> "힘+운";
            case "dex+int" -> "민첩+지력";
            case "dex+luk" -> "민첩+운";
            case "int+luk" -> "지력+운";
            default -> k;
        };
    }

    public static String slotName(String s) {
        return switch (s) {
            case "weapon" -> "무기";
            case "hat" -> "모자";
            case "top" -> "상의";
            case "bottom" -> "하의";
            case "shoes" -> "신발";
            case "gloves" -> "장갑";
            case "cape" -> "망토";
            case "belt" -> "벨트";
            case "earring" -> "귀걸이";
            case "ring" -> "반지";
            case "pendant" -> "펜던트";
            case "medal" -> "훈장";
            default -> s;
        };
    }

    public static void parse(String text) {
        json = text == null ? "{}" : text;
        ITEMS.clear();
        POOLS.clear();
        RANGES.clear();
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("keepInventory")) keepInventory = o.get("keepInventory").getAsBoolean();
            if (o.has("items")) {
                for (var e : o.getAsJsonObject("items").entrySet()) {
                    JsonObject it = e.getValue().getAsJsonObject();
                    Map<String, Double> st = new LinkedHashMap<>();
                    if (it.has("stats")) for (var s : it.getAsJsonObject("stats").entrySet()) st.put(s.getKey(), s.getValue().getAsDouble());
                    ITEMS.put(e.getKey(), new GearDef(e.getKey(), it.get("slot").getAsString(),
                            it.has("job") ? it.get("job").getAsInt() : 0, it.has("level") ? it.get("level").getAsInt() : 1,
                            st, it.has("speed") ? it.get("speed").getAsDouble() : 0,
                            it.has("title") ? it.get("title").getAsString() : "",
                            it.has("titleColor") ? Integer.parseInt(it.get("titleColor").getAsString().replace("#", ""), 16) : 0xFFD24A));
                }
            }
            Enhance.parse(o.has("enhance") ? o.getAsJsonObject("enhance") : null);
            Potential.parse(o.has("potential") ? o.getAsJsonObject("potential") : null);
            Flame.parse(o.has("options") ? o.getAsJsonObject("options") : null);
            if (o.has("options")) {
                JsonObject op = o.getAsJsonObject("options");
                fixedLines = op.has("lines") ? op.get("lines").getAsInt() : -1;
                if (op.has("lineChances")) {
                    var arr = op.getAsJsonArray("lineChances");
                    lineChances = new int[arr.size()];
                    for (int i = 0; i < arr.size(); i++) lineChances[i] = arr.get(i).getAsInt();
                }
                if (op.has("pools")) for (var p : op.getAsJsonObject("pools").entrySet()) {
                    List<String> l = new ArrayList<>();
                    p.getValue().getAsJsonArray().forEach(x -> l.add(x.getAsString()));
                    POOLS.put(p.getKey(), l);
                }
                if (op.has("ranges")) for (var r : op.getAsJsonObject("ranges").entrySet()) {
                    var a = r.getValue().getAsJsonArray();
                    RANGES.put(r.getKey(), new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble()});
                }
            }
        } catch (Exception ex) {
            rpg.core.RpgCore.LOG.error("[rpgcore] equipment.json 읽기 실패", ex);
        }
    }
}
