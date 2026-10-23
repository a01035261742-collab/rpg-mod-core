package rpg.core.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import rpg.core.shop.Storage;

/** 창고 창: 바닐라 상자 창 + 위쪽 [창고 1~5] 탭 + [확장] */
public class StorageScreen extends ContainerScreen {
    private final Storage.StorageMenu sm;
    private final Button[] tabs = new Button[Storage.MAX_PAGES];
    private Button expand;

    public StorageScreen(Storage.StorageMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.sm = menu;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < Storage.MAX_PAGES; i++) {
            final int k = i;
            tabs[i] = addRenderableWidget(Ui.button(Component.literal(String.valueOf(i + 1)), b -> press(k)).bounds(leftPos + i * 22, topPos - 16, 20, 14).build());
        }
        expand = addRenderableWidget(Ui.button(Component.literal("확장"), b -> press(9)).bounds(leftPos + imageWidth - 64, topPos - 16, 64, 14).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int page = sm.info.get(0), pages = sm.info.get(1);
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].visible = i < pages;
            tabs[i].active = i != page;
        }
        boolean full = pages >= Storage.MAX_PAGES;
        expand.visible = !full;
        if (!full) {
            long cost = Storage.COST[Math.max(0, Math.min(Storage.COST.length - 1, pages - 1))];
            expand.setMessage(Component.literal(sm.info.get(2) == 1 ? "한 번 더!" : "+" + (cost >= 10000 ? cost / 10000 + "만" : cost + "") + " G"));
        }
    }

    /** 바탕: 다른 창과 같은 어두운 창 */
    @Override
    protected void renderBg(net.minecraft.client.gui.GuiGraphics g, float pt, int mx, int my) {
        Ui.panel(g, leftPos, topPos, imageWidth, imageHeight, 0xFF80C0FF);
        for (var s : menu.slots) {
            int sx = leftPos + s.x, sy = topPos + s.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF3A4260);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF1C2132);
        }
    }

    @Override
    protected void renderLabels(net.minecraft.client.gui.GuiGraphics g, int mx, int my) {
        g.drawString(font, "창고 " + (sm.info.get(0) + 1) + " / " + sm.info.get(1), titleLabelX, titleLabelY, 0xFF80C0FF, false);
        g.drawString(font, "인벤토리", inventoryLabelX, inventoryLabelY, 0xFFA0A0B0, false);
    }
}
