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

/** 퀘스트 창 (J): 왼쪽 목록 (진행 중 / 받을 수 있음 / 완료 탭), 오른쪽 상세 */
public class QuestScreen extends Screen {
    private static final int W = 360, H = 230, LW = 130, ROW = 14;
    private static final String[] TABS = {"진행 중", "받을 수 있음", "완료"};
    private int x, y, tab, scroll;
    private String sel;
    private Button abandon;
    private long confirmUntil;

    public QuestScreen() {
        super(Component.literal("퀘스트"));
    }

    private List<Quests.Quest> list() {
        List<Quests.Quest> l = new ArrayList<>();
        for (Quests.Quest q : Quests.QUESTS.values()) {
            boolean ok = switch (tab) {
                case 0 -> ClientQuests.ACTIVE.containsKey(q.id());
                case 1 -> ClientQuests.available(q);
                default -> ClientQuests.DONE.contains(q.id());
            };
            if (ok) l.add(q);
        }
        if (tab == 0) {   // 받은 순서
            l.clear();
            l.addAll(ClientQuests.active());
        }
        return l;
    }

    @Override
    protected void init() {
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        for (int i = 0; i < 3; i++) {
            int t = i;
            addRenderableWidget(Button.builder(Component.literal(TABS[i]), b -> { tab = t; scroll = 0; sel = null; })
                    .bounds(x + 6 + i * 84, y + 20, 82, 16).build());
        }
        abandon = addRenderableWidget(Button.builder(Component.literal("포기"), b -> {
            if (sel == null) return;
            long now = System.currentTimeMillis();
            if (now < confirmUntil) {
                Net.CH.sendToServer(new Net.QuestAction(2, sel, 0));
                sel = null;
                confirmUntil = 0;
            } else confirmUntil = now + 3000;
        }).bounds(x + W - 104, y + H - Ui.FOOT, 48, Ui.BTN_H).build());
        addRenderableWidget(Ui.menu(x, y, H));
        addRenderableWidget(Ui.close(x, y, W, H, this::onClose));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.frame(g, font, x, y, W, H, 0xFFFFE070, "퀘스트 (J)", "완료 " + ClientQuests.DONE.size() + " / " + Quests.QUESTS.size(), 0xFF909090);
        Ui.footLine(g, x, y, W, H);

        List<Quests.Quest> l = list();
        if (sel != null && l.stream().noneMatch(q -> q.id().equals(sel))) sel = null;
        if (sel == null && !l.isEmpty()) sel = l.get(0).id();
        int ly = y + 42, rows = (H - 42 - Ui.FOOT - 8) / ROW;
        g.fill(x + 6, ly - 2, x + 6 + LW, y + H - Ui.FOOT - 6, 0x30FFFFFF);
        for (int r = 0; r < rows; r++) {
            int i = r + scroll;
            if (i >= l.size()) break;
            Quests.Quest q = l.get(i);
            int ry = ly + r * ROW;
            boolean hover = mx >= x + 6 && mx < x + 6 + LW && my >= ry && my < ry + ROW;
            if (q.id().equals(sel)) g.fill(x + 6, ry, x + 6 + LW, ry + ROW, 0x60FFE070);
            else if (hover) g.fill(x + 6, ry, x + 6 + LW, ry + ROW, 0x30FFFFFF);
            String mark = tab == 0 ? (ClientQuests.complete(q) ? "§a✔ " : "§7· ") : tab == 1 ? "§e! " : "§8✔ ";
            String nm = mark + "§fLv." + q.minLevel() + " " + q.name();
            g.drawString(font, Ui.cut(font, nm, LW - 6), x + 9, ry + 3, 0xFFFFFFFF, false);
        }
        if (l.isEmpty()) g.drawString(font, tab == 1 ? "받을 수 있는 퀘스트 없음" : "없음", x + 10, ly + 2, 0xFF808080, false);

        abandon.visible = tab == 0 && sel != null;
        abandon.setMessage(Component.literal(System.currentTimeMillis() < confirmUntil ? "§c정말?" : "포기"));

        Quests.Quest q = sel == null ? null : Quests.QUESTS.get(sel);
        if (q != null) {   // 상세: 창 안에서만 보이게 자름
            g.enableScissor(x + LW + 12, y + 40, x + W - 6, y + H - Ui.FOOT - 6);
            detail(g, q);
            g.disableScissor();
        }
        super.render(g, mx, my, pt);
    }

