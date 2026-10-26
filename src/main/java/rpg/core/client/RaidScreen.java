package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 레이드 입구 창: 파티원 레벨·거리 ✓/✗ + [입장 요청] */
public class RaidScreen extends Screen {
    private static final int W = 260;
    private Net.RaidInfo info;
    private int x, y, h;
    private Button go;

    public RaidScreen(Net.RaidInfo info) { super(Component.literal("레이드")); this.info = info; }

    public static void open(Net.RaidInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RaidScreen r && r.info.raid().equals(info.raid())) { r.info = info; r.rebuildWidgets(); }
        else mc.setScreen(new RaidScreen(info));
    }

    @Override
    protected void init() {
        h = 92 + info.rows().size() * 14 + (info.busy().isEmpty() ? 0 : 14);
        x = Ui.cx(width, W); y = Ui.cy(height, h);
        go = addRenderableWidget(Ui.button(Component.literal("입장 요청"), b -> { Net.CH.sendToServer(new Net.RaidAction(0, info.raid())); onClose(); })
                .bounds(x + 16, y + h - 30, 110, 20).build());
        go.active = info.canGo() && info.busy().isEmpty();
        addRenderableWidget(Ui.button(Component.literal("새로고침"), b -> Net.CH.sendToServer(new Net.RaidAction(3, info.raid())))
                .bounds(x + W - 126, y + h - 30, 110, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.panel(g, x, y, W, h, 0xFFB050E0);
        g.drawCenteredString(font, "레이드 [" + info.raid() + "]", x + W / 2, y + 10, 0xFFE0A0FF);
        g.drawString(font, "입장 Lv." + info.minLevel() + " 이상 · 1~4명 · 제한 15분", x + 16, y + 28, 0xFFC8C8C8, false);
        g.drawString(font, "파티원", x + 16, y + 44, 0xFF8A90B0, false);
        g.drawString(font, "레벨", x + 130, y + 44, 0xFF8A90B0, false);
        g.drawString(font, "16칸 안", x + 190, y + 44, 0xFF8A90B0, false);
        int ry = y + 58;
        for (String row : info.rows()) {
            String[] c = row.split("\\|");
            boolean lv = "1".equals(c[2]), near = "1".equals(c[3]);
            g.drawString(font, c[0], x + 16, ry, 0xFFFFFFFF, false);
            g.drawString(font, "Lv." + c[1] + (lv ? " ✓" : " ✗"), x + 130, ry, lv ? 0xFF7CFC7C : 0xFFFF6060, false);
            g.drawString(font, near ? "✓" : "✗", x + 205, ry, near ? 0xFF7CFC7C : 0xFFFF6060, false);
            ry += 14;
        }
        if (!info.busy().isEmpty()) g.drawString(font, info.busy(), x + 16, ry + 2, 0xFFFFB040, false);
        else if (!info.canGo()) g.drawString(font, "파티장만, 모두 조건을 채워야 해요", x + 16, ry + 2, 0xFF8A90B0, false);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
