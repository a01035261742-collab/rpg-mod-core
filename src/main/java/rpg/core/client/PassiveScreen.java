package rpg.core.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import rpg.core.net.Net;
import rpg.core.skill.Defs;

/**
 * 패시브 트리 (N): 동심원 + 별가루 배경, 원 + 금색 랭크 고리, 보석 육각(하늘), 핵심 이중 원(보라), 시작 마름모.
 * 찍은 길 굵은 금 · 찍을 수 있는 길 회청 · 나머지 어둠. 다른 2차 갈래 칸은 자물쇠.
 * 툴팁: 아이콘·이름·종류·랭크 / 지금 → 찍으면 / 전투력 변화 (서버 계산). 오른쪽 아래 [초기화] [검색] [가운데로].
 * 드래그 = 이동, 휠 = 확대, 클릭 = 1포인트.
 */
public class PassiveScreen extends Screen {
    private static final int GOLD = 0xFFFFD24A, CYAN = 0xFF40D0E0, PURP = 0xFFB060FF, HEAD = 34;
    private int CELL = 30;
    private int px, py, pw, ph, ax, ay, aw, ah;
    private double panX, panY, homeX, homeY;
    private boolean dragging;
    private double dragDist;
    private final List<Defs.Node> nodes = new ArrayList<>();
    private EditBox search;
    private boolean searching;
    private long[] stars;

    // 전투력 미리보기 (칸 id → 값, 받은 시각)
    private static final Map<String, long[]> POWER = new HashMap<>();
    private static final Map<String, Long> ASKED = new HashMap<>();

    public static void power(String node, long delta) {
        POWER.put(node, new long[]{delta, System.currentTimeMillis()});
        ASKED.remove(node);
    }

    public PassiveScreen() {
        super(Component.literal("패시브"));
    }

    @Override
    protected void init() {
        nodes.clear();
        int tree = Defs.treeOf(ClientData.job);
        for (Defs.Node n : Defs.NODES.values()) if (n.job() == tree) nodes.add(n);
        pw = width - 2 * Ui.MARGIN;
        ph = height - 2 * Ui.MARGIN;
        px = Ui.MARGIN; py = Ui.MARGIN;
        ax = px + 2; ay = py + HEAD; aw = pw - 4; ah = ph - HEAD - 2;
        int minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (Defs.Node n : nodes) { minX = Math.min(minX, n.x()); maxX = Math.max(maxX, n.x()); minY = Math.min(minY, n.y()); maxY = Math.max(maxY, n.y()); }
        int spanX = maxX - minX + 2, spanY = maxY - minY + 2;
        CELL = Math.max(16, Math.min(40, Math.min(aw / spanX, (ah - 30) / spanY)));
        homeX = -(minX + maxX) * CELL / 2.0;
        homeY = -(minY + maxY) * CELL / 2.0;
        panX = homeX; panY = homeY;
        // 별가루 (고정 위치)
        java.util.Random r = new java.util.Random(7);
        stars = new long[160];
        for (int i = 0; i < stars.length; i++) stars[i] = ((long) r.nextInt(Math.max(1, aw)) << 32) | (r.nextInt(Math.max(1, ah)) & 0xFFFFFFFFL) | ((long) r.nextInt(3) << 60);
        // 버튼: 오른쪽 아래
        int bw = 70, by = py + ph - 24, bx = px + pw - 3 * (bw + 6) - 4;
        addRenderableWidget(Ui.button(Component.literal("초기화"), b -> reset()).bounds(bx, by, bw, 18).build());
        addRenderableWidget(Ui.button(Component.literal("검색"), b -> { searching = !searching; search.visible = searching; if (searching) setFocused(search); }).bounds(bx + bw + 6, by, bw, 18).build());
        addRenderableWidget(Ui.button(Component.literal("가운데로"), b -> { panX = homeX; panY = homeY; }).bounds(bx + 2 * (bw + 6), by, bw, 18).build());
        search = new EditBox(font, bx - 128, by + 1, 120, 16, Component.literal("검색"));
        search.setHint(Component.literal("칸 이름·효과"));
        search.visible = searching;
        addRenderableWidget(search);
        // 메뉴 · 스킬 · 닫기: 아래 가운데 (범례 오른쪽)
        int mxb = px + 166;
        addRenderableWidget(Ui.button(Component.literal("메뉴"), b -> minecraft.setScreen(new MenuScreen())).bounds(mxb, by, 44, 18).build());
        addRenderableWidget(Ui.button(Component.literal("스킬 (L)"), b -> minecraft.setScreen(new SkillScreen())).bounds(mxb + 48, by, 56, 18).build());
        addRenderableWidget(Ui.button(Component.literal("닫기"), b -> onClose()).bounds(mxb + 108, by, 44, 18).build());
    }