    private void detail(GuiGraphics g, Quests.Quest q) {
        int dx = x + LW + 16, dy = y + 42, dw = W - LW - 24;
        g.drawString(font, Ui.cut(font, q.name(), dw), dx, dy, 0xFFFFE070, true);
        dy += 13;
        String from = "받는 곳: " + Quests.npcName(q.npc());
        if (q.toPoint()) from += "  →  " + Quests.pointName(q.point()) + "에 두고 오기";
        else if (!q.turnIn().equals(q.npc())) from += "  →  완료: " + Quests.npcName(q.turnIn());
        dy = Ui.wrap(g, font, from, dx, dy, dw, 0xFF80C0FF, 2) + 3;
        if (!q.summary().isEmpty()) {
            for (FormattedCharSequence s : font.split(Component.literal(q.summary()), dw)) {
                g.drawString(font, s, dx, dy, 0xFFD0D0D0, false);
                dy += 10;
            }
            dy += 4;
        }
        g.drawString(font, "목표", dx, dy, 0xFFA0A0A0, false);
        dy += 11;
        if (q.goals().isEmpty()) {
            g.drawString(font, " · " + Quests.finishName(q) + (q.toPoint() ? "에 가기" : "에게 말 걸기"), dx, dy, 0xFFFFFFFF, false);
            dy += 11;
        }
        boolean active = ClientQuests.ACTIVE.containsKey(q.id()), done = ClientQuests.DONE.contains(q.id());
        for (int i = 0; i < q.goals().size(); i++) {
            boolean ok = done || (active && ClientQuests.current(q, i) >= q.goals().get(i).count());
            g.drawString(font, Ui.cut(font, (ok ? "§a ✔ " : " · ") + ClientQuests.goalText(q, i), dw), dx, dy, 0xFFFFFFFF, false);
            dy += 11;
        }
        if (active && ClientQuests.complete(q)) {
            g.drawString(font, "§6→ " + Quests.finishName(q) + (q.toPoint() ? "에 두고 오세요" : "에게 가세요"), dx, dy, 0xFFFFFFFF, false);
            dy += 11;
        }
        dy += 4;
        g.drawString(font, "보상", dx, dy, 0xFFA0A0A0, false);
        dy += 11;
        Quests.Reward r = q.reward();
        if (r.exp() > 0) { g.drawString(font, " · " + String.format("%,d", r.exp()) + " EXP", dx, dy, 0xFFB0FF80, false); dy += 10; }
        if (r.gold() > 0) { g.drawString(font, " · " + String.format("%,d", r.gold()) + " G", dx, dy, 0xFFFFD24A, false); dy += 10; }
        for (String[] it : r.items()) {
            ItemStack rs = rpg.core.item.ItemIds.stack(it[0], 1);
            if (!rs.isEmpty()) g.renderFakeItem(rs, dx + 4, dy - 3);
            String nm = (rs.isEmpty() ? it[0] : rs.getHoverName().getString()) + (it[1].equals("1") ? "" : " x" + it[1]);
            g.drawString(font, Ui.cut(font, nm, dw - 24), dx + 24, dy + 2, 0xFF80E0FF, false);
            dy += 17;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        List<Quests.Quest> l = list();
        int ly = y + 42, rows = (H - 42 - Ui.FOOT - 8) / ROW;
        for (int r = 0; r < rows; r++) {
            int i = r + scroll;
            if (i >= l.size()) break;
            int ry = ly + r * ROW;
            if (mx >= x + 6 && mx < x + 6 + LW && my >= ry && my < ry + ROW) {
                sel = l.get(i).id();
                confirmUntil = 0;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int rows = (H - 42 - Ui.FOOT - 8) / ROW;
        scroll = Math.max(0, Math.min(Math.max(0, list().size() - rows), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.QUEST_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void toggle() {
        Minecraft.getInstance().setScreen(new QuestScreen());
    }
}
