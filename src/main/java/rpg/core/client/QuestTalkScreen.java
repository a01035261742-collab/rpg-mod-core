package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.net.Net;
import rpg.core.quest.Quests;

/**
 * NPC 대화 창 (메이플식, 화면 아래쪽).
 * 처음: 인사 + 이 NPC 퀘스트 목록 → 고르면 대사를 한 줄씩(다음) → 마지막에 [수락]/[거절] 또는 [완료]
 */
public class QuestTalkScreen extends Screen {
    private static final int W = 340, H = 150;
    private final int entity;
    private final String npc;
    private int x, y;

    private Quests.Quest sel;          // 고른 퀘스트 (null = 목록)
    private int mode;                  // 0 받기 1 진행 중 2 완료
    private List<String> lines = List.of();
    private int page;

    private record Entry(Quests.Quest q, int kind) {}   // kind 2 완료 가능 · 1 받기 · 3 진행 중 · 4 레벨 부족

    public QuestTalkScreen(int entity, String npc) {
        super(Component.literal(Quests.npcName(npc)));
        this.entity = entity;
        this.npc = npc;
    }

    public static void open(int entity, String npc) {
        Minecraft.getInstance().setScreen(new QuestTalkScreen(entity, npc));
    }

    private List<Entry> entries() {
        List<Entry> l = new ArrayList<>();
        for (Quests.Quest q : Quests.QUESTS.values())
            if (ClientQuests.ACTIVE.containsKey(q.id()) && q.turnIn().equals(npc) && ClientQuests.complete(q)) l.add(new Entry(q, 2));
        for (Quests.Quest q : Quests.QUESTS.values())
            if (q.npc().equals(npc) && ClientQuests.available(q)) l.add(new Entry(q, 1));
        for (Quests.Quest q : Quests.QUESTS.values())
            if (ClientQuests.ACTIVE.containsKey(q.id()) && q.turnIn().equals(npc) && !ClientQuests.complete(q)) l.add(new Entry(q, 3));
        for (Quests.Quest q : Quests.QUESTS.values())
            if (q.npc().equals(npc) && Quests.soon(q, ClientQuests.level(), ClientQuests.ACTIVE, ClientQuests.DONE)) l.add(new Entry(q, 4));
        return l;
    }

