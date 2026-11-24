package rpg.core.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import rpg.core.quest.Quests;

/** 약도 데이터 (assets/rpgcore/maps/world.json) + 지금 있는 곳 (서버가 알려줌) */
public final class MapData {
    private MapData() {}

    public record Node(String id, double x, double y, String type, String title, String sub, List<String> mobs, List<String> mobNames,
                       List<String> mats, List<String> npcs, List<String> points, String town, int exitTo) {}
    public record Region(String name, String image, List<Node> nodes, List<String[]> edges) {}
    public record Bld(String label, String kind, float u, float v) {}
    public record Town(String title, String image, List<String> names, List<String> npcs, List<Bld> buildings) {}
    /** 몹·보스 정보 (약도 상세 창) */
    public record MobInfo(String name, int lv, long exp, long gold, boolean boss, boolean raid, List<String> skills, List<String> drops, List<String> rewards, int respawn,
                          String variant, int variantLv, List<String> variantDrops) {}
    public static final Map<String, MobInfo> MOBS = new HashMap<>();

    public static final List<Region> REGIONS = new ArrayList<>();
    public static final Map<String, Town> TOWNS = new HashMap<>();
    public static final Map<String, String> NPC_NAMES = new HashMap<>();
    private static boolean loaded;

    /** 서버: 지금 서 있는 사냥터 몹 목록 ("a+b") · 근처 마을 이름 */
    public static String hereMobs = "", hereTown = "";

    public static void setHere(String mobs, String town) { hereMobs = mobs; hereTown = town; }

    private static List<String> strs(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (o.has(k)) for (JsonElement e : o.getAsJsonArray(k)) out.add(e.getAsString());
        return out;
    }

    private static String str(JsonObject o, String k) { return o.has(k) ? o.get(k).getAsString() : ""; }

    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("rpgcore", "maps/world.json"));
            if (res.isEmpty()) return;
            try (var r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                root.getAsJsonObject("npcNames").entrySet().forEach(e -> NPC_NAMES.put(e.getKey(), e.getValue().getAsString()));
                for (JsonElement re : root.getAsJsonArray("regions")) {
                    JsonObject ro = re.getAsJsonObject();
                    List<Node> nodes = new ArrayList<>();
                    for (JsonElement ne : ro.getAsJsonArray("nodes")) {
                        JsonObject n = ne.getAsJsonObject();
                        nodes.add(new Node(str(n, "id"), n.get("x").getAsDouble(), n.get("y").getAsDouble(), str(n, "type"), str(n, "title"), str(n, "sub"),
                                strs(n, "mobs"), strs(n, "mobNames"), strs(n, "mats"), strs(n, "npcs"), strs(n, "points"), str(n, "town"),
                                n.has("exitTo") ? n.get("exitTo").getAsInt() : 0));
                    }
                    List<String[]> edges = new ArrayList<>();
                    for (JsonElement ee : ro.getAsJsonArray("edges")) {
                        JsonArray a = ee.getAsJsonArray();
                        edges.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
                    }
                    REGIONS.add(new Region(str(ro, "name"), str(ro, "image"), nodes, edges));
                }
                root.getAsJsonObject("towns").entrySet().forEach(e -> {
                    JsonObject t = e.getValue().getAsJsonObject();
                    List<Bld> b = new ArrayList<>();
                    for (JsonElement be : t.getAsJsonArray("buildings")) {
                        JsonObject bo = be.getAsJsonObject();
                        b.add(new Bld(str(bo, "label"), str(bo, "kind"), bo.get("u").getAsFloat(), bo.get("v").getAsFloat()));
                    }
                    TOWNS.put(e.getKey(), new Town(str(t, "title"), str(t, "image"), strs(t, "names"), strs(t, "npcs"), b));
                });
                root.getAsJsonObject("mobInfo").entrySet().forEach(e -> {
                    JsonObject m = e.getValue().getAsJsonObject();
                    List<String> drops = new ArrayList<>();
                    JsonObject vr = m.has("variant") ? m.getAsJsonObject("variant") : null;   // 변종 몹 (드물게 나옴)
                    if (m.has("drops")) for (JsonElement de : m.getAsJsonArray("drops")) drops.add(de.getAsJsonArray().get(0).getAsString());
                    MOBS.put(e.getKey(), new MobInfo(str(m, "name"), m.get("lv").getAsInt(), m.get("exp").getAsLong(), m.get("gold").getAsLong(),
                            m.has("boss") && m.get("boss").getAsBoolean(), m.has("raid") && m.get("raid").getAsBoolean(),
                            strs(m, "skills"), drops, strs(m, "rewards"), m.has("respawn") ? m.get("respawn").getAsInt() : 0,
                            vr == null ? "" : str(vr, "name"), vr == null || !vr.has("lv") ? 0 : vr.get("lv").getAsInt(), vr == null ? List.of() : strs(vr, "drops")));
                });
            }
        } catch (Exception e) {
            rpg.core.RpgCore.LOG.warn("약도 데이터 읽기 실패", e);
        }
    }

    /** 지금 있는 곳 노드 id (없으면 "") */
    public static String hereNode() {
        if (!hereTown.isEmpty())
            for (var en : TOWNS.entrySet()) if (en.getValue().names().contains(hereTown) || en.getValue().title().equals(hereTown))
                for (Region r : REGIONS) for (Node n : r.nodes()) if (en.getKey().equals(n.town())) return n.id();
        if (!hereMobs.isEmpty()) {
            Set<String> ms = Set.of(hereMobs.split("\\+"));
            for (Region r : REGIONS) for (Node n : r.nodes()) if (!n.mobs().isEmpty() && ms.containsAll(n.mobs()) && n.mobs().containsAll(ms)) return n.id();
        }
        return "";
    }

    public static int regionOf(String nodeId) {
        for (int i = 0; i < REGIONS.size(); i++) for (Node n : REGIONS.get(i).nodes()) if (n.id().equals(nodeId)) return i;
        return -1;
    }

    /** 고정한·끝난 퀘스트의 목적지 노드 id */
    public static Set<String> targets() {
        Set<String> out = new LinkedHashSet<>();
        for (Quests.Quest q : ClientQuests.tracked()) {
            boolean done = ClientQuests.complete(q);
            List<String> npcs = new ArrayList<>(), pts = new ArrayList<>(), mobs = new ArrayList<>(), items = new ArrayList<>();
            if (done) {
                if (q.toPoint()) pts.add(q.point()); else npcs.add(q.turnIn());
            } else if (q.goals().isEmpty()) {
                if (q.toPoint()) pts.add(q.point()); else npcs.add(q.turnIn());
            } else {
                if (q.fetch()) pts.add(q.fetchPoint());
                for (int i = 0; i < q.goals().size(); i++) {
                    var g = q.goals().get(i);
                    if (ClientQuests.current(q, i) >= g.count()) continue;
                    if (g.type().equals("kill")) mobs.add(g.target());
                    else if (g.type().equals("item")) items.add(g.target());
                }
            }
            for (Region r : REGIONS) for (Node n : r.nodes()) {
                boolean hit = n.npcs().stream().anyMatch(npcs::contains) || n.points().stream().anyMatch(pts::contains)
                        || n.mobs().stream().anyMatch(mobs::contains) || n.mats().stream().anyMatch(items::contains);
                if (hit) out.add(n.id());
            }
        }
        return out;
    }
}
