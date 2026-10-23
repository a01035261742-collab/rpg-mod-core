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
        addRenderableWidget(Ui.button(Component.literal("메뉴"),
                b -> minecraft.setScreen(new MenuScreen(true))).bounds(leftPos + 178, topPos + 146, 50, 18).build());
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        // 바탕: 다른 창과 같은 어두운 창 (임시 A안)
        Ui.panel(g, x, y, imageWidth, imageHeight, Ui.GOLD);
        g.fill(x + 176, y + 1, x + 177, y + imageHeight - 1, Ui.EDGE);   // 인벤 | 장비 구분선
        // 캐릭터 상자
        g.fill(x + 24, y + 6, x + 77, y + 79, 0xFF4A5274);
        g.fill(x + 25, y + 7, x + 76, y + 78, 0xFF0A0A12);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 51, y + 75, 30, (float) (x + 51 - mx), (float) (y + 25 - my), minecraft.player);
        // 2×2 제작: 화살표
        g.drawString(font, "→", x + 138, y + 32, 0xFF8A90B0, false);
        // 칸 테두리
        for (var s : menu.slots) slotFrame(g, x + s.x, y + s.y);
        // 장비 칸 이름 (빈 칸일 때)
        for (int i = 0; i < PlayerEquip.SLOT_TYPES.length; i++) {
            var s = menu.slots.get(i);
            if (i == 0) continue;
            if (!s.hasItem()) g.drawString(font, LABELS[i].substring(0, 1), x + s.x + 5, y + s.y + 4, 0xFF5A6080, false);
        }
    }

    private static void slotFrame(GuiGraphics g, int sx, int sy) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF3A4260);
        g.fill(sx, sy, sx + 16, sy + 16, 0xFF1C2132);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "장비", 192, 130, 0xFFFFD24A, false);
        g.drawString(font, "제작", 98, 6, 0xFF8A90B0, false);
        // 인벤토리 글자는 칸이랑 겹쳐서 뺌
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
