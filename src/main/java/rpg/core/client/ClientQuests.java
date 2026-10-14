package rpg.core.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.quest.Quests;

/** 클라: 내 퀘스트 상태 + 판정 도우미 */
public final class ClientQuests {
    private ClientQuests() {}

    public static final Map<String, int[]> ACTIVE = new LinkedHashMap<>();
    public static final Set<String> DONE = new LinkedHashSet<>();
    /** 지점 id → 위치 (차원, 블록) */
    public record Pt(String dim, net.minecraft.core.BlockPos pos) {}
    public static final Map<String, Pt> POINTS = new LinkedHashMap<>();

    public static void setPoints(Map<String, String> raw) {
        POINTS.clear();
        raw.forEach((k, v) -> {
            String[] a = v.split("\\|");
            if (a.length == 4) POINTS.put(k, new Pt(a[0], new net.minecraft.core.BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]))));
        });
    }

    /** 지점 표시: 2 두고 오기 가능(노란 ?) · 1 가져오기(노란 !) · 0 없음 */
    public static int pointMark(String point) {
        int mark = 0;
        for (Quests.Quest q : active()) {
            if (q.toPoint() && q.point().equals(point) && complete(q)) return 2;
            if (q.fetch() && q.fetchPoint().equals(point) && count(q.fetchItem()) == 0) mark = 1;
        }
        return mark;
    }

    public static void set(Map<String, int[]> active, List<String> done) {
        ACTIVE.clear();
        ACTIVE.putAll(active);
        DONE.clear();
        DONE.addAll(done);
    }

    public static int level() {
        return ClientData.level;
    }

    public static int count(String item) {
        var p = Minecraft.getInstance().player;
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(item));
        if (p == null || it == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
        return n;
    }

    public static boolean complete(Quests.Quest q) {
        return Quests.complete(q, ACTIVE.get(q.id()), ClientQuests::count);
    }

    public static int current(Quests.Quest q, int i) {
        return Quests.current(q, i, ACTIVE.get(q.id()), ClientQuests::count);
    }

    public static boolean available(Quests.Quest q) {
        return Quests.available(q, level(), ACTIVE, DONE);
    }

    /** 목표 한 줄: "들쥐 처치 7/15" */
    public static String goalText(Quests.Quest q, int i) {
        Quests.Goal g = q.goals().get(i);
        String what;
        if (!g.label().isEmpty()) what = g.label();
        else if (g.type().equals("item")) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(g.target()));
            what = (it == null ? g.target() : new ItemStack(it).getHoverName().getString()) + " 모으기";
        } else what = (g.elite() ? "엘리트 " : "") + mobName(g.target()) + " 처치";
        return what + " " + current(q, i) + "/" + g.count();
    }

    public static String mobName(String key) {
        if (key.equals("*")) return "아무 몹";
        var t = net.minecraft.client.resources.language.I18n.get("entity.rpgcore." + key);
        if (!t.startsWith("entity.")) return t;
        var t2 = net.minecraft.client.resources.language.I18n.get("entity.minecraft." + key);
        return t2.startsWith("entity.") ? key : t2;
    }

    /** NPC 상태: 2 완료 가능(노란 ?) · 1 받을 수 있음(노란 !) · 3 진행 중(회색 ?) · 0 없음 */
    public static int npcMark(String npc) {
        boolean avail = false, prog = false;
        for (Quests.Quest q : Quests.QUESTS.values()) {
            if (ACTIVE.containsKey(q.id()) && q.turnIn().equals(npc)) {
                if (complete(q)) return 2;
                prog = true;
            }
            if (q.npc().equals(npc) && available(q)) avail = true;
        }
        return avail ? 1 : prog ? 3 : 0;
    }

    /** 진행 중 퀘스트 (받은 순서) */
    public static List<Quests.Quest> active() {
        List<Quests.Quest> l = new ArrayList<>();
        for (String id : ACTIVE.keySet()) {
            Quests.Quest q = Quests.QUESTS.get(id);
            if (q != null) l.add(q);
        }
        return l;
    }
}
