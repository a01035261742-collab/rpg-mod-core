package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import rpg.core.equip.EquipMenu;
import rpg.core.equip.PlayerEquip;

/**
 * 장비 탭 (시안 uiA3_equip): 큰 장비 칸 (28) · 캐릭터 · 장신구 · 오른쪽 능력치 전부 + 세트 효과,
 * 캐릭터 창 오른쪽에 붙은 가방 창 (큰 칸 26). 칸은 바닐라 대신 이 화면이 직접 그리고 누름.
 */
public class EquipScreen extends ScaledContainerScreen<EquipMenu> {
    private static final String[] EMPTY = {"", "장갑", "망토", "벨트", "귀걸이", "반지", "반지", "반지", "반지", "펜던트", "훈장"};
    /** 가방 창 폭 · 칸 크기 */
    static final int BW = 258, ES = 28, BS = 26;
    private static final int WEAPON = EquipMenu.INV_END - 9;

    /** 화면 칸: 메뉴 칸 번호 + 위치 + 크기 */
    private record Cell(int idx, int x, int y, int size) {}
    private final List<Cell> cells = new ArrayList<>();
    private int cx, cy;   // 캐릭터 창 왼쪽 위
    private boolean handled;

    @Override public int baseW() { return CharWin.W + 4 + BW; }

    public EquipScreen(EquipMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = CharWin.W + 4 + BW;
        imageHeight = CharWin.H;
        EquipMenu.bagOpen = false;
        EquipMenu.manual = true;
    }

    @Override
    protected void init() {
        super.init();
        EquipMenu.bagOpen = false;
        EquipMenu.manual = true;
        // 캐릭터 창은 다른 탭과 같은 자리 · 가방이 화면에 들어가게만 왼쪽으로
        cx = Math.min(Ui.cx(width, CharWin.W), Math.max(Ui.MARGIN, width - Ui.MARGIN - CharWin.W - 4 - BW));
        cy = Ui.cy(height, CharWin.H);
        leftPos = cx; topPos = cy;
        CharWin.addTabs(this::addRenderableWidget, cx, cy, CharWin.EQUIP);
        addRenderableWidget(Ui.button(Component.literal("정렬"), b -> rpg.core.net.Net.CH.sendToServer(new rpg.core.net.Net.QuickAct(1, "")))
                .bounds(bagX() + 12, cy + 158, 50, 16).build());
        cells.clear();
        int x0 = cx + 86;
        int[] armor = {11, 12, 13, 14};
        for (int i = 0; i < 4; i++) cells.add(new Cell(armor[i], x0, cy + 32 + i * 31, ES));
        int[] right = {1, 2, 3, WEAPON};
        for (int i = 0; i < 4; i++) cells.add(new Cell(right[i], x0 + 136, cy + 32 + i * 31, ES));
        int[] acc = {5, 6, 7, 8, 4, 9, 10};   // 반지 4 · 귀걸이 · 펜던트 · 훈장
        for (int i = 0; i < acc.length; i++) cells.add(new Cell(acc[i], x0 + (i % 5) * 31, cy + 182 + (i / 5) * 31, ES));
        // 가방: 3줄 27칸 + 넷째 줄 핫바 1~8
        int bx = bagX() + 12, by = cy + 44;
        for (int i = 0; i < 27; i++) cells.add(new Cell(16 + i, bx + (i % 9) * BS, by + (i / 9) * BS, BS));
        for (int i = 0; i < 8; i++) cells.add(new Cell(WEAPON + 1 + i, bx + i * BS, by + 3 * BS, BS));
        // 2×2 조합 (가방 오른쪽 아래) → 결과
        int gx = bagX() + BW - 12 - BS - 22 - 2 * BS, gy = cy + CharWin.H - 18 - 2 * BS;
        for (int i = 0; i < 4; i++) cells.add(new Cell(EquipMenu.RESULT + 1 + i, gx + (i % 2) * BS, gy + (i / 2) * BS, BS));
        cells.add(new Cell(EquipMenu.RESULT, bagX() + BW - 12 - BS, gy + BS / 2, BS));
    }

