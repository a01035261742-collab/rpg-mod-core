package rpg.core.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** 약도 (M): 지역 탭 → 장소 클릭 = 사냥터·보스 정보 / 마을 지도 */
public class MapScreen extends ScaledScreen {
    private static final int W = 480, H = 300;
    private static final int MX0 = 8, MY0 = 40, MW = W - 16, MH = H - 70;   // 지도 칸
    private static int tab = -1;
    private int x, y;
    private MapData.Node open;   // 고른 장소 (null = 지역 지도)

    public MapScreen() { super(Component.literal("약도")); }

    @Override
    protected void init() {
        MapData.load();
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        if (tab < 0 || tab >= MapData.REGIONS.size()) {
            int r = MapData.regionOf(MapData.hereNode());
            tab = Math.max(0, r);
        }
        if (open == null) {
            int tx = x + 10;
            for (int i = 0; i < MapData.REGIONS.size(); i++) {
                final int k = i;
                String nm = MapData.REGIONS.get(i).name();
                int w = font.width(nm) + 14;
                addRenderableWidget(Ui.button(Component.literal(nm), b -> { tab = k; rebuildWidgets(); })
                        .bounds(tx, y + 20, w, 15).build()).active = tab != i;
                tx += w + 4;
            }
        } else {
            addRenderableWidget(Ui.button(Component.literal("← 약도"), b -> { open = null; rebuildWidgets(); })
                    .bounds(x + W - 120, y + H - Ui.FOOT, 62, Ui.BTN_H).build());
        }
        addRenderableWidget(Ui.close(x, y, W, H, this::onClose));
    }

    /** 노드 자리 (지도 칸 안, 배경 그림과 같은 식) */
    private Map<String, int[]> layout(MapData.Region r) {
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (var n : r.nodes()) { minX = Math.min(minX, n.x()); maxX = Math.max(maxX, n.x()); minY = Math.min(minY, n.y()); maxY = Math.max(maxY, n.y()); }
        Map<String, int[]> p = new HashMap<>();
        for (var n : r.nodes()) {
            int px = (int) Math.round(60 + (n.x() - minX) / Math.max(1e-6, maxX - minX) * (MW - 120));
            int py = (int) Math.round(26 + (n.y() - minY) / Math.max(1e-6, maxY - minY) * (MH - 52));
            p.put(n.id(), new int[]{x + MX0 + px, y + MY0 + py});
        }
        return p;
    }

    private static int[] size(MapData.Node n) {
        return switch (n.type()) { case "town" -> new int[]{82, 28}; case "exit" -> new int[]{60, 14}; default -> new int[]{76, 26}; };
    }

    private static int accent(String t) {
        return switch (t) { case "town" -> Ui.G4; case "hunt" -> 0xFF6FA060; case "dung" -> 0xFF8C8CA8; case "boss" -> 0xFFC84040; default -> Ui.G2; };
    }

    private void small(GuiGraphics g, String s, int cx, int yy, int col) {
        g.pose().pushPose();
        g.pose().translate(cx, yy, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, s, -font.width(s) / 2, 0, col, false);
        g.pose().popPose();
    }

