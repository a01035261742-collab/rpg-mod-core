package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;
import rpg.core.skill.Defs;

/**
 * 패시브 트리 (N): PoE식 그물. 가운데 시작점에서 퍼짐.
 * 드래그 = 이동, 휠 = 확대/축소, 클릭 = 1포인트.
 * 작은 칸(s) · 중간(n) · 보석(g, 하늘색) · 핵심(k, 보라). 다른 2차 갈래 칸은 어둡게 잠김.
 */
public class PassiveScreen extends Screen {
    private int CELL = 24;
    private int px, py, pw, ph, ax, ay, aw, ah;   // 패널, 트리 영역
    private double panX, panY;                    // 트리 이동 (픽셀)
    private boolean dragging;
    private double dragDist;
    private final List<Defs.Node> nodes = new ArrayList<>();

    public PassiveScreen() {
        super(Component.literal("패시브"));
    }

    @Override
    protected void init() {
        nodes.clear();
        int tree = Defs.treeOf(ClientData.job);
        for (Defs.Node n : Defs.NODES.values()) if (n.job() == tree) nodes.add(n);
        int minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (Defs.Node n : nodes) {
            minX = Math.min(minX, n.x()); maxX = Math.max(maxX, n.x());
            minY = Math.min(minY, n.y()); maxY = Math.max(maxY, n.y());
        }
        int spanX = maxX - minX + 1, spanY = maxY - minY + 1;
        int head = 30, foot = Ui.FOOT + 6;
        pw = Math.min(width - 2 * Ui.MARGIN, 520);
        ph = Math.min(height - 2 * Ui.MARGIN, 380);
        px = Ui.cx(width, pw);
        py = Ui.cy(height, ph);
        ax = px + 6; ay = py + head; aw = pw - 12; ah = ph - head - foot;
        CELL = Math.max(14, Math.min(30, Math.min((aw - 16) / spanX, (ah - 16) / spanY)));
        panX = -(minX + maxX) * CELL / 2.0;
        panY = -(minY + maxY) * CELL / 2.0;
        addRenderableWidget(Ui.menu(px, py, ph));
        addRenderableWidget(Ui.button(Component.literal("스킬 (L)"), b -> minecraft.setScreen(new SkillScreen()))
                .bounds(px + 50, py + ph - Ui.FOOT, 60, Ui.BTN_H).build());
        addRenderableWidget(Ui.close(px, py, pw, ph, this::onClose));
    }

    private int nx(Defs.Node n) { return (int) Math.round(ax + aw / 2.0 + panX + n.x() * CELL); }
    private int ny(Defs.Node n) { return (int) Math.round(ay + ah / 2.0 + panY + n.y() * CELL); }

    private static int rank(Defs.Node n) { return ClientData.passives.getOrDefault(n.id(), 0); }

    private static boolean taken(Defs.Node n) { return n.maxRank() == 0 || rank(n) > 0; }

    /** 내 갈래에서 쓸 수 있는 칸 (다른 2차 갈래 칸은 false) */
    private static boolean usable(Defs.Node n) { return n.branch() == 0 || n.branch() == ClientData.branch; }

    private static boolean canLearn(Defs.Node n) {
        return usable(n) && n.maxRank() > 0 && rank(n) < n.maxRank()
                && Defs.unlocked(n, k -> ClientData.passives.getOrDefault(k, 0));
    }

