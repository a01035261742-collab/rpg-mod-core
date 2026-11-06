package rpg.core.client;

import net.minecraft.client.Minecraft;

/**
 * 창 1.5배 (시안 ui_bag_big2): 창 안은 원래 크기 좌표로 그리고, 그림·마우스만 배율로 바꿈.
 * 화면 픽셀이 고르게 나오게 (GUI 크기 2 → 1.5배 = 3픽셀) 맞추고, 화면보다 크면 줄임.
 */
public interface Scaled {
    double uiScale();

    /** 원래 크기 기준 창 폭·높이 (화면에 들어가는지 볼 때) */
    default int baseW() { return CharWin.W; }
    default int baseH() { return CharWin.H; }

    static double calc(int guiW, int guiH, int bw, int bh) {
        double g = Minecraft.getInstance().getWindow().getGuiScale();
        double s = Math.round(g * 1.5) / g;
        s = Math.min(s, Math.min((guiW - 8) / (double) bw, (guiH - 8) / (double) bh));
        return Math.max(1.0, s);
    }

    /** 지금 그리는 창의 배율 (가위질 영역 계산용) */
    double[] CUR = {1.0};

    /** 배율 창 안에서 쓰는 가위질 (GuiGraphics.enableScissor 는 배율을 모름) */
    static void scissor(net.minecraft.client.gui.GuiGraphics g, int x0, int y0, int x1, int y1) {
        double s = CUR[0];
        g.enableScissor((int) Math.floor(x0 * s), (int) Math.floor(y0 * s), (int) Math.ceil(x1 * s), (int) Math.ceil(y1 * s));
    }
}
