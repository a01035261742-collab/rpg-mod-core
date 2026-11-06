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
public class BagScreen extends ScaledContainerScreen<EquipMenu> {
    /** 다음에 열리는 EquipMenu 화면을 가방으로 (인벤토리 키) */
    public static boolean wantBag;
    private static final int WW = 430, WH = 176, BS = 24, WEAPON = EquipMenu.INV_END - 9;
    private int wx, wy;
    private boolean handled;
    private record Cell(int idx, int x, int y) {}
    private final java.util.List<Cell> cells = new java.util.ArrayList<>();

    @Override public int baseW() { return WW; }
    @Override public int baseH() { return WH; }

    public BagScreen(EquipMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = WW;
        imageHeight = WH;
        EquipMenu.bagOpen = true;
        EquipMenu.manual = true;
    }

    @Override
    protected void init() {
        super.init();
        wx = Ui.cx(width, WW);
        wy = Ui.cy(height, WH);
        leftPos = wx; topPos = wy;
        EquipMenu.bagOpen = true;
        EquipMenu.manual = true;
        ClientPotions.load();
        addRenderableWidget(Ui.button(Component.literal("정렬"), b -> Net.CH.sendToServer(new Net.QuickAct(1, "")))
                .bounds(wx + 12, wy + 148, 50, 16).build());
        addRenderableWidget(Ui.button(Component.literal("장비 ▶"), b -> CharWin.open(CharWin.EQUIP))
                .bounds(wx + 68, wy + 148, 60, 16).accent(true).build());
        cells.clear();
        for (int i = 0; i < 27; i++) cells.add(new Cell(16 + i, wx + 12 + (i % 9) * BS, wy + 40 + (i / 9) * BS));
        for (int i = 0; i < 8; i++) cells.add(new Cell(WEAPON + 1 + i, wx + 12 + i * BS, wy + 40 + 3 * BS));
    }

    @Override
    public void removed() {
        super.removed();
        EquipMenu.bagOpen = false;
        EquipMenu.manual = false;
    }

    private int px(int k) { return wx + 240; }
    private int py(int k) { return wy + 40 + k * BS; }

    private double lmx, lmy;
    private Cell hoverCell() { return at(lmx, lmy); }

    @Override
    public void mouseMoved(double mx, double my) { super.mouseMoved(mx, my); lmx = mx; lmy = my; }

    private Cell at(double mx, double my) {
        for (Cell c : cells) if (mx >= c.x && mx < c.x + BS && my >= c.y && my < c.y + BS) return c;
        return null;
    }

