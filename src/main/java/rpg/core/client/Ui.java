package rpg.core.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * 창 공통 모양·배치: 같은 테두리·제목줄·아래 버튼줄, 화면 밖으로 안 나가게 가운데 배치, 긴 글자 자르기.
 * 규칙: 제목 (x+8, y+7) · 오른쪽 정보 같은 줄 · 아래 버튼줄 y+h-24 (높이 18) · [메뉴] 왼쪽 · [닫기] 오른쪽.
 */
public final class Ui {
    private Ui() {}

    // 와인 고급 판타지 (시안 ui_d*): 색
    public static final int BG = 0xF0141420, EDGE = 0xFF4A2028, BOTTOM = 0xFF6A4A48, GOLD = 0xFFFFD86A;
    public static final int BG1 = 0xFF3A1018, BG2 = 0xFF16060A, IN1 = 0xFF1E080C, IN2 = 0xFF2A0E14, SEP = 0xFF4A2028,
            TXT = 0xFFF0E6C8, VAL = 0xFFFFD86A, GRAY = 0xFFA89890, OUT = 0xFF0A0608,
            G1 = 0xFF5A3A10, G2 = 0xFF8C5E1C, G3 = 0xFFC8902E, G4 = 0xFFECC25A, G5 = 0xFFFFF0B0,
            GREEN = 0xFF7CE07C, RED = 0xFFFF6060, BLUE = 0xFF80C0FF, PURPLE = 0xFFC080FF;
    public static final int FOOT = 24, BTN_H = 18, MARGIN = 4;

    private static ResourceLocation tex(String n) { return new ResourceLocation("rpgcore", "textures/gui/ui/" + n + ".png"); }
    private static final ResourceLocation FRAME = tex("frame"), PATTERN = tex("pattern"), SLOT = tex("slot"),
            PL = tex("plaque_l"), PM = tex("plaque_m"), PR = tex("plaque_r"),
            CTL = tex("corner_tl"), CTR = tex("corner_tr"), CBL = tex("corner_bl"), CBR = tex("corner_br");

    /** 화면 안에 들어오는 가운데 위치 (작은 화면에서도 위·왼쪽이 잘리지 않게) */
    public static int cx(int screenW, int w) { return Math.max(MARGIN, (screenW - w) / 2); }
    public static int cy(int screenH, int h) { return Math.max(MARGIN, (screenH - h) / 2); }

