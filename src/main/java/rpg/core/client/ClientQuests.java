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