    private void big(GuiGraphics g, ItemStack s, int x, int y) {
        if (s.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.renderItem(s, 0, 0);
        g.renderItemDecorations(font, s, 0, 0);
        g.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        Ui.window(g, wx, wy, WW, WH);
        Ui.plaque(g, font, wx + WW / 2, wy + 1, "가방");
        Ui.goldBox(g, font, wx + WW - 14, wy + 4, String.format("%,d G", ClientData.gold), true);
        int used = 0;
        Cell hov = at(mx, my);
        for (Cell c : cells) {
            ItemStack s = menu.slots.get(c.idx).getItem();
            if (!s.isEmpty()) used++;
            Ui.slot(g, c.x, c.y, BS);
            big(g, s, c.x, c.y);
            if (c == hov) g.fill(c.x + 2, c.y + 2, c.x + BS - 2, c.y + BS - 2, 0x40FFFFFF);
        }
        g.drawString(font, "가방", wx + 12, wy + 27, Ui.VAL, true);
        String n = used + " / 35";
        g.drawString(font, n, wx + 228 - font.width(n), wy + 27, Ui.GRAY, false);
        g.drawString(font, "물약", wx + 240, wy + 27, Ui.VAL, true);
        for (int k = 0; k < 4; k++) {
            int x = px(k), y = py(k);
            Ui.slot(g, x, y, BS);
            var it = ClientPotions.item(k);
            if (it != null) {
                big(g, new ItemStack(it), x, y);
                int c = ClientPotions.count(k);
                String cs = String.valueOf(c);
                g.pose().pushPose(); g.pose().translate(0, 0, 300);
                g.drawString(font, cs, x + BS - 1 - font.width(cs), y + BS - 9, c > 0 ? 0xFFFFFFFF : Ui.RED, true);
                g.pose().popPose();
            }
            g.pose().pushPose(); g.pose().translate(0, 0, 300);
            g.drawString(font, String.valueOf(k + 1), x + 2, y + 2, Ui.VAL, true);
            g.pose().popPose();
        }
        Ui.vsep(g, wx + 272, wy + 26, wy + WH - 16);
        buffPanel(g, -1, -1);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        for (int k = 0; k < 4; k++) {
            int x = px(k), y = py(k);
            if (mx >= x && mx < x + BS && my >= y && my < y + BS) {
                var it = ClientPotions.item(k);
                g.renderComponentTooltip(font, it == null
                        ? List.of(Component.literal("§6물약 칸 " + (k + 1)), Component.literal("§7가방의 물약을 끌어다 놓기"))
                        : List.of(Component.literal("§6" + new ItemStack(it).getHoverName().getString()), Component.literal("§7" + (k + 1) + " 키: 마시기 · 가진 개수 " + ClientPotions.count(k)), Component.literal("§8우클릭: 칸 비우기")), mx, my);
            }
        }
        buffPanel(g, mx, my);
        Cell hc = at(mx, my);
        if (hc != null && menu.getCarried().isEmpty() && !menu.slots.get(hc.idx).getItem().isEmpty())
            g.renderComponentTooltip(font, net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft, menu.slots.get(hc.idx).getItem()), mx, my);
        renderTooltip(g, mx, my);
    }

    // ───────── 오른쪽: 버프·디버프 목록 (마우스 올리면 자세히) ─────────
    private record Row(String name, String time, int color, boolean bad, java.util.List<Component> tip) {}

    private java.util.List<Row> rows() {
        java.util.List<Row> out = new java.util.ArrayList<>();
        String[] kinds = {"스킬 버프", "파티원 버프", "패시브 트리", "재사용 대기"};
        int[] cols = {0xFF5AC8FF, 0xFF6EDC6E, 0xFFFFC83C, 0xFF8C8C8C};
        for (ClientData.Buff b : ClientData.BUFFS) {
            boolean inf = b.left() >= 900_000_000;
            int left = inf ? 0 : ClientData.left(b.left());
            if (!inf && left <= 0) continue;
            int k = Math.max(0, Math.min(3, b.kind()));
            String t = inf ? "∞" : left >= 1200 ? (left / 1200) + "분" : (left + 19) / 20 + "초";
            java.util.List<Component> tip = new java.util.ArrayList<>();
            tip.add(Component.literal(b.name()).withStyle(st -> st.withColor(cols[k] & 0xFFFFFF)));
            tip.add(Component.literal("§7" + kinds[k] + (b.extra().isEmpty() ? "" : "  §f" + b.extra())));
            var sd = rpg.core.skill.Defs.SKILLS.values().stream().filter(d -> d.name().equals(b.name())).findFirst().orElse(null);
            if (sd != null) {
                int lv = Math.max(1, ClientData.skills.getOrDefault(sd.id(), 1));
                for (var line : font.getSplitter().splitLines(sd.describe(lv), 180, net.minecraft.network.chat.Style.EMPTY)) tip.add(Component.literal("§f" + line.getString()));
            }
            tip.add(Component.literal("§7남은 시간 " + (inf ? "계속" : t)));
            out.add(new Row(b.name(), t, cols[k], k == 3, tip));
        }
        var p = minecraft.player;
        if (p != null) for (var e : p.getActiveEffects()) {   // 마크 효과 (나쁜 것 = 디버프)
            if (!e.isVisible() && !e.showIcon()) continue;
            boolean bad = e.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL;
            String nm = e.getEffect().getDisplayName().getString() + (e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "");
            int d = e.getDuration();
            String t = e.isInfiniteDuration() ? "∞" : d >= 1200 ? (d / 1200) + "분" : (d + 19) / 20 + "초";
            out.add(new Row(nm, t, bad ? 0xFFFF6060 : 0xFF7CE07C, bad, java.util.List.of(
                    Component.literal((bad ? "§c" : "§a") + nm), Component.literal("§7" + (bad ? "디버프" : "효과") + " · 남은 " + t))));
        }
        return out;
    }

