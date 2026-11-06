package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import rpg.core.equip.EquipMenu;
import rpg.core.equip.PlayerEquip;

/** 장비 탭 (시안 ui_d2): 왼쪽 갑옷 · 가운데 캐릭터 · 오른쪽 장갑·망토·벨트·무기 · 아래 장신구 · 오른쪽 능력치 · [가방] 팝업 */
public class EquipScreen extends AbstractContainerScreen<EquipMenu> {
    private static final String[] EMPTY = {"", "장갑", "망토", "벨트", "귀걸이", "반지", "반지", "반지", "반지", "펜던트", "훈장"};

    public EquipScreen(EquipMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = CharWin.W;
        imageHeight = CharWin.H;
        EquipMenu.bagOpen = false;
    }

    @Override
    protected void init() {
        super.init();
        CharWin.addTabs(this::addRenderableWidget, leftPos, topPos, CharWin.EQUIP);
        addRenderableWidget(Ui.button(Component.literal("가방"), b -> { EquipMenu.bagOpen = !EquipMenu.bagOpen; rebuildWidgets(); })
                .bounds(leftPos + 228, topPos + 156, 56, 15).accent(EquipMenu.bagOpen).build());
    }

    @Override
    public void removed() {
        super.removed();
        EquipMenu.bagOpen = false;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        CharWin.draw(g, font, x, y);
        // 캐릭터 상자
        g.fill(x + 98, y + 30, x + 143, y + 116, 0xFF12040A);
        Ui.hsep(g, x + 98, x + 143, y + 116);
        if (!EquipMenu.bagOpen)
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 120, y + 108, 34, (float) (x + 120 - mx), (float) (y + 50 - my), minecraft.player);
        // 칸 테두리 (보이는 칸만)
        for (var s : menu.slots) if (s.isActive() && s.x > -1000) Ui.slot(g, x + s.x - 1, y + s.y - 1);
        // 빈 장비 칸: 장신구 윤곽
        if (!EquipMenu.bagOpen)
            for (int i = 1; i < PlayerEquip.SLOT_TYPES.length; i++) {
                var s = menu.slots.get(i);
                if (!s.hasItem())
                    g.blit(new ResourceLocation("rpgcore", "textures/gui/slot/" + PlayerEquip.SLOT_TYPES[i] + ".png"), x + s.x, y + s.y, 0, 0, 16, 16, 16, 16);
            }
        // 오른쪽 능력치
        Ui.vsep(g, x + 169, y + 28, y + 172);
        int rx = x + 175, rr = x + 284, ry = y + 30;
        g.drawString(font, "Lv." + ClientData.level + " " + ClientData.jobName(), rx, ry, Ui.VAL, true);
        Ui.hsep(g, rx, rr, ry + 11);
        ry += 16;
        ry = line(g, rx, rr, ry, "HP", String.format("%,.0f", ClientData.maxHp));
        ry = line(g, rx, rr, ry, "MP", String.format("%,.0f", ClientData.maxMp));
        ry = line(g, rx, rr, ry, ClientData.job == 3 ? "마력" : "스공", String.format("%,.0f", ClientData.atkMax));
        ry = line(g, rx, rr, ry, "방어력", String.format("%,.0f", ClientData.defPct));
        ry = line(g, rx, rr, ry, "치명", String.format("%.1f%%", ClientData.crit));
        ry = line(g, rx, rr, ry, "보스", String.format("+%.0f%%", ClientData.bossPct));
        // 가방 팝업
        if (EquipMenu.bagOpen) {
            int bx = x + EquipMenu.BAG_X - 9, by = y + EquipMenu.BAG_Y - 20;
            Ui.window(g, bx, by, 180, 112);
            g.drawString(font, "가방", bx + 10, by + 8, Ui.VAL, true);
            g.drawString(font, "Shift+클릭: 입기", bx + 72, by + 8, Ui.GRAY, false);
            for (var s : menu.slots) if (s.isActive() && s.x > -1000) Ui.slot(g, x + s.x - 1, y + s.y - 1);
        }
    }

    private int line(GuiGraphics g, int rx, int rr, int ry, String k, String v) {
        g.drawString(font, k, rx, ry, Ui.TXT, false);
        g.drawString(font, v, rr - font.width(v), ry, Ui.VAL, true);
        return ry + 12;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        RpgHud.buffBar(g, font, width, mx, my);
        if (!EquipMenu.bagOpen && hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int i = menu.slots.indexOf(hoveredSlot);
            String n = i > 0 && i < EMPTY.length ? EMPTY[i] : i == EquipMenu.INV_END - 9 ? "무기" : i >= 11 && i <= 14 ? new String[]{"모자", "상의", "하의", "신발"}[i - 11] : null;
            if (n != null) g.renderComponentTooltip(font, java.util.List.of(Component.literal("§6" + n + " 칸"), Component.literal("§7비어 있음")), mx, my);
        }
        renderTooltip(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (EquipMenu.bagOpen) {   // 팝업 밖 누르면 닫기 (들고 있는 게 없을 때)
            int bx = leftPos + EquipMenu.BAG_X - 9, by = topPos + EquipMenu.BAG_Y - 20;
            boolean in = mx >= bx && mx < bx + 180 && my >= by && my < by + 112;
            if (!in && menu.getCarried().isEmpty() && !(mx >= leftPos + 228 && mx < leftPos + 284 && my >= topPos + 156 && my < topPos + 171)) {
                EquipMenu.bagOpen = false; rebuildWidgets(); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int l, int t, int button) {
        return mx < l || my < t || mx >= l + imageWidth || my >= t + imageHeight;
    }
}
