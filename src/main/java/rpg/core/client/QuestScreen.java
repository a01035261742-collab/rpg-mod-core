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
public class QuestScreen extends ScaledScreen {
    private static final int W = CharWin.W, H = CharWin.H, LW = 125, ROW = 16, LX = 85, LY = 52, LB = 282, DB = 256;
    private static final String[] TABS = {"진행", "받기", "완료"};
    private int x, y, tab, scroll;
    private String sel;
    private net.minecraft.client.gui.components.Button pin;
    private long pinFullUntil;
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
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.QUEST);
        for (int i = 0; i < 3; i++) {
            int t = i;
            addRenderableWidget(Ui.button(Component.literal(TABS[i]), b -> { tab = t; scroll = 0; sel = null; rebuildWidgets(); })
                    .bounds(x + LX + 1 + i * 42, y + 30, 38, 16).accent(i == tab).build());
        }
        abandon = addRenderableWidget(Ui.button(Component.literal("포기"), b -> {
            if (sel == null) return;
            long now = System.currentTimeMillis();
            if (now < confirmUntil) {
                Net.CH.sendToServer(new Net.QuestAction(2, sel, 0));
                sel = null;
                confirmUntil = 0;
            } else confirmUntil = now + 3000;
        }).bounds(x + W - 76, y + 262, 56, 16).build());
        pin = addRenderableWidget(Ui.button(Component.literal("고정"), b -> {
            if (sel == null) return;
            if (!ClientQuests.togglePin(sel)) pinFullUntil = System.currentTimeMillis() + 2000;
        }).bounds(x + W - 144, y + 262, 64, 16).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CharWin.draw(g, font, x, y);
        String cnt = ClientQuests.DONE.size() + "/" + Quests.QUESTS.size();
        g.drawString(font, cnt, x + W - 20 - font.width(cnt), y + 34, Ui.GRAY, false);

        List<Quests.Quest> l = list();
        if (sel != null && l.stream().noneMatch(q -> q.id().equals(sel))) sel = null;
        if (sel == null && !l.isEmpty()) sel = l.get(0).id();
        int ly = y + LY, rows = (LB - LY) / ROW;
        Ui.vsep(g, x + LX + LW + 4, y + 28, y + LB);
        for (int r = 0; r < rows; r++) {
            int i = r + scroll;
            if (i >= l.size()) break;
            Quests.Quest q = l.get(i);
            int ry = ly + r * ROW;
            boolean hover = mx >= x + LX && mx < x + LX + LW && my >= ry && my < ry + ROW;
            if (q.id().equals(sel) || hover) Ui.row(g, x + LX, ry, x + LX + LW, ry + ROW - 1, q.id().equals(sel));
            String mark = tab == 0 ? (ClientQuests.complete(q) ? "§a✔ " : ClientQuests.pinned(q.id()) ? "§e◆ " : "§7· ") : tab == 1 ? "§e! " : "§8✔ ";
            String nm = mark + "§f" + q.name();
            g.drawString(font, Ui.cut(font, nm, LW - 6), x + LX + 4, ry + 4, 0xFFFFFFFF, false);
        }
        if (l.isEmpty()) g.drawString(font, "없음", x + LX + 4, ly + 2, Ui.GRAY, false);

        abandon.visible = tab == 0 && sel != null;
        pin.visible = tab == 0 && sel != null;
        if (sel != null) pin.setMessage(Component.literal(System.currentTimeMillis() < pinFullUntil ? "§c최대 3개" : ClientQuests.pinned(sel) ? "§e고정 풀기" : "고정"));
        abandon.setMessage(Component.literal(System.currentTimeMillis() < confirmUntil ? "§c정말?" : "포기"));

        Quests.Quest q = sel == null ? null : Quests.QUESTS.get(sel);
        HOVER.clear();
        if (q != null) {   // 상세: 창 안에서만 보이게 자름
            Scaled.scissor(g, x + LX + LW + 8, y + 28, x + W - 16, y + DB);
            detail(g, q);
            g.disableScissor();
        }
        super.render(g, mx, my, pt);
        for (Hover hv : HOVER)   // 보상 아이콘 이름
            if (mx >= hv.x() && mx < hv.x() + 16 && my >= hv.y() && my < hv.y() + 16 && my < y + DB) { g.renderTooltip(font, hv.stack(), mx, my); break; }
    }

    private void detail(GuiGraphics g, Quests.Quest q) {
        int dx = x + LX + LW + 14, dy = y + 34, dw = W - LX - LW - 34;
        g.drawString(font, Ui.cut(font, q.name(), dw), dx, dy, Ui.VAL, true);
        dy += 11;
        g.drawString(font, "Lv " + q.minLevel(), dx, dy, Ui.GRAY, false);
        dy += 12;
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
        if (r.gold() > 0) { g.drawString(font, " · " + String.format("%,d", r.gold()) + " G", dx, dy, 0xFFFFD86A, false); dy += 10; }
        dy = icons(g, r.items(), dx, dy, dw, false);
        if (!r.choose().isEmpty()) {   // 고르는 보상 (완료할 때 NPC 대화에서 하나)
            g.drawString(font, "하나 골라 받기", dx, dy + 1, 0xFFC8A8FF, false);
            dy = icons(g, r.choose(), dx, dy + 11, dw, true);
        }
    }

    /** 보상 아이콘 줄 (마우스 올리면 이름) — 칸이 모자라면 다음 줄 */
    private int icons(GuiGraphics g, List<String[]> items, int dx, int dy, int dw, boolean pick) {
        int ix = dx + 2;
        for (String[] it : items) {
            if (ix + 18 > dx + dw) { ix = dx + 2; dy += 19; }
            ItemStack rs = rpg.core.item.ItemIds.stack(it[0], Integer.parseInt(it[1]));
            if (rs.isEmpty()) continue;
            if (pick) g.fill(ix - 1, dy - 1, ix + 17, dy + 17, 0x60A080FF);
            g.renderFakeItem(rs, ix, dy);
            g.renderItemDecorations(font, rs, ix, dy);
            HOVER.add(new Hover(ix, dy, rs));
            ix += 20;
        }
        return dy + 20;
    }

    private record Hover(int x, int y, ItemStack stack) {}
    private final List<Hover> HOVER = new java.util.ArrayList<>();

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        List<Quests.Quest> l = list();
        int ly = y + LY, rows = (LB - LY) / ROW;
        for (int r = 0; r < rows; r++) {
            int i = r + scroll;
            if (i >= l.size()) break;
            int ry = ly + r * ROW;
            if (mx >= x + LX && mx < x + LX + LW && my >= ry && my < ry + ROW) {
                sel = l.get(i).id();
                confirmUntil = 0;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int rows = (LB - LY) / ROW;
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