    private void reset() {
        int spent = 0;
        for (int v : ClientData.passives.values()) spent += v;
        if (spent <= 0) return;
        Net.CH.sendToServer(new Net.Learn(2, ""));
    }

    private int nx(Defs.Node n) { return (int) Math.round(ax + aw / 2.0 + panX + n.x() * CELL); }
    private int ny(Defs.Node n) { return (int) Math.round(ay + ah / 2.0 + panY + n.y() * CELL); }

    private static int rank(Defs.Node n) { return ClientData.passives.getOrDefault(n.id(), 0); }
    private static boolean taken(Defs.Node n) { return n.maxRank() == 0 || rank(n) > 0; }
    private static boolean usable(Defs.Node n) { return n.branch() == 0 || n.branch() == ClientData.branch; }
    private static boolean canLearn(Defs.Node n) {
        return usable(n) && n.maxRank() > 0 && rank(n) < n.maxRank() && Defs.unlocked(n, k -> ClientData.passives.getOrDefault(k, 0));
    }

    private int rad(Defs.Node n) {
        int b = Math.max(6, CELL * 2 / 5);
        return switch (n.kind()) {
            case "start", "k" -> b + 3;
            case "g", "n" -> b + 1;
            default -> b - 1;
        };
    }

    // ───────── 아이콘: 스탯 → 마크 아이템 ─────────
    static Item iconOf(Defs.Node n) {
        String s = n.stat();
        return switch (s) {
            case "dmgPct", "atkPct", "basicPct", "str", "lowHpDmg", "executePct", "slayer" -> Items.IRON_SWORD;
            case "critChance", "critDmg", "critPierce", "critBleed" -> Items.ARROW;
            case "bossPct", "mobPct", "firstStrike" -> Items.SKELETON_SKULL;
            case "hpPct", "vit", "vitHpPct", "hpRegenPct", "healPct", "killHeal", "rageHeal", "lifesteal" -> Items.APPLE;
            case "giantHp", "crisisShield" -> Items.GOLDEN_APPLE;
            case "defPct", "kbResist", "thorns", "guardInstinct", "oath" -> Items.SHIELD;
            case "speedPct", "cdPct", "dodgeCd", "stamina", "moveDmg", "thrillSpeed" -> Items.FEATHER;
            case "mpPct", "mpRegenPct", "killMp", "mpCost", "meditation", "buffDur" -> Items.LAPIS_LAZULI;
            case "overloadMp" -> Items.AMETHYST_SHARD;
            case "dex" -> Items.BOW;
            case "int" -> Items.BOOK;
            case "luk" -> Items.EMERALD;
            case "dotPct", "bleedPct", "deepWound", "doubleAgony", "bloodFeast" -> Items.REDSTONE;
            case "poisonPct", "poisonArrow", "chaosField" -> Items.SPIDER_EYE;
            case "burnOnCrit", "burnDmg", "burnSpread", "frenzy", "bloodHeat" -> Items.BLAZE_POWDER;
            case "ignoreDef", "shatter" -> Items.IRON_PICKAXE;
            case "farDmg" -> Items.SPYGLASS;
            case "trapMaster", "weakHunt" -> Items.TRIPWIRE_HOOK;
            case "domino" -> Items.TNT;
            case "quickShot" -> Items.CROSSBOW;
            case "rollDmg", "rollCrit" -> Items.RABBIT_FOOT;
            case "windVeil", "escape" -> Items.PHANTOM_MEMBRANE;
            case "huntSpeed", "chaseSpeed" -> Items.LEATHER_BOOTS;
            case "lone", "loneAssassin" -> Items.ENDER_EYE;
            case "stillAtk" -> Items.CLOCK;
            case "tpDmg" -> Items.ENDER_PEARL;
            case "intimidate", "tauntDmg", "warcry" -> Items.GOAT_HORN;
            case "holyAura", "healDmg", "healBuff" -> Items.GLOWSTONE_DUST;
            default -> n.kind().equals("start") ? Items.NETHER_STAR : n.kind().equals("k") ? Items.NETHER_STAR : Items.AMETHYST_SHARD;
        };
    }