    /** 9조각 그리기: 텍스처(ts×ts, 테두리 b) → 화면 (x,y,w,h). half = 2배 텍스처를 반 크기로 */
    public static void nine(GuiGraphics g, ResourceLocation t, int x, int y, int w, int h, int ts, int b, boolean half) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        int d = half ? b / 2 : b, m = ts - 2 * b;
        g.blit(t, x, y, d, d, 0, 0, b, b, ts, ts);
        g.blit(t, x + w - d, y, d, d, ts - b, 0, b, b, ts, ts);
        g.blit(t, x, y + h - d, d, d, 0, ts - b, b, b, ts, ts);
        g.blit(t, x + w - d, y + h - d, d, d, ts - b, ts - b, b, b, ts, ts);
        g.blit(t, x + d, y, w - 2 * d, d, b, 0, m, b, ts, ts);
        g.blit(t, x + d, y + h - d, w - 2 * d, d, b, ts - b, m, b, ts, ts);
        g.blit(t, x, y + d, d, h - 2 * d, 0, b, b, m, ts, ts);
        g.blit(t, x + w - d, y + d, d, h - 2 * d, ts - b, b, b, m, ts, ts);
        g.blit(t, x + d, y + d, w - 2 * d, h - 2 * d, b, b, m, m, ts, ts);
    }

    /** 창 바탕: 붉은 그라데이션 + 마름모 무늬 + 금 테두리 + 모서리 메달 */
    public static void window(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x + 3, y + 3, x + w - 3, y + h - 3, BG1, BG2);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        for (int ty = y + 4; ty < y + h - 4; ty += 12)
            for (int tx = x + 4; tx < x + w - 4; tx += 12) {
                int cw = Math.min(12, x + w - 4 - tx), ch = Math.min(12, y + h - 4 - ty);
                g.blit(PATTERN, tx, ty, 0, 0, cw, ch, 12, 12);
            }
        nine(g, FRAME, x, y, w, h, 32, 8, false);
        g.blit(CTL, x - 2, y - 2, 0, 0, 26, 26, 26, 26);
        g.blit(CTR, x + w - 24, y - 2, 0, 0, 26, 26, 26, 26);
        g.blit(CBL, x - 2, y + h - 24, 0, 0, 26, 26, 26, 26);
        g.blit(CBR, x + w - 24, y + h - 24, 0, 0, 26, 26, 26, 26);
    }

    /** 위 가운데 현판 (제목) */
    public static void plaque(GuiGraphics g, Font f, int cxp, int y, String title) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        int mw = f.width(title) + 14, x0 = cxp - mw / 2 - 9;
        g.blit(PL, x0, y, 9, 14, 0, 0, 18, 28, 18, 28);
        g.blit(PM, x0 + 9, y, mw, 14, 0, 0, 4, 28, 4, 28);
        g.blit(PR, x0 + 9 + mw, y, 9, 14, 0, 0, 18, 28, 18, 28);
        g.drawString(f, title, cxp - f.width(title) / 2, y + 3, VAL, true);
    }

    /** 오른쪽 위 금 상자 (골드 등): 오른쪽 끝 xr 기준 */
    public static void goldBox(GuiGraphics g, Font f, int xr, int y, String s, boolean coin) {
        int w = f.width(s) + (coin ? 17 : 8), x0 = xr - w;
        g.fill(x0, y, xr, y + 13, OUT); g.fill(x0 + 1, y + 1, xr - 1, y + 12, G2); g.fill(x0 + 2, y + 2, xr - 2, y + 11, IN1);
        if (coin) { g.fill(x0 + 4, y + 4, x0 + 9, y + 9, G4); g.fill(x0 + 5, y + 3, x0 + 8, y + 10, G4); g.fill(x0 + 3, y + 5, x0 + 10, y + 8, G4); }
        g.drawString(f, s, x0 + (coin ? 13 : 4), y + 3, VAL, false);
    }

    /** 안쪽 판 (내용 칸) */
    public static void inset(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fillGradient(x0, y0, x1, y1, IN1, IN2);
        g.fill(x0, y0, x1, y0 + 1, 0xFF000000); g.fill(x0, y0, x0 + 1, y1, 0xFF000000);
        g.fill(x0, y1 - 1, x1, y1, G2); g.fill(x1 - 1, y0, x1, y1, G2);
    }

    /** 아이템 칸 (18×18, 아이템은 x+1,y+1) */
    public static void slot(GuiGraphics g, int x, int y) { com.mojang.blaze3d.systems.RenderSystem.enableBlend(); g.blit(SLOT, x, y, 18, 18, 0, 0, 36, 36, 36, 36); }

    /** 아이템 칸 (크기 지정) */
    public static void slot(GuiGraphics g, int x, int y, int size) { com.mojang.blaze3d.systems.RenderSystem.enableBlend(); g.blit(SLOT, x, y, size, size, 0, 0, 36, 36, 36, 36); }

    /** HUD 상자: 반투명 와인 + 금 테두리 + 네 귀 금점 */
    public static void hudBox(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xDC1E080C);
        g.fill(x0, y0, x1, y0 + 1, G3); g.fill(x0, y1 - 1, x1, y1, G3); g.fill(x0, y0, x0 + 1, y1, G3); g.fill(x1 - 1, y0, x1, y1, G3);
        g.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 3, SEP); g.fill(x0 + 2, y1 - 3, x1 - 2, y1 - 2, SEP);
        g.fill(x0 + 2, y0 + 2, x0 + 3, y1 - 2, SEP); g.fill(x1 - 3, y0 + 2, x1 - 2, y1 - 2, SEP);
        for (int[] c : new int[][]{{x0, y0}, {x1 - 1, y0}, {x0, y1 - 1}, {x1 - 1, y1 - 1}}) { g.fill(c[0] - 1, c[1] - 1, c[0] + 2, c[1] + 2, G4); }
    }

    /** 고른 칸 금 테두리 */
    public static void selBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y, G4); g.fill(x - 1, y + h, x + w + 1, y + h + 1, G4);
        g.fill(x - 1, y, x, y + h, G4); g.fill(x + w, y, x + w + 1, y + h, G4);
    }

    /** 가로·세로 구분선 */
    public static void hsep(GuiGraphics g, int x0, int x1, int y) { g.fill(x0, y, x1, y + 1, SEP); }
    public static void vsep(GuiGraphics g, int x, int y0, int y1) { g.fill(x, y0, x + 1, y1, SEP); }

    /** 줄 바탕 (고르면 금 막대) */
    public static void row(GuiGraphics g, int x0, int y0, int x1, int y1, boolean sel) {
        g.fill(x0, y0, x1, y1, sel ? SEP : IN2);
        if (sel) g.fill(x0, y0, x0 + 2, y1, G4);
    }

    /** 막대 (금 테두리) */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, G2); g.fill(x, y, x + w, y + h, 0xFF000000);
        int fw = Math.round(w * Math.max(0, Math.min(1, f)));
        if (fw > 0) g.fill(x, y, x + fw, y + h, color);
    }

    /** 창 바탕 (예전 이름) */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) { window(g, x, y, w, h); }

    /** 창 + 현판 제목 + 오른쪽 위 금 상자 (right 는 빈 글자면 안 그림) */
    public static void frame(GuiGraphics g, Font f, int x, int y, int w, int h, int accent, String title, String right, int rightColor) {
        window(g, x, y, w, h);
        plaque(g, f, x + w / 2, y + 1, title);
        if (right != null && !right.isEmpty()) {
            int max = w / 2 - f.width(title) / 2 - 36;
            String r = cut(f, right, max);
            if (!r.isEmpty()) goldBox(g, f, x + w - 14, y + 4, r, r.endsWith(" G") || r.endsWith("G"));
        }
    }

    /** 아래 줄 위 얇은 구분선 */
    public static void footLine(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 8, y + h - FOOT - 3, x + w - 8, y + h - FOOT - 2, SEP);
    }

    /** [닫기] 버튼 (오른쪽 아래) */
    public static Button close(int x, int y, int w, int h, Runnable onClose) {
        return button(Component.literal("닫기"), b -> onClose.run()).bounds(x + w - 52, y + h - FOOT, 46, BTN_H).build();
    }

    /** [메뉴] 버튼 (왼쪽 아래) */
    public static Button menu(int x, int y, int h) {
        return button(Component.literal("메뉴"), b -> Minecraft.getInstance().setScreen(new MenuScreen())).bounds(x + 6, y + h - FOOT, 40, BTN_H).build();
    }

    // ───────── 납작한 버튼 (A안, 임시) ─────────
    /** Button.builder 대신: Ui.button(글자, 동작).bounds(...).build() */
    public static Builder button(Component msg, Button.OnPress press) { return new Builder(msg, press); }

    public static final class Builder {
        private final Component msg; private final Button.OnPress press;
        private int x, y, w = 150, h = 20; private Boolean accent;
        Builder(Component m, Button.OnPress p) { msg = m; press = p; }
        public Builder bounds(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; return this; }
        public Builder accent(boolean a) { accent = a; return this; }
        public FlatButton build() { return new FlatButton(x, y, w, h, msg, press, accent != null ? accent : autoAccent(msg.getString())); }
    }

    /** 강조 버튼 (주황): 강화 · 제작 · 수락 · 완료 · 적용 · 전직 · 시련 시작 */
    private static boolean autoAccent(String s) {
        String t = s.replaceAll("§.", "");
        return t.equals("강화") || t.contains("제작") || t.equals("수락") || t.startsWith("완료") || t.equals("새 옵션 적용")
                || t.contains("전직") || t.equals("시련 시작") || t.equals("합성") || t.startsWith("합성 ");
    }

    public static class FlatButton extends Button {
        private final boolean accent;
        protected FlatButton(int x, int y, int w, int h, Component msg, OnPress press, boolean accent) {
            super(x, y, w, h, msg, press, DEFAULT_NARRATION);
            this.accent = accent;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hov = active && isHoveredOrFocused();
            String t9 = !active ? "btn_off" : accent ? (hov ? "btn_accent_hover" : "btn_accent") : (hov ? "btn_hover" : "btn");
            nine(g, tex(t9), x, y, w, h, 40, 6, true);
            Font f = Minecraft.getInstance().font;
            String t = cut(f, getMessage().getString(), w - 6);
            int col = !active ? 0xFF7A6A68 : hov ? 0xFFFFFFFF : TXT;
            g.drawString(f, t, x + (w - f.width(t)) / 2, y + (h - 8) / 2, col, true);
        }
    }

    /** 폭을 넘으면 … 로 자르기 */
    public static String cut(Font f, String s, int w) {
        if (w <= 0) return "";
        if (f.width(s) <= w) return s;
        return f.plainSubstrByWidth(s, Math.max(0, w - f.width("…"))) + "…";
    }

    /** 줄 바꿈해서 그리기 → 다음 y. maxLines 넘으면 마지막 줄 … */
    public static int wrap(GuiGraphics g, Font f, String s, int x, int y, int w, int color, int maxLines) {
        List<FormattedCharSequence> ls = f.split(Component.literal(s), w);
        int n = Math.min(ls.size(), maxLines);
        for (int i = 0; i < n; i++) g.drawString(f, ls.get(i), x, y + i * 10, color, false);
        return y + n * 10;
    }

    /** 줄 바꿈 줄 수 */
    public static int lines(Font f, String s, int w) {
        return f.split(Component.literal(s), w).size();
    }

    /**
     * 탭 버튼을 폭 안에서 여러 줄로 배치 → 각 탭의 {x, y, w}. rowH = 줄 높이.
     */
    public static int[][] flow(Font f, List<String> names, int x, int y, int maxW, int pad, int gap, int rowH) {
        int[][] out = new int[names.size()][3];
        int tx = x, ty = y;
        for (int i = 0; i < names.size(); i++) {
            int bw = f.width(names.get(i)) + pad;
            if (tx > x && tx + bw > x + maxW) { tx = x; ty += rowH; }
            out[i][0] = tx; out[i][1] = ty; out[i][2] = bw;
            tx += bw + gap;
        }
        return out;
    }

    /** flow 결과 줄 수 */
    public static int rows(int[][] flow, int rowH) {
        if (flow.length == 0) return 0;
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] r : flow) { min = Math.min(min, r[1]); max = Math.max(max, r[1]); }
        return (max - min) / rowH + 1;
    }
}
