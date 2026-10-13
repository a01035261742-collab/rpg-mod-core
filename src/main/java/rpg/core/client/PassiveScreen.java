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
    private static final int CELL = 28, BOX = 20;
    private int cx, cy;

    public PassiveScreen() {
        super(Component.literal("패시브"));
    }

    @Override
    protected void init() {
        cx = width / 2;
        cy = height / 2 + 6;
        addRenderableWidget(Button.builder(Component.literal("스킬 창 (L)"), b -> minecraft.setScreen(new SkillScreen()))
                .bounds(width / 2 + 110, height / 2 - 150, 70, 14).build());
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
        g.drawCenteredString(font, "패시브 트리", width / 2, cy - 4 * CELL - 30, 0xFFFFD24A);
        String pp = "패시브 포인트: " + ClientData.passivePoints;
        g.drawCenteredString(font, pp, width / 2, cy - 4 * CELL - 18, ClientData.passivePoints > 0 ? 0xFF80FF80 : 0xFFA0A0A0);

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
            String label = n.maxRank() == 0 ? "★" : r + "/" + n.maxRank();
            g.drawCenteredString(font, label, x + BOX / 2, y + 7, r > 0 ? 0xFFFFFFFF : 0xFF909090);
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
