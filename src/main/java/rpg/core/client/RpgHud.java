package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** HP·MP·EXP 바 (핫바 바로 위) */
public final class RpgHud {
    private RpgHud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientData.ready) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;
        Font font = mc.font;
        int cx = w / 2;
        int left = cx - 91;

        // EXP 바 (핫바 바로 위, 얇게)
        int ey = h - 22 - 6;
        double ratio = ClientData.need <= 0 || ClientData.need == Long.MAX_VALUE ? 1.0 : (double) ClientData.exp / ClientData.need;
        ratio = Math.max(0, Math.min(1, ratio));
        g.fill(left, ey, left + 182, ey + 4, 0xFF1A1A1A);
        g.fill(left, ey, left + (int) (182 * ratio), ey + 4, 0xFFE8C030);
        String pct = String.format("%.2f%%", ratio * 100);
        g.drawString(font, pct, left + 182 + 4, ey - 2, 0xFFE8C030, true);

        // HP / MP 바 (그 위)
        int by = ey - 12;
        bar(g, font, left, by, 90, 10, ClientData.hp, ClientData.maxHp, 0xFFD02828, 0xFF4A1414, "HP");
        bar(g, font, left + 92, by, 90, 10, ClientData.mp, ClientData.maxMp, 0xFF2A68E0, 0xFF142448, "MP");

        // 레벨 (왼쪽)
        String lv = "Lv." + ClientData.level;
        g.drawString(font, lv, left - font.width(lv) - 5, by + 1, 0xFFFFD24A, true);
        if (ClientData.statPoints > 0) {
            String sp = "+" + ClientData.statPoints;
            g.drawString(font, sp, left - font.width(sp) - 5, by + 11, 0xFF80FF80, true);
        }
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, int bw, int bh, double v, double max, int fg, int bg, String label) {
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000);
        g.fill(x, y, x + bw, y + bh, bg);
        double r = max <= 0 ? 0 : Math.max(0, Math.min(1, v / max));
        g.fill(x, y, x + (int) (bw * r), y + bh, fg);
        String s = label + " " + (long) Math.ceil(v) + "/" + (long) Math.ceil(max);
        g.drawString(font, s, x + bw / 2 - font.width(s) / 2, y + 1, 0xFFFFFFFF, true);
    }
}
