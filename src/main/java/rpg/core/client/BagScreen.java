package rpg.core.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import rpg.core.equip.EquipMenu;
import rpg.core.net.Net;

/**
 * 가방 창 (Tab, 시안 ui_bag_menu A1): 가방 35칸 + 물약 칸 4개 (끌어다 놓기, 우클릭 = 비우기) + [정렬] [메뉴].
 * 장비 창과 같은 메뉴(EquipMenu)를 가방 칸만 보이게 씀. 핫바 0번 = 무기 칸이라 여기선 안 보임.
 */
public class BagScreen extends AbstractContainerScreen<EquipMenu> {
    /** 다음에 열리는 EquipMenu 화면을 가방으로 (인벤토리 키) */
    public static boolean wantBag;
    private static final int WW = 232, WH = 146;
    private int wx, wy;

    public BagScreen(EquipMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = WW;
        imageHeight = WH;
        EquipMenu.bagOpen = true;
    }

    @Override
    protected void init() {
        super.init();
        wx = (width - WW) / 2;
        wy = (height - WH) / 2;
        leftPos = wx + 20 - EquipMenu.BAG_X;   // 가방 칸이 창 안 (20, 40) 에 오게
        topPos = wy + 40 - EquipMenu.BAG_Y;
        EquipMenu.bagOpen = true;
        ClientPotions.load();
        addRenderableWidget(Ui.button(Component.literal("정렬"), b -> Net.CH.sendToServer(new Net.QuickAct(1, "")))
                .bounds(wx + 20, wy + 122, 50, 15).build());
        addRenderableWidget(Ui.button(Component.literal("메뉴"), b -> minecraft.setScreen(new MenuScreen()))
                .bounds(wx + WW - 70, wy + 122, 52, 15).accent(true).build());
    }

    @Override
    public void removed() {
        super.removed();
        EquipMenu.bagOpen = false;
    }

    private int px(int k) { return wx + 196; }
    private int py(int k) { return wy + 39 + k * 19; }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        Ui.window(g, wx, wy, WW, WH);
        Ui.plaque(g, font, wx + WW / 2, wy + 1, "가방");
        Ui.goldBox(g, font, wx + WW - 12, wy + 4, String.format("%,d G", ClientData.gold), true);
        int used = 0;
        for (var s : menu.slots) if (s.isActive() && s.x > -1000 && s.hasItem()) used++;
        g.drawString(font, "가방", wx + 20, wy + 27, Ui.VAL, true);
        String n = used + " / 35";
        g.drawString(font, n, wx + 182 - font.width(n), wy + 27, Ui.GRAY, false);
        for (var s : menu.slots) if (s.isActive() && s.x > -1000) Ui.slot(g, leftPos + s.x - 1, topPos + s.y - 1);
        Ui.vsep(g, wx + 188, wy + 26, wy + 116);
        g.drawString(font, "물약", wx + 196, wy + 27, Ui.VAL, true);
        for (int k = 0; k < 4; k++) {
            int x = px(k), y = py(k);
            Ui.slot(g, x, y);
            var it = ClientPotions.item(k);
            if (it != null) {
                ItemStack st = new ItemStack(it);
                g.renderItem(st, x + 1, y + 1);
                int c = ClientPotions.count(k);
                String cs = String.valueOf(c);
                g.pose().pushPose(); g.pose().translate(0, 0, 200);
                g.drawString(font, cs, x + 18 - font.width(cs), y + 10, c > 0 ? 0xFFFFFFFF : Ui.RED, true);
                g.pose().popPose();
            }
            g.pose().pushPose(); g.pose().translate(0, 0, 200);
            g.drawString(font, String.valueOf(k + 1), x + 1, y + 1, Ui.VAL, true);
            g.pose().popPose();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        for (int k = 0; k < 4; k++) {
            int x = px(k), y = py(k);
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                var it = ClientPotions.item(k);
                g.renderComponentTooltip(font, it == null
                        ? List.of(Component.literal("§6물약 칸 " + (k + 1)), Component.literal("§7가방의 물약을 끌어다 놓기"))
                        : List.of(Component.literal("§6" + new ItemStack(it).getHoverName().getString()), Component.literal("§7" + (k + 1) + " 키: 마시기 · 가진 개수 " + ClientPotions.count(k)), Component.literal("§8우클릭: 칸 비우기")), mx, my);
            }
        }
        renderTooltip(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int k = 0; k < 4; k++) {
            int x = px(k), y = py(k);
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18) {
                ItemStack c = menu.getCarried();
                if (button == 1) ClientPotions.set(k, ItemStack.EMPTY);
                else if (ClientPotions.fits(c)) ClientPotions.set(k, c);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        for (int k = 0; k < 4; k++) {   // 끌어서 놓기
            int x = px(k), y = py(k);
            if (mx >= x && mx < x + 18 && my >= y && my < y + 18 && ClientPotions.fits(menu.getCarried())) { ClientPotions.set(k, menu.getCarried()); return true; }
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int l, int t, int button) {
        return mx < wx || my < wy || mx >= wx + WW || my >= wy + WH;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (minecraft.options.keyInventory.matches(key, scan)) { onClose(); return true; }
        // 물약에 마우스 올리고 1~4 = 그 물약 칸에 넣기
        for (int k = 0; k < 4; k++)
            if (minecraft.options.keyHotbarSlots[k].matches(key, scan) && hoveredSlot != null && ClientPotions.fits(hoveredSlot.getItem())) {
                ClientPotions.set(k, hoveredSlot.getItem());
                return true;
            }
        return super.keyPressed(key, scan, mods);
    }

    /** RPG 월드: 숫자키로 핫바(무기 칸)와 바꾸기 막음 (1~4 = 물약) */
    @Override
    protected boolean checkHotbarKeyPressed(int key, int scan) {
        return !ClientRpg.inRpg() && super.checkHotbarKeyPressed(key, scan);
    }
}