    private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int fill, int edge) {
        g.fill(x0, y0, x1, y1, fill);
        g.fill(x0, y0, x1, y0 + 1, edge); g.fill(x0, y1 - 1, x1, y1, edge); g.fill(x0, y0, x0 + 1, y1, edge); g.fill(x1 - 1, y0, x1, y1, edge);
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int w, int col) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int px = x0 + (x1 - x0) * i / Math.max(1, n), py = y0 + (y1 - y0) * i / Math.max(1, n);
            g.fill(px - w / 2, py - w / 2, px - w / 2 + w, py - w / 2 + w, col);
        }
    }

    private void tex(GuiGraphics g, String name, int x0, int y0, int w, int h) {
        if (name == null || name.isEmpty()) return;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(new ResourceLocation("rpgcore", "textures/gui/map/" + name + ".png"), x0, y0, w, h, 0, 0, 1, 1, 1, 1);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.frame(g, font, x, y, W, H, 0, "약도", null, 0);
        if (MapData.REGIONS.isEmpty()) { g.drawCenteredString(font, "약도 데이터가 없어요", x + W / 2, y + H / 2, Ui.GRAY); super.render(g, mx, my, pt); return; }
        if (open == null) renderRegion(g, mx, my);
        else if (open.type().equals("town")) renderTown(g);
        else renderDetail(g);
        super.render(g, mx, my, pt);
    }

    private void renderRegion(GuiGraphics g, int mx, int my) {
        MapData.Region r = MapData.REGIONS.get(tab);
        int ax = x + MX0, ay = y + MY0;
        g.fill(ax, ay, ax + MW, ay + MH, Ui.IN1);
        tex(g, r.image(), ax, ay, MW, MH);
        box(g, ax - 1, ay - 1, ax + MW + 1, ay + MH + 1, 0, 0xFF000000);
        Map<String, int[]> p = layout(r);
        String here = MapData.hereNode();
        Set<String> tg = MapData.targets();
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        for (var n : r.nodes()) {
            int[] c = p.get(n.id()), s = size(n);
            int x0 = c[0] - s[0] / 2, y0 = c[1] - s[1] / 2, x1 = x0 + s[0], y1 = y0 + s[1];
            boolean hov = mx >= x0 && mx < x1 && my >= y0 && my < y1;
            int acc = accent(n.type());
            box(g, x0, y0, x1, y1, n.type().equals("town") ? 0xFF4A3010 : hov ? Ui.SEP : Ui.IN2, acc);
            g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, acc);
            if (n.type().equals("exit")) {
                g.drawCenteredString(font, n.title(), c[0], c[1] - 4, Ui.GRAY);
            } else {
                g.drawCenteredString(font, n.title(), c[0], y0 + 4, Ui.TXT);
                String sub = n.sub().isEmpty() ? "상점·창고·제작" : n.sub();
                small(g, sub, c[0], y0 + 15, n.type().equals("town") ? Ui.BLUE : Ui.VAL);
            }
            if (!n.points().isEmpty()) g.drawString(font, "!", x1 - 5, y0 - 9, Ui.VAL, true);
            if (hov) box(g, x0 - 2, y0 - 2, x1 + 2, y1 + 2, 0, Ui.G4);
            if (tg.contains(n.id()) && blink) box(g, x0 - 4, y0 - 4, x1 + 4, y1 + 4, 0, Ui.VAL);
            if (n.id().equals(here)) {
                int ax0 = x0 - 9;
                for (int i = 0; i < 6; i++) g.fill(ax0 + i, c[1] - 5 + i, ax0 + i + 1, c[1] + 6 - i, Ui.GREEN);
            }
        }
        g.drawString(font, "▶ 지금 위치   □ 퀘스트 목적지   ! 퀘스트 지점   장소를 누르면 자세히", x + 12, y + H - Ui.FOOT + 5, Ui.GRAY, false);
    }

    /** 노드 사이 길: 배경 그림에 이미 그려져 있음 */

    private void renderTown(GuiGraphics g) {
        MapData.Town t = MapData.TOWNS.get(open.town());
        g.drawString(font, open.title(), x + 14, y + 22, Ui.VAL, true);
        g.drawString(font, "마을 지도", x + 20 + font.width(open.title()), y + 23, Ui.GRAY, false);
        int ax = x + MX0, ay = y + MY0;
        g.fill(ax, ay, ax + MW, ay + MH, Ui.IN1);
        if (t == null || t.image().isEmpty()) {
            g.drawCenteredString(font, "마을 지도 준비 중이에요", x + W / 2, ay + MH / 2 - 4, Ui.GRAY);
            return;
        }
        int[] ts = TEX_SIZE.getOrDefault(t.image(), new int[]{990, 564});
        double r = Math.min(MW / (double) ts[0], MH / (double) ts[1]);
        int iw = (int) (ts[0] * r), ih = (int) (ts[1] * r), ox = ax + (MW - iw) / 2, oy = ay + (MH - ih) / 2;
        tex(g, t.image(), ox, oy, iw, ih);
        box(g, ax - 1, ay - 1, ax + MW + 1, ay + MH + 1, 0, 0xFF000000);
        Set<String> tg = MapData.targets();
        boolean blink = (System.currentTimeMillis() / 400) % 2 == 0;
        for (MapData.Bld b : t.buildings()) {
            int bx = ox + Math.round(b.u() * iw), by = oy + Math.round(b.v() * ih);
            int col = kindColor(b.kind());
            g.pose().pushPose();
            g.pose().translate(bx, by, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            int w = font.width(b.label()) + 8;
            box(g, -w / 2, -7, w - w / 2, 7, 0xF01E0C10, col);
            g.drawString(font, b.label(), -w / 2 + 4, -4, Ui.TXT, false);
            g.pose().popPose();
        }
        if (tg.contains(open.id()) && blink) box(g, ax - 2, ay - 2, ax + MW + 2, ay + MH + 2, 0, Ui.VAL);
        int lx = x + 12, ly = y + H - Ui.FOOT + 5;
        String[][] leg = {{"quest", "퀘스트 NPC"}, {"shop", "상점"}, {"craft", "제작"}, {"storage", "창고"}, {"job", "전직"}};
        for (String[] l : leg) {
            if (t.buildings().stream().noneMatch(b -> b.kind().equals(l[0]))) continue;
            box(g, lx, ly, lx + 7, ly + 7, Ui.IN2, kindColor(l[0]));
            g.drawString(font, l[1], lx + 10, ly, Ui.GRAY, false);
            lx += font.width(l[1]) + 22;
        }
    }

    private static final Map<String, int[]> TEX_SIZE = Map.of("town_t1", new int[]{990, 564}, "town_t2", new int[]{882, 604});

    private static int kindColor(String k) {
        return switch (k) { case "quest" -> Ui.VAL; case "shop" -> Ui.BLUE; case "craft" -> 0xFFFFB080; case "storage" -> Ui.PURPLE; case "job" -> Ui.GREEN; default -> Ui.TXT; };
    }

    private void renderDetail(GuiGraphics g) {
        g.drawString(font, open.title(), x + 14, y + 22, Ui.VAL, true);
        g.drawString(font, open.sub(), x + 20 + font.width(open.title()), y + 23, Ui.GRAY, false);
        List<MapData.MobInfo> ms = new ArrayList<>();
        for (String m : open.mobs()) { var mi = MapData.MOBS.get(m); if (mi != null) ms.add(mi); }
        int n = Math.max(1, ms.size()), cw = (W - 28 - 8 * (n - 1)) / n;
        for (int i = 0; i < ms.size(); i++) {
            var o = ms.get(i);
            int x0 = x + 14 + i * (cw + 8), y0 = y + 40, x1 = x0 + cw, y1 = y + H - 32;
            Ui.inset(g, x0, y0, x1, y1);
            g.drawString(font, o.name(), x0 + 8, y0 + 6, Ui.TXT, true);
            g.drawString(font, "Lv." + o.lv() + (o.raid() ? "  레이드 보스" : o.boss() ? "  필드 보스" : ""), x0 + 8, y0 + 18, Ui.VAL, false);
            int yy = y0 + 34;
            String ev = String.format("%,d", o.exp()), gv = String.format("%,d G", o.gold());
            g.drawString(font, "경험치", x0 + 8, yy, Ui.GRAY, false); g.drawString(font, ev, x1 - 8 - font.width(ev), yy, Ui.TXT, false); yy += 12;
            g.drawString(font, "골드", x0 + 8, yy, Ui.GRAY, false); g.drawString(font, gv, x1 - 8 - font.width(gv), yy, Ui.VAL, false); yy += 12;
            Ui.hsep(g, x0 + 6, x1 - 6, yy + 1); yy += 6;
            if (o.boss()) {
                g.drawString(font, "주요 보상", x0 + 8, yy, Ui.GREEN, true); yy += 12;
                for (String r : o.rewards()) { g.drawString(font, "· " + Ui.cut(font, r, cw - 24), x0 + 12, yy, Ui.TXT, false); yy += 11; }
                if (o.respawn() > 0) g.drawString(font, "재등장 " + o.respawn() / 60 + "분", x0 + 8, y1 - 14, Ui.GRAY, false);
            } else {
                g.drawString(font, "공격", x0 + 8, yy, 0xFFFFB080, true); yy += 12;
                List<String> sk = o.skills().isEmpty() ? List.of("근접 공격") : o.skills();
                for (String s : sk) { g.drawString(font, "· " + Ui.cut(font, s, cw - 24), x0 + 12, yy, Ui.TXT, false); yy += 11; }
                yy += 3;
                g.drawString(font, "드롭", x0 + 8, yy, Ui.GREEN, true); yy += 12;
                for (String d : o.drops()) { g.drawString(font, "· " + Ui.cut(font, d, cw - 24), x0 + 12, yy, Ui.TXT, false); yy += 11; }
            }
        }
        if (!open.npcs().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String id : open.npcs()) names.add(MapData.NPC_NAMES.getOrDefault(id, id));
            g.drawString(font, "NPC: " + String.join(", ", names), x + 14, y + H - Ui.FOOT + 5, Ui.VAL, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (open != null || MapData.REGIONS.isEmpty()) return false;
        MapData.Region r = MapData.REGIONS.get(tab);
        Map<String, int[]> p = layout(r);
        for (var n : r.nodes()) {
            int[] c = p.get(n.id()), s = size(n);
            int x0 = c[0] - s[0] / 2, y0 = c[1] - s[1] / 2;
            if (mx >= x0 && mx < x0 + s[0] && my >= y0 && my < y0 + s[1]) {
                if (n.type().equals("exit")) { if (n.exitTo() > 0) { tab = n.exitTo() - 1; rebuildWidgets(); } }
                else { open = n; rebuildWidgets(); }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.MAP_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