    @Override
    protected void init() {
        x = width / 2 - W / 2;
        y = height - H - 40;
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int by = y + H - 24;
        if (sel == null) {
            List<Entry> es = entries();
            int i = 0;
            for (Entry en : es) {
                if (i >= 5) break;
                int ry = y + 46 + i * 17;
                String prefix = switch (en.kind()) { case 2 -> "§6? "; case 1 -> "§e! "; case 3 -> "§7… "; default -> "§8Lv." + en.q().minLevel() + " "; };
                Button b = Button.builder(Component.literal(prefix + en.q().name()), btn -> pick(en))
                        .bounds(x + 10, ry, W - 20, 15).build();
                b.active = en.kind() != 4;
                addRenderableWidget(b);
                i++;
            }
            addRenderableWidget(Button.builder(Component.literal("대화 끝내기"), b -> onClose()).bounds(x + W - 90, by, 80, 18).build());
            return;
        }
        boolean last = page >= lines.size() - 1;
        if (!last) {
            addRenderableWidget(Button.builder(Component.literal("다음 ▶"), b -> { page++; rebuild(); }).bounds(x + W - 90, by, 80, 18).build());
        } else if (mode == 0) {
            addRenderableWidget(Button.builder(Component.literal("§a수락"), b -> {
                Net.CH.sendToServer(new Net.QuestAction(0, sel.id(), entity));
                back();
            }).bounds(x + W - 176, by, 80, 18).build());
            addRenderableWidget(Button.builder(Component.literal("거절"), b -> back()).bounds(x + W - 90, by, 80, 18).build());
        } else if (mode == 2) {
            addRenderableWidget(Button.builder(Component.literal("§6완료하기"), b -> {
                Net.CH.sendToServer(new Net.QuestAction(1, sel.id(), entity));
                back();
            }).bounds(x + W - 90, by, 80, 18).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("알겠어요"), b -> back()).bounds(x + W - 90, by, 80, 18).build());
        }
        if (page > 0) addRenderableWidget(Button.builder(Component.literal("◀"), b -> { page--; rebuild(); }).bounds(x + 10, by, 24, 18).build());
    }

    private void pick(Entry en) {
        sel = en.q();
        mode = en.kind() == 1 ? 0 : en.kind() == 2 ? 2 : 1;
        List<String> src = mode == 0 ? sel.offer() : mode == 2 ? sel.done() : sel.progress();
        lines = src.isEmpty() ? List.of(mode == 0 ? sel.summary() : mode == 2 ? "고마워요!" : "아직 다 못 했나요?") : src;
        page = 0;
        rebuild();
    }

    private void back() {
        sel = null;
        rebuild();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(x, y, x + W, y + H, 0xF0101820);
        g.fill(x, y, x + W, y + 1, 0xFF60E060);
        g.fill(x, y + H - 1, x + W, y + H, 0xFF305030);
        g.drawString(font, title.getString(), x + 10, y + 8, 0xFF70F070, true);

        if (sel == null) {
            var n = Quests.NPCS.get(npc);
            String greet = n == null || n.greet().isEmpty() ? "무슨 일이에요?" : n.greet();
            drawWrapped(g, greet, x + 10, y + 24, W - 20, 0xFFFFFFFF, 2);
            if (entries().isEmpty()) g.drawString(font, "지금은 부탁할 일이 없어요.", x + 10, y + 50, 0xFF909090, false);
        } else {
            String head = switch (mode) { case 0 -> "§e! "; case 2 -> "§6? "; default -> "§7… "; };
            g.drawString(font, head + sel.name(), x + 10, y + 22, 0xFFFFE070, true);
            drawWrapped(g, lines.isEmpty() ? "" : lines.get(Math.min(page, lines.size() - 1)), x + 10, y + 38, W - 20, 0xFFFFFFFF, 4);
            if (page >= lines.size() - 1) detail(g);
            String pg = (page + 1) + "/" + Math.max(1, lines.size());
            g.drawString(font, pg, x + 40, y + H - 19, 0xFF707070, false);
        }
        super.render(g, mx, my, pt);
    }

    /** 마지막 장: 목표·보상 */
    private void detail(GuiGraphics g) {
        int ty = y + 84;
        if (!sel.goals().isEmpty()) {
            StringBuilder sb = new StringBuilder("목표: ");
            for (int i = 0; i < sel.goals().size(); i++) {
                if (i > 0) sb.append(" · ");
                boolean ok = ClientQuests.current(sel, i) >= sel.goals().get(i).count();
                sb.append(ok ? "§a" : "§f").append(ClientQuests.goalText(sel, i)).append("§r");
            }
            g.drawString(font, sb.toString(), x + 10, ty, 0xFFC0C0C0, false);
            ty += 12;
        } else if (!sel.turnIn().equals(sel.npc())) {
            g.drawString(font, "목표: " + Quests.npcName(sel.turnIn()) + "에게 말 걸기", x + 10, ty, 0xFFC0C0C0, false);
            ty += 12;
        }
        if (mode != 1) {
            StringBuilder r = new StringBuilder("보상: ");
            Quests.Reward rw = sel.reward();
            if (rw.exp() > 0) r.append("§a").append(String.format("%,d", rw.exp())).append(" EXP  ");
            if (rw.gold() > 0) r.append("§6").append(String.format("%,d", rw.gold())).append(" G  ");
            for (String[] it : rw.items()) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(it[0]));
                r.append("§b").append(item == null ? it[0] : new ItemStack(item).getHoverName().getString());
                if (!it[1].equals("1")) r.append(" x").append(it[1]);
                r.append("  ");
            }
            g.drawString(font, r.toString(), x + 10, ty, 0xFFC0C0C0, false);
        }
    }

    private void drawWrapped(GuiGraphics g, String s, int x0, int y0, int w, int color, int maxLines) {
        List<FormattedCharSequence> ls = font.split(Component.literal(s), w);
        for (int i = 0; i < ls.size() && i < maxLines; i++) g.drawString(font, ls.get(i), x0, y0 + i * 11, color, false);
    }

    @Override
    public void tick() {
        // 수락·완료 후 서버 동기화가 오면 목록 갱신
        if (sel == null && minecraft != null && minecraft.level != null && minecraft.level.getGameTime() % 5 == 0) rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
