package rpg.core.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 스킬·패시브 정의 (skills.json / passives.json).
 * 서버가 읽고, 접속·/reload 때 클라이언트에도 원문을 보내서 같은 내용으로 화면을 그린다.
 */
public final class Defs {
    private Defs() {}

    /** type: "instant"(누르면 바로) / "charge"(누르고 있다가 떼면). job: 0 = 모든 직업 */
    public record SkillDef(String id, String name, int tier, int maxLevel, double mp, double cooldown, double duration,
                           double value, double valuePerLevel, String desc, String type, int job,
                           double value2, double value2PerLevel, double mpPerLevel,
                           int branch, String stat, String stat2, double cdPerLevel,
                           double hits, double hitsPerLevel, double step, double step2) {
        /** 레벨별 계수. step 이 있으면 그 단위(예: 5)로 반올림해서 5·10의 배수로 딱 떨어지게 */
        public double value(int level) { return snap(value + valuePerLevel * Math.max(0, level - 1), step); }
        /** 레벨별 MP (찍을수록 더 씀) */
        public double mp(int level) { return Math.round(mp + mpPerLevel * Math.max(0, level - 1)); }
        public double value2(int level) { return snap(value2 + value2PerLevel * Math.max(0, level - 1), step2); }
        private static double snap(double v, double st) { return st > 0 ? Math.round(v / st) * st : v; }
        public boolean charge() { return "charge".equals(type); }
        /** 패시브: 배우면 항상 켜짐 (칸에 못 넣음). stat/stat2 = PlayerData.bonus 에 value/value2 를 더함 */
        public boolean passive() { return "passive".equals(type); }
        /** 레벨별 쿨타임 (초) */
        public double cooldown(int level) { return Math.max(0, cooldown + cdPerLevel * Math.max(0, level - 1)); }

        /** 레벨별 타수 (연속 찌르기처럼 레벨이 오르면 타수가 늘어나는 스킬) */
        public int hits(int level) { return (int) Math.max(1, Math.round(hits + hitsPerLevel * Math.max(0, level - 1))); }

        public String describe(int level) {
            int lv = Math.max(1, level);
            return desc.replace("{n}", String.valueOf(hits(lv))).replace("{v}", fmt(value(lv))).replace("{v2}", fmt(value2(lv))).replace("{d}", fmt(duration));
        }
    }

    public record Node(String id, String name, int x, int y, int maxRank, String stat, double perRank,
                       List<String> requires, boolean special) {}

    public static final Map<String, SkillDef> SKILLS = new LinkedHashMap<>();
    public static final Map<String, Node> NODES = new LinkedHashMap<>();
    public static String skillsJson = "{}", passivesJson = "{}";

    public static void parse(String skillsText, String passivesText) {
        skillsJson = skillsText == null ? "{}" : skillsText;
        passivesJson = passivesText == null ? "{}" : passivesText;
        SKILLS.clear();
        NODES.clear();
        try {
            JsonObject s = JsonParser.parseString(skillsJson).getAsJsonObject();
            for (var e : s.entrySet()) {
                if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) continue;
                JsonObject o = e.getValue().getAsJsonObject();
                SKILLS.put(e.getKey(), new SkillDef(e.getKey(), str(o, "name", e.getKey()), (int) num(o, "tier", 0),
                        (int) num(o, "maxLevel", 10), num(o, "mp", 0), num(o, "cooldown", 1), num(o, "duration", 0),
                        num(o, "value", 0), num(o, "valuePerLevel", 0), str(o, "desc", ""), str(o, "type", "instant"),
                        (int) num(o, "job", 0), num(o, "value2", 0), num(o, "value2PerLevel", 0), num(o, "mpPerLevel", 0),
                        (int) num(o, "branch", 0), str(o, "stat", ""), str(o, "stat2", ""), num(o, "cooldownPerLevel", 0),
                        num(o, "hits", 1), num(o, "hitsPerLevel", 0), num(o, "step", 0), num(o, "step2", 0)));
            }
        } catch (Exception ex) {
            rpg.core.RpgCore.LOG.error("[rpgcore] skills.json 읽기 실패", ex);
        }
        try {
            JsonObject p = JsonParser.parseString(passivesJson).getAsJsonObject();
            if (p.has("nodes")) {
                for (JsonElement el : p.getAsJsonArray("nodes")) {
                    JsonObject o = el.getAsJsonObject();
                    List<String> req = new ArrayList<>();
                    if (o.has("requires")) o.getAsJsonArray("requires").forEach(x -> req.add(x.getAsString()));
                    String id = str(o, "id", "?");
                    NODES.put(id, new Node(id, str(o, "name", id), (int) num(o, "x", 0), (int) num(o, "y", 0),
                            (int) num(o, "maxRank", 1), str(o, "stat", ""), num(o, "perRank", 0), req,
                            o.has("special") && o.get("special").getAsBoolean()));
                }
            }
        } catch (Exception ex) {
            rpg.core.RpgCore.LOG.error("[rpgcore] passives.json 읽기 실패", ex);
        }
    }

    public static String statLabel(String stat) {
        return switch (stat) {
            case "atkPct" -> "스공 +%s%%";
            case "critDmg" -> "치명타 피해 +%s%%";
            case "critChance" -> "치명타 확률 +%s%%";
            case "hpPct" -> "최대 HP +%s%%";
            case "def" -> "방어 +%s";
            case "hpRegenPct" -> "HP 회복 +%s%%";
            case "speedPct" -> "이동속도 +%s%%";
            case "mpRegenPct" -> "MP 회복 +%s%%";
            case "bossDmg" -> "보스 피해 +%s%% (보스 단계에서 적용)";
            case "reviveTime" -> "부활 시간 -%s%% (파티 단계에서 적용)";
            case "dodgeCd" -> "구르기 쿨타임 -%s%%";
            case "stamina" -> "최대 스태미나 +%s";
            case "healPct" -> "물약·치유 회복량 +%s%%";
            default -> "%s";
        };
    }

    public static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private static String str(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
    private static double num(JsonObject o, String k, double d) { return o.has(k) ? o.get(k).getAsDouble() : d; }
}