    private int bagX() { return cx + CharWin.W + 4; }

    @Override
    public void removed() {
        super.removed();
        EquipMenu.bagOpen = false;
        EquipMenu.manual = false;
    }

    private Cell at(double mx, double my) {
        for (Cell c : cells) if (mx >= c.x && mx < c.x + c.size && my >= c.y && my < c.y + c.size) return c;
        return null;
    }

    /** 큰 칸 아이템 (1.5배) */
    private void bigItem(GuiGraphics g, ItemStack s, int x, int y, int size) {
        if (s.isEmpty()) return;
        float k = 1.5f;
        int off = (size - 24) / 2;
        g.pose().pushPose();
        g.pose().translate(x + off, y + off, 0);
        g.pose().scale(k, k, 1);
        g.renderItem(s, 0, 0);
        g.renderItemDecorations(font, s, 0, 0);
        g.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = cx, y = cy;
        CharWin.draw(g, font, x, y);
        int x0 = x + 86;
        // 캐릭터
        g.fill(x0 + 34, y + 32, x0 + 130, y + 152, 0xFF12040A);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0 + 82, y + 145, 52, (float) (x0 + 82 - mx), (float) (y + 70 - my), minecraft.player);
        g.drawCenteredString(font, "장신구", x0 + 16, y + 170, Ui.GRAY);
        // 가방 창 (칸보다 먼저 그려야 칸이 보임)
        int bx = bagX();
        Ui.window(g, bx, y, BW, CharWin.H);
        Ui.plaque(g, font, bx + BW / 2, y + 1, "가방");
        g.drawString(font, "Shift+클릭: 입기", bx + 12, y + 30, Ui.GRAY, false);
        int gx = bx + BW - 12 - BS - 22 - 2 * BS, gy = y + CharWin.H - 18 - 2 * BS;
        g.drawString(font, "조합", gx, gy - 11, Ui.GRAY, false);
        g.drawString(font, "▶", gx + 2 * BS + 7, gy + BS - 4, Ui.G4, false);
        // 칸
        Cell hov = at(mx, my);
        for (Cell c : cells) {
            Ui.slot(g, c.x, c.y, c.size);
            ItemStack s = menu.slots.get(c.idx).getItem();
            if (s.isEmpty() && c.idx >= 1 && c.idx < EMPTY.length) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                g.blit(new ResourceLocation("rpgcore", "textures/gui/slot/" + PlayerEquip.SLOT_TYPES[c.idx] + ".png"), c.x + (c.size - 16) / 2, c.y + (c.size - 16) / 2, 0, 0, 16, 16, 16, 16);
            }
            bigItem(g, s, c.x, c.y, c.size);
            if (c == hov) g.fill(c.x + 2, c.y + 2, c.x + c.size - 2, c.y + c.size - 2, 0x40FFFFFF);
        }
        // 오른쪽: 능력치 전부 + 세트 효과
        int rx = x + 261, rr = x + CharWin.W - 20, ry = y + 34;
        Ui.vsep(g, rx - 8, y + 30, y + CharWin.H - 18);
        g.drawString(font, "Lv." + ClientData.level + " " + ClientData.jobName(), rx, ry, Ui.VAL, true);
        Ui.hsep(g, rx, rr, ry + 12);
        ry += 17;
        ry = line(g, rx, rr, ry, ClientData.job == 3 ? "마력 스공" : "스공", String.format("%,.0f~%,.0f", ClientData.atkMin, ClientData.atkMax));
        ry = line(g, rx, rr, ry, "최대 HP", String.format("%,.0f", ClientData.maxHp));
        ry = line(g, rx, rr, ry, "최대 MP", String.format("%,.0f", ClientData.maxMp));
        ry = line(g, rx, rr, ry, "방어력", String.format("%,.0f", ClientData.defPct));
        ry = line(g, rx, rr, ry, "치명타 확률", String.format("%.1f%%", ClientData.crit));
        ry = line(g, rx, rr, ry, "치명타 피해", String.format("%.0f%%", ClientData.critDmgPct));
        ry = line(g, rx, rr, ry, "보스 데미지", String.format("+%.0f%%", ClientData.bossPct));
        ry = line(g, rx, rr, ry, "방어율 무시", String.format("%.1f%%", ClientData.ignoreDef));
        ry = line(g, rx, rr, ry, "회복량", String.format("+%.1f%%", ClientData.healBonus));
        ry = line(g, rx, rr, ry, "드롭률", String.format("+%.1f%%", ClientData.dropBonus));
        ry = line(g, rx, rr, ry, "골드 획득", String.format("+%.1f%%", ClientData.goldBonus));
        ry += 2;
        Ui.hsep(g, rx, rr, ry);
        g.drawString(font, "세트 효과", rx, ry + 5, Ui.GRAY, false);
        setLines(g, rx, rr, ry + 18);
    }

    /** 입은 장비로 세트 효과 (켜진 단계 초록, 다음 단계 회색) */
    private void setLines(GuiGraphics g, int rx, int rr, int yy) {
        java.util.Set<String> worn = new java.util.HashSet<>();
        for (Cell c : cells) {
            if (c.idx >= 16 && c.idx != WEAPON && c.idx < WEAPON) continue;
            if (c.idx > WEAPON) continue;
            var d = rpg.core.equip.Gear.def(menu.slots.get(c.idx).getItem());
            if (d != null) worn.add(d.id());
        }
        int shown = 0;
        for (var s : rpg.core.equip.Sets.SETS.values()) {
            int n = rpg.core.equip.Sets.count(s, worn);
            if (n < 1) continue;
            for (var e : s.bonus().entrySet()) {
                if (shown >= 4) return;
                boolean on = n >= e.getKey();
                String t = s.name() + " " + e.getKey() + "세트: " + rpg.core.equip.Sets.text(e.getValue());
                g.drawString(font, Ui.cut(font, t, rr - rx), rx, yy + shown * 11, on ? Ui.GREEN : Ui.GRAY, false);
                shown++;
            }
        }
        if (shown == 0) g.drawString(font, "없음", rx, yy, Ui.GRAY, false);
    }

    private int line(GuiGraphics g, int rx, int rr, int ry, String k, String v) {
        g.drawString(font, k, rx, ry, Ui.TXT, false);
        g.drawString(font, v, rr - font.width(v), ry, Ui.VAL, true);
        return ry + 13;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        RpgHud.buffBar(g, font, width, mx, my);
        Cell c = at(mx, my);
        if (c != null && menu.getCarried().isEmpty()) {
            ItemStack s = menu.slots.get(c.idx).getItem();
            if (!s.isEmpty()) g.renderComponentTooltip(font, Screen.getTooltipFromItem(minecraft, s), mx, my);
            else {
                String n = c.idx > 0 && c.idx < EMPTY.length ? EMPTY[c.idx] : c.idx == WEAPON ? "무기" : c.idx >= 11 && c.idx <= 14 ? new String[]{"모자", "상의", "하의", "신발"}[c.idx - 11] : null;
                if (n != null) g.renderComponentTooltip(font, List.of(Component.literal("§6" + n + " 칸"), Component.literal("§7비어 있음")), mx, my);
            }
        }
        renderTooltip(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Cell c = at(mx, my);
        if (c != null && (button == 0 || button == 1)) {
            slotClicked(menu.slots.get(c.idx), c.idx, button, hasShiftDown() ? ClickType.QUICK_MOVE : ClickType.PICKUP);
            handled = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (handled) { handled = false; return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (handled) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int l, int t, int button) {
        boolean inChar = mx >= cx && mx < cx + CharWin.W && my >= cy && my < cy + CharWin.H;
        boolean inBag = mx >= bagX() && mx < bagX() + BW && my >= cy && my < cy + CharWin.H;
        return !inChar && !inBag;
    }

    /** RPG 월드: 숫자키로 핫바(무기 칸)와 바꾸기 막음 (1~4 = 물약) */
    @Override
    protected boolean checkHotbarKeyPressed(int key, int scan) {
        return !ClientRpg.inRpg() && super.checkHotbarKeyPressed(key, scan);
    }
}
