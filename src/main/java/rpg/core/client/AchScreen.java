package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 업적 창 (캐릭터 창 [업적] 탭 · U): 종류별 탭 5개, 못 한 것 위 · 달성한 것 아래, 휠·막대로 내려보기 */
public class AchScreen extends ScaledScreen {
    private static final String[] TABS = {"성장", "사냥", "몹별", "보스", "장비"};
    private static final int ROW = 24, TOP = 54;
    private static int tab = 1;   // 마지막으로 본 탭 기억
    private Net.AchSync data;
    private int x, y, scroll;
    private boolean barDrag;
    private double dragAcc;

    private AchScreen(Net.AchSync d) {
        super(Component.literal("업적"));
        this.data = d;
    }

    public static void request() {
        Net.CH.sendToServer(new Net.AchReq(0));
    }

    public static void receive(Net.AchSync d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AchScreen s) { s.data = d; s.rebuildWidgets(); }
        else mc.setScreen(new AchScreen(d));
    }

    /** 지금 탭 줄: 못 한 것 먼저 (원래 순서), 달성한 것 뒤 */
    private List<Net.AchRow> rows() {
        List<Net.AchRow> todo = new ArrayList<>(), done = new ArrayList<>();
        for (Net.AchRow r : data.rows()) if (r.tab() == tab) (r.done() ? done : todo).add(r);
        todo.addAll(done);
        return todo;
    }

    private int visible() { return (CharWin.bottom(y, CharWin.H) - (y + TOP)) / ROW; }
    private int maxScroll() { return Math.max(0, rows().size() - visible()); }

    @Override
    protected void init() {
        x = Ui.cx(width, CharWin.W);
        y = Ui.cy(height, CharWin.H);
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.ACH);
        int l = CharWin.left(x), r = CharWin.right(x, CharWin.W);
        int bw = (r - l - 80 - 4 * 4) / TABS.length;   // 오른쪽은 달성 수
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            addRenderableWidget(Ui.button(Component.literal(TABS[i]), b -> { tab = k; scroll = 0; rebuildWidgets(); })
                    .bounds(l + i * (bw + 4), y + 32, bw, 16).accent(i == tab).build());
        }
        scroll = Math.min(scroll, maxScroll());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CharWin.draw(g, font, x, y);
        int l = CharWin.left(x), r = CharWin.right(x, CharWin.W) - 8;
        long all = data.rows().size(), ok = data.rows().stream().filter(Net.AchRow::done).count();
        String head = ok + " / " + all + " 달성";
        g.drawString(font, head, CharWin.right(x, CharWin.W) - font.width(head), y + 36, Ui.GRAY, false);
        List<Net.AchRow> rs = rows();
        int vis = visible();
        for (int i = 0; i < vis && scroll + i < rs.size(); i++) {
            Net.AchRow a = rs.get(scroll + i);
            int ry = y + TOP + i * ROW;
            Ui.row(g, l, ry, r, ry + ROW - 2, false);
            if (a.done()) g.fill(l, ry, r, ry + ROW - 2, 0x3030A030);
            g.drawString(font, Ui.cut(font, (a.done() ? "✔ " : "") + a.name(), 120), l + 6, ry + 2, a.done() ? Ui.GREEN : Ui.TXT, true);
            g.drawString(font, a.kind(), l + 6, ry + 12, Ui.GRAY, false);
            String rw = Ui.cut(font, a.reward(), 110);
            g.drawString(font, rw, r - 6 - font.width(rw), ry + 2, a.medal() ? 0xFFFFA040 : Ui.VAL, true);
            String n = String.format("%,d / %,d", Math.min(a.cur(), a.need()), a.need());
            g.drawString(font, n, r - 6 - font.width(n), ry + 12, a.done() ? Ui.GREEN : Ui.TXT, false);
            int bx = l + 130, bw = r - 6 - 110 - bx;
            Ui.bar(g, bx, ry + 14, Math.max(20, bw), 4, (float) Math.min(1.0, a.cur() / (double) Math.max(1, a.need())), a.done() ? 0xFF5AC85A : 0xFFC89632);
        }
        if (rs.isEmpty()) g.drawString(font, "없음", l + 6, y + TOP + 4, Ui.GRAY, false);
        // 스크롤 막대
        if (rs.size() > vis) {
            int bx = CharWin.right(x, CharWin.W) - 5, top = y + TOP, h = vis * ROW - 2;
            g.fill(bx, top, bx + 4, top + h, Ui.IN1);
            int th = Math.max(12, h * vis / rs.size());
            int ty = top + (h - th) * scroll / Math.max(1, maxScroll());
            g.fill(bx, ty, bx + 4, ty + th, Ui.G3);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int ns = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta)));
        if (ns != scroll) scroll = ns;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int bx = CharWin.right(x, CharWin.W) - 5;
        barDrag = maxScroll() > 0 && mx >= bx - 3 && mx < bx + 7 && my >= y + TOP && my < y + TOP + visible() * ROW;
        dragAcc = 0;
        if (barDrag) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { barDrag = false; return super.mouseReleased(mx, my, button); }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (barDrag) {   // 막대 끌기
            dragAcc += dy * rows().size() / (double) visible();
            int step = (int) (dragAcc / ROW);
            if (step != 0) {
                dragAcc -= step * ROW;
                scroll = Math.max(0, Math.min(maxScroll(), scroll + step));
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.ACH_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
