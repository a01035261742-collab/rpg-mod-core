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
            double[] v3 = V3.get(id);
            String d3 = v3 == null ? "" : fmt(snap(v3[0] + v3[1] * Math.max(0, lv - 1), step));
            return desc.replace("{v3}", d3).replace("{n}", String.valueOf(hits(lv))).replace("{v}", fmt(value(lv))).replace("{v2}", fmt(value2(lv))).replace("{d}", fmt(duration));
        }
    }

    /**
     * 패시브 칸. kind: s 작은 칸 · n 노터블 · g 끝 보석 · k 키스톤 · start.
     * 직업 트리(job>0)는 PoE식: requires 중 하나라도 찍혀 있으면 열림 (공용 트리는 전부).
     */
    public record Node(String id, String name, int x, int y, int maxRank, String stat, double perRank,
                       List<String> requires, boolean special, int job, int branch, String stat2, double perRank2, String kind, String desc) {
        public boolean anyOf() { return job > 0; }
    }

    /** 이 직업이 쓰는 트리: 전용 트리가 있으면 그것, 없으면 공용(job 0). 초보자(0)는 트리 없음 */
    public static int treeOf(int job) {
        if (job <= 0) return -1;
        for (Node n : NODES.values()) if (n.job() == job) return job;
        return 0;
    }

    /** 이 칸이 이 직업 트리에 있나 (갈래 칸 포함) */
    public static boolean inJobTree(Node n, int job) {
        return n.job() == treeOf(job);
    }

    /** 이 칸이 이 플레이어가 쓸 수 있는 칸인가 (갈래 칸은 그 갈래만) */
    public static boolean inTree(Node n, int job, int branch) {
        return inJobTree(n, job) && (n.branch() == 0 || n.branch() == branch);
    }

    /** 찍을 수 있나 (앞 칸 조건): 시작 칸(maxRank 0)은 찍힌 걸로 봄 */
    public static boolean unlocked(Node n, java.util.function.ToIntFunction<String> rank) {
        if (n.requires().isEmpty()) return true;
        boolean any = false, all = true;
        for (String r : n.requires()) {
            Node req = NODES.get(r);
            boolean ok = req == null || req.maxRank() == 0 || rank.applyAsInt(r) > 0;
            any |= ok; all &= ok;
        }
        return n.anyOf() ? any : all;
    }

    public static final Map<String, SkillDef> SKILLS = new LinkedHashMap<>();
    /** 3번째 계수 (연속 베기 3타 등): id → {value3, value3PerLevel} */
    public static final Map<String, double[]> V3 = new java.util.HashMap<>();

    public static double value3(SkillDef s, int level) {
        double[] v = V3.get(s.id());
        if (v == null) return 0;
        double x = v[0] + v[1] * Math.max(0, level - 1);
        return s.step() > 0 ? Math.round(x / s.step()) * s.step() : x;
    }
    public static final Map<String, Node> NODES = new LinkedHashMap<>();
    public static String skillsJson = "{}", passivesJson = "{}";

    public static void parse(String skillsText, String passivesText) {
        skillsJson = skillsText == null ? "{}" : skillsText;
        passivesJson = passivesText == null ? "{}" : passivesText;
        SKILLS.clear();
        V3.clear();
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
                if (o.has("value3")) V3.put(e.getKey(), new double[]{num(o, "value3", 0), num(o, "value3PerLevel", 0)});
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
                            o.has("special") && o.get("special").getAsBoolean(), (int) num(o, "job", 0), (int) num(o, "branch", 0),
                            str(o, "stat2", ""), num(o, "perRank2", 0), str(o, "kind", o.has("special") ? "k" : "s"), str(o, "desc", "")));
                }
            }
        } catch (Exception ex) {
            rpg.core.RpgCore.LOG.error("[rpgcore] passives.json 읽기 실패", ex);
        }
    }

    public static String statLabel(String stat) {
        return switch (stat) {
            case "atkPct" -> "공격력 +%s%%";
            case "dmgPct" -> "데미지 +%s%%";
            case "bossPct" -> "보스 데미지 +%s%%";
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
            case "basicPct" -> "평타 계수 +%s%%p";
            case "mobPct" -> "일반 몹 데미지 +%s%%";
            case "str" -> "힘 +%s";
            case "vit" -> "체력 +%s";
            case "defPct" -> "방어 +%s%%";
            case "kbResist" -> "넉백 저항 +%s%%";
            case "vitHpPct" -> "체력의 HP 효율 +%s%%";
            case "cdPct" -> "쿨타임 -%s%%";
            case "mastery" -> "숙련도 +%s%%";
            case "executePct" -> "HP 30%% 이하 몹 데미지 +%s%%";
            case "lowHpDmg" -> "HP 50%% 이하일 때 데미지 +%s%%";
            case "giantHp" -> "최대 HP ×(1+%s%%)";
            default -> "%s";
        };
    }

    public static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private static String str(JsonObject o, String k, String d) { return o.has(k) ? o.get(k).getAsString() : d; }
    private static double num(JsonObject o, String k, double d) { return o.has(k) ? o.get(k).getAsDouble() : d; }
}
