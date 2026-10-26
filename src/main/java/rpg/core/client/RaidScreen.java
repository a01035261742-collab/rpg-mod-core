package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 레이드 단말기 창: 보스 · 빈 경기장 · 파티원 레벨·거리·오늘 보상 + [입장 요청] */
public class RaidScreen extends Screen {
    private static final int W = 270;
    private Net.RaidInfo info;
    private int x, y, h;
    private Button go;

    public RaidScreen(Net.RaidInfo info) { super(Component.literal("레이드")); this.info = info; }

    public static void open(Net.RaidInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RaidScreen r && r.info.raid().equals(info.raid())) { r.info = info; r.rebuildWidgets(); }
        else mc.setScreen(new RaidScreen(info));
    }

    private String[] head() {
        String[] t = info.title().split("\\|");
        return t.length >= 4 ? t : new String[]{info.raid(), "?", "0", "0"};
    }

    @Override
    protected void init() {
        h = 120 + info.rows().size() * 14 + (info.busy().isEmpty() ? 0 : 14);
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
        String[] t = head();
        Ui.panel(g, x, y, W, h, 0xFFB050E0);
        g.drawCenteredString(font, t[0] + " 레이드", x + W / 2, y + 10, 0xFFE0A0FF);
        g.drawString(font, "Lv." + t[1] + " 보스 · 입장 Lv." + info.minLevel() + " 이상 · 1~4명 · 15분", x + 16, y + 28, 0xFFC8C8C8, false);
        g.drawString(font, "빈 경기장 " + t[2] + " / " + t[3] + " · 보상은 하루 1번 (오전 6시)", x + 16, y + 42, 0xFFC8C8C8, false);
        g.drawString(font, "파티원", x + 16, y + 60, 0xFF8A90B0, false);
        g.drawString(font, "레벨", x + 116, y + 60, 0xFF8A90B0, false);
        g.drawString(font, "근처", x + 170, y + 60, 0xFF8A90B0, false);
        g.drawString(font, "오늘 보상", x + 206, y + 60, 0xFF8A90B0, false);
        int ry = y + 74;
        for (String row : info.rows()) {
            String[] c = row.split("\\|");
            boolean lv = c.length > 2 && "1".equals(c[2]), near = c.length > 3 && "1".equals(c[3]), rw = c.length <= 4 || "1".equals(c[4]);
            g.drawString(font, c[0], x + 16, ry, 0xFFFFFFFF, false);
            g.drawString(font, "Lv." + (c.length > 1 ? c[1] : "?") + (lv ? " ✓" : " ✗"), x + 116, ry, lv ? 0xFF7CFC7C : 0xFFFF6060, false);
            g.drawString(font, near ? "✓" : "✗", x + 178, ry, near ? 0xFF7CFC7C : 0xFFFF6060, false);
            g.drawString(font, rw ? "가능" : "받음", x + 212, ry, rw ? 0xFF7CFC7C : 0xFFFFB040, false);
            ry += 14;
        }
        g.drawString(font, "\"받음\" = 같이 클리어는 되지만 보상 없음", x + 16, ry + 2, 0xFF8A90B0, false);
        ry += 14;
        if (!info.busy().isEmpty()) g.drawString(font, info.busy(), x + 16, ry + 2, 0xFFFFB040, false);
        else if (!info.canGo()) g.drawString(font, "파티장만, 모두 조건을 채워야 해요", x + 16, ry + 2, 0xFF8A90B0, false);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
