package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 파티원 준비 확인 (30초) */
public class RaidReadyScreen extends Screen {
    private static final int W = 240, H = 96;
    private final String raid, leader;
    private final long until = System.currentTimeMillis() + 30_000;
    private int x, y;
    private boolean answered;

    public RaidReadyScreen(String raid, String leader) { super(Component.literal("레이드 준비")); this.raid = raid; this.leader = leader; }

    @Override
    protected void init() {
        x = Ui.cx(width, W); y = Ui.cy(height, H);
        addRenderableWidget(Ui.button(Component.literal("준비"), b -> send(1)).bounds(x + 16, y + H - 30, 96, 20).build());
        addRenderableWidget(Ui.button(Component.literal("거절"), b -> send(2)).bounds(x + W - 112, y + H - 30, 96, 20).build());
    }

    private void send(int a) { answered = true; Net.CH.sendToServer(new Net.RaidAction(a, raid)); onClose(); }

    @Override
    public void tick() { if (System.currentTimeMillis() > until) onClose(); }

    @Override
    public void onClose() { if (!answered) { answered = true; Net.CH.sendToServer(new Net.RaidAction(2, raid)); } super.onClose(); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.panel(g, x, y, W, H, 0xFFB050E0);
        g.drawCenteredString(font, "레이드 [" + raid + "] 입장 요청", x + W / 2, y + 12, 0xFFE0A0FF);
        g.drawCenteredString(font, leader + "님이 입장을 요청했어요", x + W / 2, y + 30, 0xFFC8C8C8);
        long left = Math.max(0, (until - System.currentTimeMillis()) / 1000);
        g.drawCenteredString(font, left + "초 남음", x + W / 2, y + 44, 0xFFFFB040);
        super.render(g, mx, my, pt);
    }

    @Override public boolean isPauseScreen() { return false; }
}
