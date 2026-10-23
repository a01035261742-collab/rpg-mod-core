package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import rpg.core.shop.Trade;

/** 거래창: 왼쪽 내 물건 · 오른쪽 상대 물건 · 골드 · [확인] [거래] */
public class TradeScreen extends AbstractContainerScreen<Trade.TradeMenu> {
    private Button ok, deal;

    public TradeScreen(Trade.TradeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 182;
        inventoryLabelY = 89;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("+1천"), b -> press(2)).bounds(x + 62, y + 28, 50, 12).build());
        addRenderableWidget(Button.builder(Component.literal("+1만"), b -> press(3)).bounds(x + 62, y + 41, 50, 12).build());
        addRenderableWidget(Button.builder(Component.literal("+10만"), b -> press(4)).bounds(x + 62, y + 54, 50, 12).build());
        addRenderableWidget(Button.builder(Component.literal("골드 0"), b -> press(5)).bounds(x + 62, y + 67, 50, 12).build());
        ok = addRenderableWidget(Button.builder(Component.literal("확인"), b -> press(0)).bounds(x + 8, y + 84 - 2, 50, 14).build());
        deal = addRenderableWidget(Button.builder(Component.literal("거래"), b -> press(1)).bounds(x + 118, y + 84 - 2, 50, 14).build());
        inventoryLabelY = 1000;   // 인벤 글자 숨김 (버튼 자리)
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean both = menu.data.get(4) == 1 && menu.data.get(5) == 1;
        ok.setMessage(Component.literal(menu.data.get(4) == 1 ? "✔ 확인됨" : "확인"));
        deal.active = both && menu.data.get(6) == 0;
        deal.setMessage(Component.literal(menu.data.get(6) == 1 ? "대기 중…" : "거래"));
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        g.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF555555);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF555555);
        // 확인 상태 테두리 (초록)
        int mine = menu.data.get(4) == 1 ? 0xFF30C030 : 0xFF707070, theirs = menu.data.get(5) == 1 ? 0xFF30C030 : 0xFF707070;
        g.fill(x + 5, y + 25, x + 61, y + 81, mine);
        g.fill(x + 113, y + 25, x + 169, y + 81, theirs);
        for (var s : menu.slots) {
            int sx = x + s.x, sy = y + s.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF373737);
            g.fill(sx, sy, sx + 17, sy + 17, 0xFFFFFFFF);
            g.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 5, 0xFF404040, false);
        String mg = String.format("%,d G", menu.myGold()), tg = String.format("%,d G", menu.theirGold());
        g.drawString(font, "나", 8, 16, 0xFF404040, false);
        g.drawString(font, mg, 62, 16, 0xFF8A6A00, false);
        g.drawString(font, "상대 " + tg, 116, 16, 0xFF8A6A00, false);
        if (menu.data.get(7) == 1) g.drawString(font, "상대 거래✔", 62, 86, 0xFF208020, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
