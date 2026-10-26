package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 첫 접속: 야생으로 시작 / RPG로 시작 (한 번만) */
public class WorldChoiceScreen extends Screen {
    private static final int W = 260, H = 130;
    private int x, y;

    public WorldChoiceScreen() { super(Component.literal("어디서 시작할까요?")); }

    @Override
    protected void init() {
        x = Ui.cx(width, W); y = Ui.cy(height, H);
        addRenderableWidget(Ui.button(Component.literal("야생으로 시작"), b -> pick(false)).bounds(x + 16, y + H - 34, 108, 20).build());
        addRenderableWidget(Ui.button(Component.literal("RPG로 시작"), b -> pick(true)).bounds(x + W - 124, y + H - 34, 108, 20).build());
    }

    private void pick(boolean rpg) {
        Net.CH.sendToServer(new Net.ChooseWorld(rpg));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.panel(g, x, y, W, H, Ui.GOLD);
        g.drawCenteredString(font, "어디서 시작할까요?", x + W / 2, y + 12, 0xFFFFD24A);
        g.drawString(font, "야생: 바닐라 + 다른 모험 모드", x + 16, y + 34, 0xFFC8C8C8, false);
        g.drawString(font, "RPG: 레벨·스킬·장비·보스", x + 16, y + 48, 0xFFC8C8C8, false);
        g.drawString(font, "언제든 /rpg 이동 이나 차원 문으로 오갈 수 있어요", x + 16, y + 66, 0xFF8A90B0, false);
        g.drawString(font, "인벤토리는 월드마다 따로 (공유 창고로 주고받기)", x + 16, y + 78, 0xFF8A90B0, false);
        super.render(g, mx, my, pt);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