    /** 칸 크기 (반지름) */
    private int size(Defs.Node n) {
        int base = Math.max(4, CELL / 3);
        return switch (n.kind()) {
            case "start" -> base + 3;
            case "k" -> base + 3;
            case "g" -> base + 1;
            case "n" -> base;
            default -> Math.max(3, base - 2);
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.frame(g, font, px, py, pw, ph, Ui.GOLD, "패시브 트리 (N)", "패시브 포인트: " + ClientData.passivePoints,
                ClientData.passivePoints > 0 ? 0xFF80FF80 : 0xFFA0A0A0);
        g.drawString(font, "드래그 이동 · 휠 확대 · 클릭 1포인트", px + 8, py + 18, 0xFF808080, false);
        Ui.footLine(g, px, py, pw, ph);
        g.fill(ax, ay, ax + aw, ay + ah, 0xFF0C0C14);

        if (ClientData.job <= 0 || nodes.isEmpty()) {
            g.drawCenteredString(font, "1차 전직 후 열려요", ax + aw / 2, ay + ah / 2 - 4, 0xFFA0A0A0);
            super.render(g, mx, my, pt);
            return;
        }

        g.enableScissor(ax, ay, ax + aw, ay + ah);
        // 연결선
        for (Defs.Node n : nodes) {
            for (String r : n.requires()) {
                Defs.Node p = Defs.NODES.get(r);
                if (p == null || !nodes.contains(p)) continue;
                int col = taken(n) && taken(p) ? 0xFFFFD24A
                        : (taken(p) && canLearn(n)) || (taken(n) && canLearn(p)) ? 0xFF8090B0 : 0xFF2C2C3C;
                line(g, nx(p), ny(p), nx(n), ny(n), col);
            }
        }
        // 칸
        Defs.Node hover = null;
        boolean inArea = mx >= ax && mx < ax + aw && my >= ay && my < ay + ah;
        for (Defs.Node n : nodes) {
            int x = nx(n), y = ny(n), s = size(n);
            if (x < ax - s || x > ax + aw + s || y < ay - s || y > ay + ah + s) continue;
            int r = rank(n);
            boolean use = usable(n), full = n.maxRank() > 0 && r >= n.maxRank(), can = canLearn(n);
            int ring = switch (n.kind()) {
                case "g" -> 0xFF40D0E0;
                case "k" -> 0xFFB060FF;
                case "start" -> 0xFFFFFFFF;
                default -> 0xFFB0B0C0;
            };
            int border = !use ? 0xFF303038 : full || n.maxRank() == 0 ? 0xFFFFD24A : r > 0 ? 0xFFFFE890 : can ? ring : dim(ring);
            int fill = !use ? 0xFF101014 : r > 0 || n.maxRank() == 0 ? 0xFF3A3420 : can ? 0xFF1C1C2C : 0xFF121218;
            if (n.kind().equals("k") || n.kind().equals("start")) diamond(g, x, y, s + 1, border, fill);
            else { g.fill(x - s - 1, y - s - 1, x + s + 1, y + s + 1, border); g.fill(x - s, y - s, x + s, y + s, fill); }
            if (n.maxRank() > 1 && CELL >= 18) {
                String t = r + "/" + n.maxRank();
                g.pose().pushPose();
                g.pose().translate(x, y + s + 2, 0);
                g.pose().scale(0.5f, 0.5f, 1);
                g.drawCenteredString(font, t, 0, 0, r > 0 ? 0xFFFFE890 : 0xFF808080);
                g.pose().popPose();
            }
            if (inArea && Math.abs(mx - x) <= s + 1 && Math.abs(my - y) <= s + 1) hover = n;
        }
        g.disableScissor();
        super.render(g, mx, my, pt);
        if (hover != null) g.renderComponentTooltip(font, tip(hover), mx, my);
    }

    private List<Component> tip(Defs.Node n) {
        List<Component> tip = new ArrayList<>();
        String kind = switch (n.kind()) {
            case "g" -> " §b(보석)";
            case "k" -> " §d(핵심)";
            case "n" -> " §7(중간)";
            case "start" -> "";
            default -> "";
        };
        tip.add(Component.literal("§e" + n.name() + kind));
        if (n.kind().equals("start")) { tip.add(Component.literal("§7여기서 사방으로 뻗어 나가요")); return tip; }
        int r = rank(n);
        if (!n.desc().isEmpty()) tip.add(Component.literal("§f" + n.desc()));
        else {
            if (!n.stat().isEmpty()) tip.add(Component.literal("§f" + String.format(Defs.statLabel(n.stat()), Defs.fmt(n.perRank())) + (n.maxRank() > 1 ? " §7(칸당)" : "")));
            if (!n.stat2().isEmpty()) tip.add(Component.literal("§f" + String.format(Defs.statLabel(n.stat2()), Defs.fmt(n.perRank2())) + (n.maxRank() > 1 ? " §7(칸당)" : "")));
        }
        if (n.maxRank() > 1 && r > 0) {
            StringBuilder sb = new StringBuilder("§a현재: ");
            if (!n.stat().isEmpty()) sb.append(String.format(Defs.statLabel(n.stat()), Defs.fmt(n.perRank() * r)));
            if (!n.stat2().isEmpty()) sb.append(", ").append(String.format(Defs.statLabel(n.stat2()), Defs.fmt(n.perRank2() * r)));
            tip.add(Component.literal(sb.toString()));
        }
        tip.add(Component.literal("§7" + r + " / " + n.maxRank()));
        if (!usable(n)) tip.add(Component.literal("§c" + (n.branch() == 1 ? "가디언" : "버서커") + " 전용"));
        else if (!Defs.unlocked(n, k -> ClientData.passives.getOrDefault(k, 0))) tip.add(Component.literal("§c이어진 칸을 먼저 찍어야 해요"));
        return tip;
    }

    private static int dim(int c) {
        int r = (c >> 16 & 255) / 2, gg = (c >> 8 & 255) / 2, b = (c & 255) / 2;
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    private static void diamond(GuiGraphics g, int x, int y, int s, int border, int fill) {
        for (int i = -s; i <= s; i++) {
            int w = s - Math.abs(i);
            g.fill(x - w - 1, y + i, x + w + 1, y + i + 1, border);
            if (w > 0) g.fill(x - w + 1, y + i, x + w - 1, y + i + 1, fill);
        }
    }

    /** 대각선 포함 선 (2픽셀 점 찍기) */
    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int col) {
        if (y1 == y2) { g.fill(Math.min(x1, x2), y1 - 1, Math.max(x1, x2), y1 + 1, col); return; }
        if (x1 == x2) { g.fill(x1 - 1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2), col); return; }
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            g.fill(x - 1, y - 1, x + 1, y + 1, col);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= ax && mx < ax + aw && my >= ay && my < ay + ah) {
            dragging = true;
            dragDist = 0;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            panX += dx; panY += dy;
            dragDist += Math.abs(dx) + Math.abs(dy);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && button == 0) {
            dragging = false;
            if (dragDist < 4) {   // 거의 안 움직였으면 클릭
                for (Defs.Node n : nodes) {
                    int s = size(n);
                    if (Math.abs(mx - nx(n)) <= s + 1 && Math.abs(my - ny(n)) <= s + 1) {
                        if (canLearn(n) && ClientData.passivePoints > 0) Net.CH.sendToServer(new Net.Learn(1, n.id()));
                        break;
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int old = CELL;
        CELL = Math.max(12, Math.min(44, CELL + (delta > 0 ? 2 : -2)));
        if (old != CELL) { panX = panX * CELL / old; panY = panY * CELL / old; }
        return true;
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
