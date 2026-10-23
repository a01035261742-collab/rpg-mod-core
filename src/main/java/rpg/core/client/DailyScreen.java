package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 일일 퀘스트 창 (메뉴 M → 일일 퀘스트): 오늘 3개, 진행도, [완료] [다시 뽑기] */
public class DailyScreen extends Screen {
    private static final int W = 300, ROW = 30;
    private int H = 160;
    private Net.DailySync data;
    private int x, y;

    private DailyScreen(Net.DailySync d) {
        super(Component.literal("일일 퀘스트"));
        this.data = d;
    }

    public static void request() {
        Net.CH.sendToServer(new Net.DailyAction(0, 0));
    }

    public static void receive(Net.DailySync d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DailyScreen ds) { ds.data = d; ds.rebuildWidgets(); }
        else mc.setScreen(new DailyScreen(d));
    }

    @Override
    protected void init() {
        H = 26 + data.rows().size() * ROW + 14 + Ui.FOOT + 4;   // 줄 · 보너스 줄 · 버튼줄
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        for (int i = 0; i < data.rows().size(); i++) {
            final int k = i;
            Net.DailyRow r = data.rows().get(i);
            int ry = y + 26 + i * ROW;
            Button done = Button.builder(Component.literal(r.done() ? "완료됨" : "완료"), b -> Net.CH.sendToServer(new Net.DailyAction(1, k)))
                    .bounds(x + W - 104, ry + 4, 48, 18).build();
            done.active = !r.done() && r.prog() >= r.need();
            addRenderableWidget(done);
            Button re = Button.builder(Component.literal("다시"), b -> Net.CH.sendToServer(new Net.DailyAction(2, k))).bounds(x + W - 52, ry + 4, 44, 18).build();
            re.active = !r.done() && data.rerolls() > 0;
            addRenderableWidget(re);
        }
        addRenderableWidget(Ui.menu(x, y, H));
        addRenderableWidget(Ui.close(x, y, W, H, this::onClose));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        String reset = "초기화까지 " + data.minutes() / 60 + "시간 " + data.minutes() % 60 + "분 · 다시 뽑기 " + data.rerolls() + "번";
        Ui.frame(g, font, x, y, W, H, 0xFFD27FFF, "일일 퀘스트", reset, 0xFF909090);
        Ui.footLine(g, x, y, W, H);
        for (int i = 0; i < data.rows().size(); i++) {
            Net.DailyRow r = data.rows().get(i);
            int ry = y + 26 + i * ROW;
            g.fill(x + 6, ry, x + W - 6, ry + ROW - 4, r.done() ? 0x3030C030 : 0x30FFFFFF);
            g.drawString(font, Ui.cut(font, r.label(), W - 124), x + 12, ry + 4, r.done() ? 0xFF80C080 : 0xFFFFFFFF, false);
            String p = r.prog() + " / " + r.need();
            g.drawString(font, p, x + 12, ry + 15, r.prog() >= r.need() ? 0xFF55FF55 : 0xFFFFD24A, false);
            // 진행 막대
            int bw = 90, bx = x + 60, by = ry + 17;
            g.fill(bx, by, bx + bw, by + 4, 0xFF303030);
            g.fill(bx, by, bx + (int) (bw * Math.min(1.0, r.prog() / (double) Math.max(1, r.need()))), by + 4, 0xFFD27FFF);
        }
        String bonus = data.bonus() ? "오늘 보너스 받음 ✔" : "3개 다 하면 보너스: 주문서 조각 · 물약 · 귀환 주문서 · 강력한 불꽃";
        g.drawString(font, Ui.cut(font, bonus, W - 16), x + 8, y + H - Ui.FOOT - 14, data.bonus() ? 0xFF55FF55 : 0xFFA0A0A0, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
