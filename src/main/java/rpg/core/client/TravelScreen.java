package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 마을 이동 창: 이동의 돌(골드) · 마을 이동 주문서(3초 시전). 가 본 마을만 [이동], 안 가 본 곳은 ??? */
public class TravelScreen extends Screen {
    private static final int W = 240, ROW = 22;
    private final Net.TravelInfo info;
    private int x, y, h;

    public TravelScreen(Net.TravelInfo info) {
        super(Component.literal(info.mode() == 0 ? "이동의 돌" : "마을 이동 주문서"));
        this.info = info;
    }

    public static void open(Net.TravelInfo info) {
        Minecraft.getInstance().setScreen(new TravelScreen(info));
    }

    private String[] row(int i) {
        String[] c = info.rows().get(i).split("\\|");
        return c.length >= 3 ? c : new String[]{c[0], "0", "0"};
    }

    @Override
    protected void init() {
        h = 62 + info.rows().size() * ROW + Ui.FOOT;
        x = Ui.cx(width, W);
        y = Ui.cy(height, h);
        for (int i = 0; i < info.rows().size(); i++) {
            String[] c = row(i);
            boolean known = "1".equals(c[1]), here = "1".equals(c[2]);
            boolean money = info.mode() != 0 || info.gold() >= info.cost();
            String name = c[0];
            Button b = addRenderableWidget(Ui.button(Component.literal(here ? "여기" : "이동"), btn -> {
                Net.CH.sendToServer(new Net.TravelAction(info.mode(), name));
                onClose();
            }).bounds(x + W - 76, y + 40 + i * ROW, 60, 18).build());
            b.active = known && !here && money;
        }
        addRenderableWidget(Ui.button(Component.literal("닫기"), b -> onClose()).bounds(x + W - 76, y + h - Ui.FOOT + 3, 60, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.frame(g, font, x, y, W, h, 0xFF60D0E0, title.getString(), "", 0);
        Ui.footLine(g, x, y, W, h);
        String sub = info.mode() == 0
                ? "비용 " + String.format("%,d", info.cost()) + " G · 가진 골드 " + String.format("%,d", info.gold()) + " G"
                : "고르면 3초 시전 · 움직이거나 맞으면 취소";
        g.drawString(font, Ui.cut(font, sub, W - 20), x + 12, y + 24, info.mode() == 0 && info.gold() < info.cost() ? 0xFFFF7070 : 0xFFB8C4D0, false);
        for (int i = 0; i < info.rows().size(); i++) {
            String[] c = row(i);
            boolean known = "1".equals(c[1]), here = "1".equals(c[2]);
            int ry = y + 40 + i * ROW;
            g.fill(x + 10, ry - 2, x + W - 10, ry + 20, i % 2 == 0 ? 0xFF4A2028 : 0x18FFFFFF);
            g.drawString(font, known ? c[0] : "???", x + 16, ry + 5, known ? 0xFFFFFFFF : 0xFF707070, false);
            String st = here ? "지금 있는 마을" : known ? "" : "아직 가 보지 않음";
            if (!st.isEmpty()) g.drawString(font, st, x + W - 80 - font.width(st), ry + 5, 0xFFA89890, false);
        }
        g.drawString(font, "마을 근처에 가면 자동으로 등록돼요", x + 12, y + h - Ui.FOOT + 8, 0xFF8A6A60, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