    // ───────── 그리기 도구 ─────────
    private static void disc(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy <= r; dy++) {
            int w = (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, col);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int th, int col) { arc(g, cx, cy, r, th, 1.0, col); }

    /** 12시부터 시계방향으로 frac 만큼 원호 */
    private static void arc(GuiGraphics g, int cx, int cy, int r, int th, double frac, int col) {
        int steps = Math.max(24, r * 8);
        int n = (int) Math.round(steps * frac);
        for (int i = 0; i <= n; i++) {
            double a = -Math.PI / 2 + Math.PI * 2 * i / steps;
            int x = cx + (int) Math.round(Math.cos(a) * (r - th / 2.0)), y = cy + (int) Math.round(Math.sin(a) * (r - th / 2.0));
            g.fill(x - th / 2, y - th / 2, x - th / 2 + th, y - th / 2 + th, col);
        }
    }

    private static void hexagon(GuiGraphics g, int cx, int cy, int r, int fill, int border) {
        for (int dy = -r; dy <= r; dy++) {
            double h = r * Math.sqrt(3) / 2;
            if (Math.abs(dy) > h) continue;
            int w = (int) Math.round(r - Math.abs(dy) / Math.sqrt(3));
            g.fill(cx - w, cy + dy, cx + w + 1, cy + dy + 1, border);
            if (w > 2 && Math.abs(dy) < h - 2) g.fill(cx - w + 2, cy + dy, cx + w - 1, cy + dy + 1, fill);
        }
    }

    private static void diamond(GuiGraphics g, int x, int y, int s, int border, int fill) {
        for (int i = -s; i <= s; i++) {
            int w = s - Math.abs(i);
            g.fill(x - w - 1, y + i, x + w + 2, y + i + 1, border);
            if (w > 1) g.fill(x - w + 2, y + i, x + w - 1, y + i + 1, fill);
        }
    }

