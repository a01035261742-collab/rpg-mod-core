package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;
import rpg.core.skill.Defs;

/** 패시브 트리 (N): 가운데에서 퍼지는 별 모양. 칸을 클릭하면 1포인트 */
public class PassiveScreen extends Screen {
    private int CELL = 28, BOX = 20;
    private int cx, cy, px, py, pw, ph;

    public PassiveScreen() {
        super(Component.literal("패시브"));
    }

    @Override
    protected void init() {
        // 트리 크기 (칸 좌표 범위) → 화면에 맞게 칸 크기 정함
        int minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (Defs.Node n : Defs.NODES.values()) {
            minX = Math.min(minX, n.x()); maxX = Math.max(maxX, n.x());
            minY = Math.min(minY, n.y()); maxY = Math.max(maxY, n.y());
        }
        int spanX = maxX - minX + 1, spanY = maxY - minY + 1;
        int head = 30, foot = Ui.FOOT + 6;
        CELL = Math.max(16, Math.min(28, Math.min((width - 2 * Ui.MARGIN - 16) / spanX, (height - 2 * Ui.MARGIN - head - foot - 8) / spanY)));
        BOX = Math.max(14, CELL - 8);
        pw = Math.max(220, spanX * CELL + 16);
        ph = head + spanY * CELL + 8 + foot;
        px = Ui.cx(width, pw);
        py = Ui.cy(height, ph);
        cx = px + pw / 2 - (minX + maxX) * CELL / 2;
        cy = py + head + 4 + CELL / 2 - minY * CELL;
        addRenderableWidget(Ui.menu(px, py, ph));
        addRenderableWidget(Button.builder(Component.literal("스킬 (L)"), b -> minecraft.setScreen(new SkillScreen()))
                .bounds(px + 50, py + ph - Ui.FOOT, 60, Ui.BTN_H).build());
        addRenderableWidget(Ui.close(px, py, pw, ph, this::onClose));
    }

    private int nx(Defs.Node n) { return cx + n.x() * CELL; }
    private int ny(Defs.Node n) { return cy + n.y() * CELL; }

    private static int rank(Defs.Node n) {
        return ClientData.passives.getOrDefault(n.id(), 0);
    }

    private static boolean unlocked(Defs.Node n) {
        for (String r : n.requires()) {
            Defs.Node req = Defs.NODES.get(r);
            if (req != null && req.maxRank() > 0 && ClientData.passives.getOrDefault(r, 0) <= 0) return false;
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.frame(g, font, px, py, pw, ph, Ui.GOLD, "패시브 트리 (N)", "패시브 포인트: " + ClientData.passivePoints,
                ClientData.passivePoints > 0 ? 0xFF80FF80 : 0xFFA0A0A0);
        g.drawString(font, "칸 클릭 = 1포인트", px + 8, py + 18, 0xFF808080, false);
        Ui.footLine(g, px, py, pw, ph);

        // 연결선
        for (Defs.Node n : Defs.NODES.values()) {
            for (String r : n.requires()) {
                Defs.Node p = Defs.NODES.get(r);
                if (p == null) continue;
                int col = rank(n) > 0 ? 0xFFFFD24A : 0xFF404050;
                line(g, nx(p), ny(p), nx(n), ny(n), col);
            }
        }
        // 칸
        Defs.Node hover = null;
        for (Defs.Node n : Defs.NODES.values()) {
            int x = nx(n) - BOX / 2, y = ny(n) - BOX / 2;
            int r = rank(n);
            boolean can = unlocked(n) && r < n.maxRank();
            int border = n.special() ? 0xFFFF8040 : n.maxRank() == 0 ? 0xFFFFFFFF : r >= n.maxRank() ? 0xFFFFD24A : can ? 0xFF80C0FF : 0xFF404050;
            g.fill(x - 1, y - 1, x + BOX + 1, y + BOX + 1, border);
            g.fill(x, y, x + BOX, y + BOX, r > 0 || n.maxRank() == 0 ? 0xFF2A2A40 : 0xFF141420);
            String label = n.maxRank() == 0 ? "★" : font.width(r + "/" + n.maxRank()) <= BOX - 2 ? r + "/" + n.maxRank() : String.valueOf(r);
            g.drawCenteredString(font, label, x + BOX / 2, y + BOX / 2 - 3, r > 0 ? 0xFFFFFFFF : 0xFF909090);
            if (mx >= x && mx < x + BOX && my >= y && my < y + BOX) hover = n;
        }
        super.render(g, mx, my, pt);
        if (hover != null) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(hover.name() + (hover.special() ? " (큰 코어)" : "")));
            if (!hover.stat().isEmpty()) {
                String label = Defs.statLabel(hover.stat());
                int r = rank(hover);
                tip.add(Component.literal("칸당: " + String.format(label, Defs.fmt(hover.perRank()))));
                tip.add(Component.literal("현재: " + String.format(label, Defs.fmt(hover.perRank() * r)) + "  (" + r + "/" + hover.maxRank() + ")"));
                if (!unlocked(hover)) tip.add(Component.literal("앞 칸을 먼저 찍어야 해요"));
            } else {
                tip.add(Component.literal("여기서 4방향으로 뻗어 나가요"));
            }
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    /** 가로·세로 선만 (트리 모양이 십자라서) */
    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int col) {
        if (y1 == y2) g.fill(Math.min(x1, x2), y1 - 1, Math.max(x1, x2), y1 + 1, col);
        else if (x1 == x2) g.fill(x1 - 1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2), col);
        else { g.fill(Math.min(x1, x2), y1 - 1, Math.max(x1, x2), y1 + 1, col); g.fill(x2 - 1, Math.min(y1, y2), x2 + 1, Math.max(y1, y2), col); }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Defs.Node n : Defs.NODES.values()) {
            int x = nx(n) - BOX / 2, y = ny(n) - BOX / 2;
            if (mx >= x && mx < x + BOX && my >= y && my < y + BOX && n.maxRank() > 0) {
                Net.CH.sendToServer(new Net.Learn(1, n.id()));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.PASSIVE_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
