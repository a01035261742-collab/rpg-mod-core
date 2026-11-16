package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * 보스 알림 (우리 HUD 상자 모양: 와인 + 금 테두리).
 * - 패턴 설명 (모여라·흩어져라 등): 화면 위 가운데, 보스바 아래
 * - 맞음 표시 (◆ 패턴에 맞음): 아래 HUD 상자 바로 위
 */
public final class BossAlert {
    private BossAlert() {}

    private static String text = "", hit = "";
    private static int color = 0xFFFFFFFF, hitColor = 0xFFFF8070;
    private static long start, end, hitStart, hitEnd;

    /** ticks < 0 = 맞음 표시 (-ticks 틱) */
    public static void show(String t, int c, int ticks) {
        long now = System.currentTimeMillis();
        if (ticks < 0) { hit = t; hitColor = 0xFF000000 | c; hitStart = now; hitEnd = now - ticks * 50L; return; }
        text = t; color = 0xFF000000 | c;
        start = now;
        end = start + ticks * 50L;
    }

    private static int alpha(long now, long s, long e) {
        float a = Math.min(1f, Math.min((now - s) / 150f, (e - now) / 400f));   // 살짝 나타났다 사라짐
        return Math.max(8, (int) (a * 255));
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        var font = mc.font;
        long now = System.currentTimeMillis();
        if (!text.isEmpty() && now < end) {
            int al = alpha(now, start, end);
            float sc = 1.4f;
            int tw = (int) Math.ceil(font.width(text) * sc);
            int y = 52, x0 = w / 2 - tw / 2 - 12, x1 = w / 2 + tw / 2 + 12;
            int y1 = y + (int) (9 * sc) + 10;
            box(g, x0, y, x1, y1, al);
            g.pose().pushPose();
            g.pose().translate(w / 2f, y + 6, 0);
            g.pose().scale(sc, sc, 1);
            g.drawString(font, text, -font.width(text) / 2, 0, (al << 24) | (color & 0xFFFFFF), true);
            g.pose().popPose();
        }
        if (!hit.isEmpty() && now < hitEnd) {
            int al = alpha(now, hitStart, hitEnd);
            RpgHud.layout(w, h);
            int tw = font.width(hit);
            int y1 = RpgHud.boxY0 - 4, y0 = y1 - 16;
            box(g, w / 2 - tw / 2 - 8, y0, w / 2 + tw / 2 + 8, y1, al);
            g.drawString(font, hit, w / 2 - tw / 2, y0 + 4, (al << 24) | (hitColor & 0xFFFFFF), true);
        }
    }

    /** Ui.hudBox 와 같은 모양 (나타났다 사라질 때 투명도만) */
    private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int al) {
        if (al >= 250) { Ui.hudBox(g, x0, y0, x1, y1); return; }
        int f = al << 24;
        g.fill(x0, y0, x1, y1, (al * 0xDC / 255) << 24 | 0x1E080C);
        int gold = f | (Ui.G3 & 0xFFFFFF);
        g.fill(x0, y0, x1, y0 + 1, gold); g.fill(x0, y1 - 1, x1, y1, gold); g.fill(x0, y0, x0 + 1, y1, gold); g.fill(x1 - 1, y0, x1, y1, gold);
    }
}