    private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int th, int col) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps, y = y1 + (y2 - y1) * i / steps;
            g.fill(x - th / 2, y - th / 2, x - th / 2 + th, y - th / 2 + th, col);
        }
    }

    private static void lock(GuiGraphics g, int x, int y, int col) {
        g.fill(x - 4, y - 1, x + 5, y + 6, col);
        g.fill(x - 3, y - 5, x - 1, y - 1, col); g.fill(x + 2, y - 5, x + 4, y - 1, col); g.fill(x - 3, y - 6, x + 4, y - 4, col);
    }

    // ───────── 화면 ─────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px, py, px + pw, py + ph, 0xFF0C0E16);
        // 위 바
        g.fill(px, py, px + pw, py + HEAD, 0xFF12141E);
        g.fill(px, py + HEAD - 1, px + pw, py + HEAD, GOLD);
        g.pose().pushPose(); g.pose().translate(px + 10, py + 9, 0); g.pose().scale(1.4f, 1.4f, 1);
        g.drawString(font, "패시브 트리", 0, 0, GOLD, true); g.pose().popPose();
        String jn = jobName();
        g.drawString(font, jn, px + 100, py + 13, 0xFFD2D2E1, false);
        String pts = "남은 포인트  " + ClientData.passivePoints;
        int scrolls = scrolls();
        String rs = "무료 초기화 " + ClientData.freeTreeReset + "회  ·  초기화 주문서 " + scrolls + "개";
        int rx = px + pw - font.width(rs) - 22, prx = rx - font.width(pts) - 30;
        box(g, prx, py + 7, font.width(pts) + 16, 20, 0xFF18281C, 0xFF6EC86E);
        g.drawString(font, pts, prx + 8, py + 13, 0xFF96EB96, false);
        box(g, rx, py + 7, font.width(rs) + 16, 20, 0xFF1A1C28, 0xFF464A64);
        g.drawString(font, rs, rx + 8, py + 13, 0xFFD2D2E1, false);

        if (ClientData.job <= 0 || nodes.isEmpty()) {
            g.drawCenteredString(font, "1차 전직 후 열려요", ax + aw / 2, ay + ah / 2 - 4, 0xFFA0A0A0);
            super.render(g, mx, my, pt);
            return;
        }

        g.enableScissor(ax, ay, ax + aw, ay + ah);
        // 배경: 동심원 + 별가루
        int cx = (int) (ax + aw / 2.0 + panX), cy = (int) (ay + ah / 2.0 + panY);
        for (int r = 1; r <= 16; r++) {
            int rr = r * CELL * 3 / 2;
            ellipse(g, cx, cy, rr * 5 / 4, rr, 0xFF1C2030);
        }
        for (long s : stars) {
            int sx = ax + (int) ((s >> 32) & 0x0FFFFFFF), sy = ay + (int) (s & 0xFFFFFFFFL);
            int c = switch ((int) (s >>> 60)) { case 0 -> 0xFF464864; case 1 -> 0xFF64688C; default -> 0xFF9696BE; };
            g.fill(sx, sy, sx + 1, sy + 1, c);
        }
        // 구역 이름 (옅게)
        String[] lab = regionLabels();
        int[][] lp = {{0, -7}, {0, 7}, {-8, -7}, {8, -7}, {-8, 7}, {8, 7}, {-4, 1}, {4, 1}};
        for (int i = 0; i < lab.length && i < lp.length; i++) {
            int lx = (int) (ax + aw / 2.0 + panX + lp[i][0] * CELL), ly = (int) (ay + ah / 2.0 + panY + lp[i][1] * CELL);
            int col = 0xFF6E7088;
            if (i >= 6) {
                boolean left = i == 6;
                boolean mine = nodes.stream().anyMatch(n -> n.branch() != 0 && usable(n) && (left ? n.x() < 0 : n.x() > 0));
                col = mine ? 0xFFE07070 : 0xFF5A5A70;
            }
            g.drawCenteredString(font, lab[i], lx, ly - 4, col);
        }
        // 선
        for (Defs.Node n : nodes) {
            for (String r : n.requires()) {
                Defs.Node p = Defs.NODES.get(r);
                if (p == null || !nodes.contains(p)) continue;
                int x1 = nx(p), y1 = ny(p), x2 = nx(n), y2 = ny(n);
                if (taken(n) && taken(p) && usable(n)) { line(g, x1, y1, x2, y2, 5, 0xFF7A5A18); line(g, x1, y1, x2, y2, 3, GOLD); }
                else if ((taken(p) && canLearn(n)) || (taken(n) && canLearn(p))) line(g, x1, y1, x2, y2, 2, 0xFF6973A0);
                else line(g, x1, y1, x2, y2, 2, 0xFF262A3A);
            }
        }
        // 칸
        Defs.Node hover = null;
        String q = searching ? search.getValue().trim() : "";
        boolean inArea = mx >= ax && mx < ax + aw && my >= ay && my < ay + ah;
        for (Defs.Node n : nodes) {
            int x = nx(n), y = ny(n), r = rad(n);
            if (x < ax - r - 4 || x > ax + aw + r + 4 || y < ay - r - 4 || y > ay + ah + r + 4) continue;
            drawNode(g, n, x, y, r);
            if (!q.isEmpty() && matches(n, q) && (System.currentTimeMillis() / 300) % 2 == 0) ring(g, x, y, r + 5, 2, 0xFFFFFFFF);
            if (inArea && (mx - x) * (mx - x) + (my - y) * (my - y) <= (r + 2) * (r + 2)) hover = n;
        }
        if (hover != null) ring(g, nx(hover), ny(hover), rad(hover) + 4, 2, 0xFFFFFFFF);
        g.disableScissor();
        legend(g);
        super.render(g, mx, my, pt);
        if (hover != null) tooltip(g, hover, mx, my);
    }

    private void drawNode(GuiGraphics g, Defs.Node n, int x, int y, int r) {
        boolean use = usable(n), t = taken(n) && use, can = canLearn(n);
        int rk = rank(n);
        switch (n.kind()) {
            case "start" -> {
                disc(g, x, y, r + 6, 0xFF3C3012);
                diamond(g, x, y, r, 0xFFFFFFFF, 0xFF322C1C);
            }
            case "g" -> {
                if (t) disc(g, x, y, r + 5, 0xFF10323C);
                hexagon(g, x, y, r, 0xFF10202A, t || can ? CYAN : 0xFF1E4652);
            }
            case "k" -> {
                if (t) disc(g, x, y, r + 7, 0xFF2E1846);
                int c = t || can ? PURP : !use ? 0xFF323240 : 0xFF402A60;
                ring(g, x, y, r + 4, 2, c);
                disc(g, x, y, r, c);
                disc(g, x, y, r - 3, !use ? 0xFF0C0C10 : 0xFF1C122C);
            }
            default -> {
                if (t && rk >= n.maxRank()) disc(g, x, y, r + 4, 0xFF3A2E10);
                ring(g, x, y, r, 3, !use ? 0xFF22222A : 0xFF343850);
                if (rk > 0 && use) arc(g, x, y, r, 3, (double) rk / n.maxRank(), GOLD);
                disc(g, x, y, r - 3, t ? 0xFF221E16 : can ? 0xFF181A26 : 0xFF0D0D13);
            }
        }
        if (!use) { lock(g, x, y, 0xFF64646E); return; }
        // 아이콘
        float sc = Math.max(0.5f, Math.min(1.1f, (r * 1.3f) / 16f));
        g.pose().pushPose();
        g.pose().translate(x - 8 * sc, y - 8 * sc, 0);
        g.pose().scale(sc, sc, 1);
        g.renderItem(new ItemStack(iconOf(n)), 0, 0);
        g.pose().popPose();
        if (!t && !can) {   // 못 찍는 칸은 아이콘 어둡게
            g.pose().pushPose(); g.pose().translate(0, 0, 200);
            disc(g, x, y, r - 3, 0xB00D0D13);
            g.pose().popPose();
        }
        if (n.maxRank() > 1 && CELL >= 20) {
            g.pose().pushPose();
            g.pose().translate(x + r - 1, y + r - 4, 210);
            g.pose().scale(0.6f, 0.6f, 1);
            g.drawString(font, rk + "/" + n.maxRank(), 0, 0, rk > 0 ? GOLD : 0xFF6E6E82, true);
            g.pose().popPose();
        }
    }

    private static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int col) {
        int steps = Math.max(40, (rx + ry) / 2);
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = cx + (int) (Math.cos(a) * rx), y = cy + (int) (Math.sin(a) * ry);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void legend(GuiGraphics g) {
        int lx = px + 8, ly = py + ph - 82, w = 150;
        box(g, lx, ly, w, 74, 0xF012141E, 0xFF2C3042);
        int cx = lx + 12, cy = ly + 12;
        ring(g, cx, cy, 6, 2, 0xFF343850); arc(g, cx, cy, 6, 2, 0.6, GOLD);
        g.drawString(font, "스탯 칸 (고리 = 랭크)", lx + 24, cy - 4, 0xFFD7D7E4, false);
        cy += 16; hexagon(g, cx, cy, 6, 0xFF10202A, CYAN);
        g.drawString(font, "보석 · 특수 효과", lx + 24, cy - 4, CYAN, false);
        cy += 16; ring(g, cx, cy, 7, 1, PURP); disc(g, cx, cy, 4, PURP);
        g.drawString(font, "핵심 · 강한 특수 효과", lx + 24, cy - 4, PURP, false);
        cy += 16; lock(g, cx, cy, 0xFF64646E);
        g.drawString(font, "다른 2차 갈래 (잠김)", lx + 24, cy - 4, 0xFF9696A5, false);
    }

    // ───────── 툴팁 ─────────
    private void tooltip(GuiGraphics g, Defs.Node n, int mx, int my) {
        boolean mage = ClientData.job == 3;
        List<String[]> rows = new ArrayList<>();   // [왼쪽, 오른쪽, 색]
        int rk = rank(n);
        String kind = switch (n.kind()) { case "g" -> "보석"; case "k" -> "핵심"; case "n" -> "중간 칸"; case "start" -> "시작"; default -> "작은 칸"; };
        int w = 230, x, y;
        List<String> now = new ArrayList<>(), next = new ArrayList<>();
        if (!n.desc().isEmpty()) next.add(n.desc());
        else {
            if (!n.stat().isEmpty()) { now.add(fmt(n.stat(), n.perRank() * rk)); next.add(fmt(n.stat(), n.perRank() * Math.min(n.maxRank(), rk + 1))); }
            if (!n.stat2().isEmpty()) { now.add(fmt(n.stat2(), n.perRank2() * rk)); next.add(fmt(n.stat2(), n.perRank2() * Math.min(n.maxRank(), rk + 1))); }
        }
        if (mage) { now.replaceAll(s -> s.replace("공격력", "마력")); next.replaceAll(s -> s.replace("공격력", "마력")); }
        boolean full = n.maxRank() > 0 && rk >= n.maxRank(), can = canLearn(n);
        // 전투력
        long[] pw0 = null;
        if (can) {
            pw0 = POWER.get(n.id());
            long t = System.currentTimeMillis();
            if ((pw0 == null || t - pw0[1] > 4000) && t - ASKED.getOrDefault(n.id(), 0L) > 1000) {
                ASKED.put(n.id(), t);
                Net.CH.sendToServer(new Net.Learn(3, n.id()));
            }
        }
        // 높이 계산
        int lines = 0;
        List<String> wrapNext = new ArrayList<>();
        for (String s : next) wrapNext.addAll(wrap(s, w - 70));
        int h = 46 + (n.kind().equals("start") ? 14 : (rk > 0 && n.desc().isEmpty() ? now.size() * 11 + 2 : 0) + wrapNext.size() * 11 + 10) + (can ? 22 : 0) + 18;
        x = mx + 14; y = my - 10;
        if (x + w > width - 4) x = mx - w - 14;
        if (y + h > height - 4) y = height - 4 - h;
        g.pose().pushPose(); g.pose().translate(0, 0, 400);
        box(g, x + 2, y + 2, w, h, 0xC0000000, 0xC0000000);
        box(g, x, y, w, h, 0xF8121420, GOLD);
        g.fill(x + 3, y + 1, x + w - 3, y + 3, GOLD);
        // 머리
        disc(g, x + 18, y + 20, 12, 0xFFDCDCEB); disc(g, x + 18, y + 20, 10, 0xFF181A26);
        g.renderItem(new ItemStack(iconOf(n)), x + 10, y + 12);
        g.drawString(font, mage ? n.name().replace("공격력", "마력") : n.name(), x + 36, y + 9, 0xFFFFFFFF, true);
        String sub = kind + (n.maxRank() > 0 ? "  ·  " + rk + " / " + n.maxRank() : "");
        g.drawString(font, sub, x + 36, y + 21, GOLD, false);
        int yy = y + 36;
        g.fill(x + 6, yy, x + w - 6, yy + 1, 0xFF3A4260);
        yy += 5;
        if (n.kind().equals("start")) {
            g.drawString(font, "여기서 사방으로 뻗어 나가요", x + 8, yy, 0xFFB4B4C8, false); yy += 14;
        } else {
            if (rk > 0 && n.desc().isEmpty()) {
                for (int i = 0; i < now.size(); i++) {
                    if (i == 0) g.drawString(font, "지금", x + 8, yy, 0xFF9696AA, false);
                    g.drawString(font, now.get(i), x + 60, yy, 0xFFE1E1EB, false); yy += 11;
                }
                yy += 2;
            }
            for (int i = 0; i < wrapNext.size(); i++) {
                if (i == 0) g.drawString(font, full ? "효과" : rk > 0 ? "찍으면" : n.desc().isEmpty() ? "찍으면" : "효과", x + 8, yy, full ? 0xFF9696AA : 0xFF8CE68C, false);
                g.drawString(font, wrapNext.get(i) + (i == wrapNext.size() - 1 && !full && n.desc().isEmpty() ? "  ▲" : ""), x + 60, yy, full ? 0xFFE1E1EB : 0xFF8CE68C, false);
                yy += 11;
            }
            yy += 4;
            g.fill(x + 6, yy, x + w - 6, yy + 1, 0xFF3A4260);
            yy += 5;
            if (can) {
                g.fill(x + 6, yy, x + w - 6, yy + 16, 0xFF143018);
                String ps = pw0 == null ? "전투력 계산 중…" : pw0[0] > 0 ? String.format("전투력  +%,d  ▲", pw0[0]) : pw0[0] < 0 ? String.format("전투력  %,d  ▼", pw0[0]) : "전투력 변화 없음";
                g.drawString(font, ps, x + 12, yy + 4, pw0 != null && pw0[0] > 0 ? 0xFF6EE66E : 0xFFA0A0B4, false);
                yy += 22;
            }
        }
        String foot = !usable(n) ? (n.branch() == 1 ? branchName(1) : branchName(2)) + " 전용 (잠김)"
                : full ? "다 찍었어요" : n.maxRank() == 0 ? "" : !Defs.unlocked(n, k -> ClientData.passives.getOrDefault(k, 0)) ? "이어진 칸을 먼저 찍어야 해요"
                : ClientData.passivePoints <= 0 ? "포인트가 없어요" : "클릭: 찍기 (포인트 1)";
        int fc = foot.startsWith("클릭") ? 0xFF9696AA : 0xFFE07070;
        g.drawString(font, foot, x + 8, yy, fc, false);
        g.pose().popPose();
    }

    private List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(t) > maxW && cur.length() > 0) { out.add(cur.toString()); cur = new StringBuilder(word); }
            else cur = new StringBuilder(t);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static String fmt(String stat, double v) { return String.format(Defs.statLabel(stat), Defs.fmt(v)); }

    private boolean matches(Defs.Node n, String q) {
        return n.name().contains(q) || n.desc().contains(q)
                || (!n.stat().isEmpty() && Defs.statLabel(n.stat()).contains(q)) || (!n.stat2().isEmpty() && Defs.statLabel(n.stat2()).contains(q));
    }

    private int scrolls() {
        if (minecraft == null || minecraft.player == null) return 0;
        int c = 0;
        var item = rpg.core.item.ModItems.PASSIVE_RESET_SCROLL.get();
        for (var s : minecraft.player.getInventory().items) if (s.is(item)) c += s.getCount();
        return c;
    }

    private static String branchName(int b) {
        return switch (ClientData.job * 10 + b) {
            case 11 -> "가디언"; case 12 -> "버서커"; case 21 -> "스나이퍼"; case 22 -> "헌터";
            case 31 -> "아크메이지"; case 32 -> "클레릭"; case 41 -> "어쌔신"; case 42 -> "트릭스터";
            default -> "2차";
        };
    }

    private static String jobName() {
        String j = switch (ClientData.job) { case 1 -> "전사"; case 2 -> "궁수"; case 3 -> "마법사"; case 4 -> "도적"; default -> "초보자"; };
        return ClientData.branch > 0 ? j + " · " + branchName(ClientData.branch) : j;
    }

    /** 구역 이름: 위 · 아래 · 좌상 · 우상 · 좌하 · 우하 · 왼쪽 갈래 · 오른쪽 갈래 */
    private static String[] regionLabels() {
        return switch (ClientData.job) {
            case 1 -> new String[]{"공격", "기동·회복", "크리", "방어", "보스", "체력", "버서커", "가디언"};
            case 2 -> new String[]{"궁술", "질풍", "크리", "보스", "생존", "자원", "스나이퍼", "헌터"};
            case 3 -> new String[]{"마력", "흐름", "크리", "보스", "MP", "효율", "아크메이지", "클레릭"};
            case 4 -> new String[]{"암습", "날렵함", "크리", "보스", "생존", "상처", "어쌔신", "트릭스터"};
            default -> new String[0];
        };
    }

    // ───────── 조작 ─────────
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && mx >= ax && mx < ax + aw && my >= ay && my < ay + ah) {
            dragging = true;
            dragDist = 0;
            return true;
        }
        return false;
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
            if (dragDist < 4) {
                for (Defs.Node n : nodes) {
                    int r = rad(n), x = nx(n), y = ny(n);
                    if ((mx - x) * (mx - x) + (my - y) * (my - y) <= (r + 2) * (r + 2)) {
                        if (canLearn(n) && ClientData.passivePoints > 0) { Net.CH.sendToServer(new Net.Learn(1, n.id())); POWER.clear(); }
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
        CELL = Math.max(14, Math.min(56, CELL + (delta > 0 ? 3 : -3)));
        if (old != CELL) { panX = panX * CELL / old; panY = panY * CELL / old; homeX = homeX * CELL / old; homeY = homeY * CELL / old; }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searching && search.isFocused()) {
            if (key == 256) { searching = false; search.visible = false; return true; }
            return search.keyPressed(key, scan, mods) || true;
        }
        if (ClientEvents.PASSIVE_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
