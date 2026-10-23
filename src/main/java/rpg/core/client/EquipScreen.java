package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import rpg.core.equip.EquipMenu;
import rpg.core.equip.PlayerEquip;

/** 장비 창: 바닐라 인벤토리 배치 + 오른쪽 장비 칸 (Tab) */
public class EquipScreen extends AbstractContainerScreen<EquipMenu> {
    private static final String[] LABELS = {"무기", "장갑", "망토", "벨트", "귀걸", "반지", "반지", "반지", "반지", "펜던", "훈장"};

    public EquipScreen(EquipMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 230;
        imageHeight = 166;
        inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        // 메뉴(M) 바로가기: 장비 패널 아래
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("메뉴"),
                b -> minecraft.setScreen(new MenuScreen())).bounds(leftPos + 178, topPos + 146, 50, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        // 바탕 (바닐라 느낌의 회색 창)
        g.fill(x, y, x + 176, y + imageHeight, 0xFFC6C6C6);
        g.fill(x, y, x + 176, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + imageHeight, 0xFFFFFFFF);
        g.fill(x + 175, y, x + 176, y + imageHeight, 0xFF555555);
        g.fill(x, y + imageHeight - 1, x + 176, y + imageHeight, 0xFF555555);
        // 장비 패널
        g.fill(x + 176, y, x + imageWidth, y + 142, 0xFF2A2A3A);
        g.fill(x + 176, y, x + imageWidth, y + 1, 0xFFFFD24A);
        // 캐릭터 상자
        g.fill(x + 25, y + 7, x + 76, y + 78, 0xFF000000);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 51, y + 75, 30, (float) (x + 51 - mx), (float) (y + 25 - my), minecraft.player);
        // 칸 테두리
        for (var s : menu.slots) slotFrame(g, x + s.x, y + s.y);
        // 장비 칸 이름 (빈 칸일 때)
        for (int i = 0; i < PlayerEquip.SLOT_TYPES.length; i++) {
            var s = menu.slots.get(i);
            if (i == 0) continue;
            if (!s.hasItem()) g.drawString(font, LABELS[i].substring(0, 1), x + s.x + 5, y + s.y + 4, 0xFF707080, false);
        }
    }

    private static void slotFrame(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
        g.fill(sx, sy, sx + 17, sy + 17, 0xFFFFFFFF);
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "장비", 192, 130, 0xFFFFD24A, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
