package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** 보스 기믹 알림 (모여라·흩어져라 등): 화면 위 가운데, 보스바 아래에 크게 몇 초 */
public final class BossAlert {
    private BossAlert() {}

    private static String text = "";
    private static int color = 0xFFFFFFFF;
    private static long start, end;

    public static void show(String t, int c, int ticks) {
        text = t; color = 0xFF000000 | c;
        start = System.currentTimeMillis();
        end = start + ticks * 50L;
    }

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        long now = System.currentTimeMillis();
        if (text.isEmpty() || now >= end || Minecraft.getInstance().options.hideGui) return;
        var font = Minecraft.getInstance().font;
        float a = Math.min(1f, Math.min((now - start) / 150f, (end - now) / 400f));   // 살짝 나타났다 사라짐
        int alpha = Math.max(8, (int) (a * 255));
        float sc = 1.6f;
        int tw = font.width(text);
        int y = 58;
        g.pose().pushPose();
        g.pose().translate(w / 2f, y, 0);
        g.pose().scale(sc, sc, 1);
        int bw = tw / 2 + 8;
        g.fill(-bw, -4, bw, 12, (alpha * 3 / 5) << 24);
        g.drawString(font, text, -tw / 2, 0, (alpha << 24) | (color & 0xFFFFFF), true);
        g.pose().popPose();
    }
}
