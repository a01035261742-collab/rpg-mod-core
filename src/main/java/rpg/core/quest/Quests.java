package rpg.core.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.core.RpgCore;

/**
 * 퀘스트 정의 (quests.json, /reload 반영). 서버·클라 둘 다 같은 원문을 읽는다.
 * <ul>
 *   <li>npc: 퀘스트를 주는 NPC, turnIn: 완료하는 NPC (없으면 npc). 목표 없이 turnIn 만 다르면 = 배달·심부름</li>
 *   <li>goals: kill(mob, count, elite) · item(item, count — 완료 때 가져감)</li>
 *   <li>after: 먼저 깨야 하는 퀘스트 (없으면 레벨만 되면 받음)</li>
 * </ul>
 */
public final class Quests {
    private Quests() {}

    public record Npc(String id, String name, String greet) {}
    public record Goal(String type, String target, int count, boolean elite, String label) {}
    public record Reward(long exp, long gold, List<String[]> items) {}
    public record Quest(String id, String name, String npc, String turnIn, int minLevel, String after,
                        List<Goal> goals, Reward reward, List<String> offer, List<String> progress, List<String> done, String summary) {}

    public static final Map<String, Npc> NPCS = new LinkedHashMap<>();
    public static final Map<String, Quest> QUESTS = new LinkedHashMap<>();
    /** 클라로 보낼 원문 */
    public static String json = "{}";

    public static void parse(String text) {
        NPCS.clear();
        QUESTS.clear();
        json = text == null ? "{}" : text;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("npcs")) for (var e : o.getAsJsonObject("npcs").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                JsonObject n = e.getValue().getAsJsonObject();
                NPCS.put(e.getKey(), new Npc(e.getKey(), str(n, "name", e.getKey()), str(n, "greet", "")));
            }
            if (o.has("quests")) for (var e : o.getAsJsonObject("quests").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                JsonObject q = e.getValue().getAsJsonObject();
                List<Goal> goals = new ArrayList<>();
                if (q.has("goals")) for (var g : q.getAsJsonArray("goals")) {
                    JsonObject go = g.getAsJsonObject();
                    String type = str(go, "type", "kill");
                    String target = type.equals("item") ? full(str(go, "item", "")) : str(go, "mob", "");
                    goals.add(new Goal(type, target, go.has("count") ? go.get("count").getAsInt() : 1,
                            go.has("elite") && go.get("elite").getAsBoolean(), str(go, "label", "")));
                }
                JsonObject r = q.has("reward") ? q.getAsJsonObject("reward") : new JsonObject();
                List<String[]> items = new ArrayList<>();
                if (r.has("items")) for (var x : r.getAsJsonArray("items")) {
                    JsonArray a = x.getAsJsonArray();
                    items.add(new String[]{full(a.get(0).getAsString()), String.valueOf(a.size() > 1 ? a.get(1).getAsInt() : 1)});
                }
                String npc = str(q, "npc", "");
                QUESTS.put(e.getKey(), new Quest(e.getKey(), str(q, "name", e.getKey()), npc, str(q, "turnIn", npc),
                        q.has("level") ? q.get("level").getAsInt() : 1, str(q, "after", ""), goals,
                        new Reward(r.has("exp") ? r.get("exp").getAsLong() : 0, r.has("gold") ? r.get("gold").getAsLong() : 0, items),
                        lines(q, "offer"), lines(q, "progress"), lines(q, "done"), str(q, "summary", "")));
            }
            RpgCore.LOG.info("[rpgcore] 퀘스트 NPC {}명, 퀘스트 {}개 읽음", NPCS.size(), QUESTS.size());
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] quests.json 읽기 실패: {}", ex.toString());
        }
    }

    private static List<String> lines(JsonObject q, String k) {
        List<String> l = new ArrayList<>();
        if (q.has(k)) {
            if (q.get(k).isJsonArray()) for (var x : q.getAsJsonArray(k)) l.add(x.getAsString());
            else l.add(q.get(k).getAsString());
        }
        return l;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) ? o.get(k).getAsString() : def;
    }

    private static String full(String id) {
        return id.isEmpty() || id.contains(":") ? id : RpgCore.MODID + ":" + id;
    }

    public static String npcName(String id) {
        Npc n = NPCS.get(id);
        return n == null ? id : n.name();
    }

    /** 이름으로 NPC 찾기 (클라: 주민 이름표 → NPC) */
    public static String npcByName(String name) {
        for (Npc n : NPCS.values()) if (n.name().equals(name)) return n.id();
        return null;
    }

    // ───────── 상태 판정 (서버·클라 공용) ─────────

    /** 받을 수 있나: 안 깼고, 진행 중 아니고, 레벨 되고, 선행 퀘스트 깸 */
    public static boolean available(Quest q, int level, Map<String, int[]> active, java.util.Set<String> done) {
        return !done.contains(q.id()) && !active.containsKey(q.id()) && level >= q.minLevel()
                && (q.after().isEmpty() || done.contains(q.after()));
    }

    /** 레벨만 모자라서 못 받는 상태 (곧 받을 수 있음 표시용) */
    public static boolean soon(Quest q, int level, Map<String, int[]> active, java.util.Set<String> done) {
        return !done.contains(q.id()) && !active.containsKey(q.id()) && level < q.minLevel()
                && (q.after().isEmpty() || done.contains(q.after()));
    }

    /** 목표 i 의 현재 수치 (kill = 저장된 진행도, item = 인벤 개수) */
    public static int current(Quest q, int i, int[] prog, java.util.function.ToIntFunction<String> itemCount) {
        Goal g = q.goals().get(i);
        int v = g.type().equals("item") ? itemCount.applyAsInt(g.target()) : (prog != null && i < prog.length ? prog[i] : 0);
        return Math.min(v, g.count());
    }

    public static boolean complete(Quest q, int[] prog, java.util.function.ToIntFunction<String> itemCount) {
        for (int i = 0; i < q.goals().size(); i++) if (current(q, i, prog, itemCount) < q.goals().get(i).count()) return false;
        return true;
    }
}
