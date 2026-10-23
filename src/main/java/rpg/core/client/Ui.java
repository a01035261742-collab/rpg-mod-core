package rpg.core.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 창 공통 모양·배치: 같은 테두리·제목줄·아래 버튼줄, 화면 밖으로 안 나가게 가운데 배치, 긴 글자 자르기.
 * 규칙: 제목 (x+8, y+7) · 오른쪽 정보 같은 줄 · 아래 버튼줄 y+h-24 (높이 18) · [메뉴] 왼쪽 · [닫기] 오른쪽.
 */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xF0141420, EDGE = 0xFF303044, BOTTOM = 0xFF505060, GOLD = 0xFFFFD24A;
    public static final int FOOT = 24, BTN_H = 18, MARGIN = 4;

    /** 화면 안에 들어오는 가운데 위치 (작은 화면에서도 위·왼쪽이 잘리지 않게) */
    public static int cx(int screenW, int w) { return Math.max(MARGIN, (screenW - w) / 2); }
    public static int cy(int screenH, int h) { return Math.max(MARGIN, (screenH - h) / 2); }

    /** 창 바탕 + 위 색줄 + 테두리 */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        g.fill(x, y, x + w, y + h, BG);
        g.fill(x, y, x + 1, y + h, EDGE);
        g.fill(x + w - 1, y, x + w, y + h, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, BOTTOM);
        g.fill(x, y, x + w, y + 1, accent);
    }

    /** 창 + 제목 + 오른쪽 정보 (right 는 빈 글자면 안 그림) */
    public static void frame(GuiGraphics g, Font f, int x, int y, int w, int h, int accent, String title, String right, int rightColor) {
        panel(g, x, y, w, h, accent);
        g.drawString(f, title, x + 8, y + 7, accent, true);
        if (right != null && !right.isEmpty()) {
            int max = w - 24 - f.width(title);
            String r = cut(f, right, max);
            g.drawString(f, r, x + w - 8 - f.width(r), y + 7, rightColor, true);
        }
    }

    /** 아래 줄 위 얇은 구분선 */
    public static void footLine(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 6, y + h - FOOT - 3, x + w - 6, y + h - FOOT - 2, 0x30FFFFFF);
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
            int bg, edge, top;
            if (!active) { bg = 0xFF16161E; edge = 0xFF2C2C38; top = 0xFF22222C; }
            else if (accent) { bg = hov ? 0xFF6A4220 : 0xFF4E3018; edge = hov ? GOLD : 0xFFB07030; top = 0xFF8A5A2C; }
            else { bg = hov ? 0xFF323A58 : 0xFF232A40; edge = hov ? GOLD : 0xFF4A5274; top = 0xFF3C4668; }
            g.fill(x, y, x + w, y + h, edge);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, top);   // 윗줄 살짝 밝게
            Font f = Minecraft.getInstance().font;
            String t = cut(f, getMessage().getString(), w - 6);
            int col = !active ? 0xFF6A6A78 : hov ? 0xFFFFFFFF : 0xFFE6E6F0;
            g.drawString(f, t, x + (w - f.width(t)) / 2, y + (h - 8) / 2, col, false);
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