    /** mx < 0 = 그리기만, 아니면 툴팁만 */
    private void buffPanel(GuiGraphics g, int mx, int my) {
        int x0 = wx + 280, x1 = wx + WW - 14, y = wy + 27;
        var list = rows();
        if (mx < 0) {
            g.drawString(font, "버프", x0, y, Ui.VAL, true);
            String c = list.size() + "개";
            g.drawString(font, c, x1 - font.width(c), y, Ui.GRAY, false);
            if (list.isEmpty()) g.drawString(font, "없음", x0 + 2, y + 14, Ui.GRAY, false);
        }
        int max = 7;
        for (int i = 0; i < Math.min(max, list.size()); i++) {
            Row r = list.get(i);
            int ry = y + 12 + i * 16;
            boolean on = mx >= x0 && mx < x1 && my >= ry && my < ry + 15;
            if (mx < 0) {
                Ui.row(g, x0, ry, x1, ry + 15, false);
                if (r.bad()) g.fill(x0, ry, x0 + 2, ry + 15, 0xFFDC3232);
                g.drawString(font, Ui.cut(font, r.name(), x1 - x0 - 8 - font.width(r.time())), x0 + 4, ry + 4, r.color(), true);
                g.drawString(font, r.time(), x1 - 2 - font.width(r.time()), ry + 4, r.bad() ? Ui.RED : Ui.GRAY, false);
            } else if (on) g.renderComponentTooltip(font, r.tip(), mx, my);
        }
        if (mx < 0 && list.size() > max) g.drawString(font, "+" + (list.size() - max) + "개 더", x0 + 2, y + 12 + max * 16 + 2, Ui.GRAY, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int k = 0; k < 4; k++) {
            int x = px(k), y = py(k);
            if (mx >= x && mx < x + BS && my >= y && my < y + BS) {
                ItemStack c = menu.getCarried();
                if (button == 1) ClientPotions.set(k, ItemStack.EMPTY);
                else if (ClientPotions.fits(c)) ClientPotions.set(k, c);
                return true;
            }
        }
        Cell cl = at(mx, my);
        if (cl != null && (button == 0 || button == 1)) {
            slotClicked(menu.slots.get(cl.idx), cl.idx, button, hasShiftDown() ? net.minecraft.world.inventory.ClickType.QUICK_MOVE : net.minecraft.world.inventory.ClickType.PICKUP);
            handled = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (handled) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (handled) {   // 끌어서 물약 칸에 놓기는 아래에서
            handled = false;
            for (int k = 0; k < 4; k++) {
                int x = px(k), y = py(k);
                if (mx >= x && mx < x + BS && my >= y && my < y + BS && ClientPotions.fits(menu.getCarried())) { ClientPotions.set(k, menu.getCarried()); return true; }
            }
            return true;
        }
        for (int k = 0; k < 4; k++) {   // 끌어서 놓기
            int x = px(k), y = py(k);
            if (mx >= x && mx < x + BS && my >= y && my < y + BS && ClientPotions.fits(menu.getCarried())) { ClientPotions.set(k, menu.getCarried()); return true; }
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
            if (minecraft.options.keyHotbarSlots[k].matches(key, scan) && hoverCell() != null && ClientPotions.fits(menu.slots.get(hoverCell().idx).getItem())) {
                ClientPotions.set(k, menu.slots.get(hoverCell().idx).getItem());
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
