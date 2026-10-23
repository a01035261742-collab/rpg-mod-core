package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 메뉴 (M): 다른 창으로 가는 버튼 모음 */
public class MenuScreen extends Screen {
    private static final int W = 180, BH = 20, GAP = 4;
    private static final String[][] ITEMS = {
            {"스탯", "K"}, {"스킬", "L"}, {"패시브", "N"}, {"장비", "Tab"},
            {"파티", "P"}, {"퀘스트", "J"}, {"강화", ""}, {"일일 퀘스트", ""}};
    private int x, y, h;

    /** 인벤토리(장비 창)에서 열었으면 닫을 때 인벤토리로 돌아감 */
    private final boolean fromInv;

    public MenuScreen() {
        this(false);
    }

    public MenuScreen(boolean fromInv) {
        super(Component.literal("메뉴"));
        this.fromInv = fromInv;
    }

    @Override
    public void onClose() {
        if (fromInv) { Minecraft.getInstance().setScreen(null); Net.CH.sendToServer(new Net.OpenEquip(0)); }
        else super.onClose();
    }

    @Override
    protected void init() {
        int rows = (ITEMS.length + 1) / 2;
        h = 28 + rows * (BH + GAP) + 26;
        x = width / 2 - W / 2;
        y = height / 2 - h / 2;
        int bw = (W - 16 - GAP) / 2;
        for (int i = 0; i < ITEMS.length; i++) {
            final int k = i;
            String label = ITEMS[i][0] + (ITEMS[i][1].isEmpty() ? "" : " (" + ITEMS[i][1] + ")");
            int bx = x + 8 + (i % 2) * (bw + GAP), by = y + 24 + (i / 2) * (BH + GAP);
            addRenderableWidget(Button.builder(Component.literal(label), b -> open(k)).bounds(bx, by, bw, BH).build());
        }
        addRenderableWidget(Button.builder(Component.literal("닫기"), b -> onClose()).bounds(x + W - 54, y + h - 24, 46, 18).build());
    }

    private void open(int k) {
        Minecraft mc = Minecraft.getInstance();
        switch (k) {
            case 0 -> mc.setScreen(new StatScreen());
            case 1 -> mc.setScreen(new SkillScreen());
            case 2 -> mc.setScreen(new PassiveScreen());
            case 3 -> { mc.setScreen(null); Net.CH.sendToServer(new Net.OpenEquip(0)); }   // 장비 = 인벤토리
            case 4 -> mc.setScreen(new PartyScreen());
            case 5 -> mc.setScreen(new QuestScreen());
            case 6 -> EnhanceScreen.request();
            case 7 -> DailyScreen.request();
            default -> {}
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x, y, x + W, y + h, 0xF0141420);
        g.fill(x, y, x + W, y + 1, 0xFF55FFFF);
        g.drawString(font, "메뉴", x + 8, y + 8, 0xFF55FFFF, true);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
