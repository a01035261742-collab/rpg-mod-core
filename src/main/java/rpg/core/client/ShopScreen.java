package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.net.Net;

/** 상점 창: 왼쪽 사기 목록 (클릭 1개 / Shift 10개), 오른쪽 내 인벤 (클릭 1개 팔기 / Shift 한 묶음) */
public class ShopScreen extends Screen {
    private static final int W = 330, ROW = 20, ROWS = 8, CELL = 18;
    private int H = 250, off;   // off = 탭이 2줄 이상이면 내용이 내려가는 만큼
    private final int npc;
    private final String name;
    private final List<ItemStack> offers = new ArrayList<>();
    private final List<Long> prices;
    private final Map<String, Long> sell;
    private int x, y, scroll, tab;
    /** 탭: 무기 · 방어구 · 장신구 부위별 · 기타 (물건이 있는 탭만) */
    private final List<String> tabs = new ArrayList<>();
    private final List<Integer> shown = new ArrayList<>();   // 지금 탭의 offers 번호

    private static final String[][] CATS = {{"weapon", "무기"}, {"armor", "방어구"}, {"gloves", "장갑"}, {"cape", "망토"}, {"belt", "벨트"},
            {"earring", "귀걸이"}, {"ring", "반지"}, {"pendant", "펜던트"}, {"medal", "훈장"}, {"etc", "소모품·기타"}};

    private static String cat(ItemStack s) {
        String slot = rpg.core.equip.Gear.slotOf(s);
        if (slot.isEmpty()) return "etc";
        if (slot.equals("hat") || slot.equals("top") || slot.equals("bottom") || slot.equals("shoes")) return "armor";
        return slot;
    }

    private void filter() {
        shown.clear();
        String c = tabs.isEmpty() ? "" : tabs.get(tab);
        for (int i = 0; i < offers.size(); i++) if (c.isEmpty() || cat(offers.get(i)).equals(c)) shown.add(i);
        scroll = 0;
    }

    public ShopScreen(int npc, String name, List<String> items, List<Long> prices, Map<String, Long> sell) {
        super(Component.literal(name));
        this.npc = npc;
        this.name = name;
        this.prices = prices;
        this.sell = sell;
        for (String id : items) {
            offers.add(rpg.core.item.ItemIds.stack(id, 1));
        }
    }

    public static void open(int npc, String name, List<String> items, List<Long> prices, Map<String, Long> sell) {
        Minecraft.getInstance().setScreen(new ShopScreen(npc, name, items, prices, sell));
    }

    @Override
    protected void init() {
        tabs.clear();
        List<String> labels = new ArrayList<>();
        for (String[] c : CATS) {
            boolean any = false;
            for (ItemStack o : offers) if (cat(o).equals(c[0])) { any = true; break; }
            if (!any) continue;
            tabs.add(c[0]);
            labels.add(c[1]);
        }
        int[][] fl = Ui.flow(font, labels, 0, 0, W - 16, 10, 2, 16);
        off = (Math.max(1, Ui.rows(fl, 16)) - 1) * 16;
        H = 250 + off;
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        for (int i = 0; i < labels.size(); i++) {
            final int k = i;
            addRenderableWidget(Button.builder(Component.literal(labels.get(i)), b -> { tab = k; filter(); rebuildWidgets(); })
                    .bounds(x + 8 + fl[i][0], y + 19 + fl[i][1], fl[i][2], 14).build()).active = tab != i;
        }
        tab = Math.min(tab, Math.max(0, tabs.size() - 1));
        filter();
        addRenderableWidget(Button.builder(Component.literal("재료 전부 팔기"), b ->
                Net.CH.sendToServer(new Net.ShopAction(npc, 2, 0, 0))).bounds(x + W - 156, y + H - Ui.FOOT, 100, Ui.BTN_H).build());
        addRenderableWidget(Ui.close(x, y, W, H, this::onClose));
    }

    private long sellPrice(ItemStack s) {
        if (s.isEmpty()) return 0;
        return sell.getOrDefault(rpg.core.item.ItemIds.sellKey(s), 0L);
    }

    private ItemStack inv(int i) {
        var p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getInventory().items.get(i);
    }

    /** 인벤 칸 i 의 화면 위치: 위 3줄 = 9~35, 아래 1줄 = 핫바 0~8 */
    private int cellX(int i) { return x + 168 + (i % 9) * CELL; }
    private int cellY(int i) { return i < 9 ? y + 50 + off + 3 * CELL + 6 : y + 50 + off + (i / 9 - 1) * CELL; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.frame(g, font, x, y, W, H, 0xFF55FFFF, name, String.format("%,d", ClientData.gold) + " G", Ui.GOLD);
        Ui.footLine(g, x, y, W, H);

        // 사기
        g.drawString(font, "사기", x + 8, y + 38 + off, 0xFFA0A0A0, false);
        List<Component> tip = null;
        for (int r = 0; r < ROWS; r++) {
            if (r + scroll >= shown.size()) break;
            int i = shown.get(r + scroll);
            int ry = y + 50 + off + r * ROW;
            boolean hover = mx >= x + 6 && mx < x + 160 && my >= ry && my < ry + ROW - 2;
            g.fill(x + 6, ry, x + 160, ry + ROW - 2, hover ? 0x60FFFFFF : 0x30FFFFFF);
            ItemStack s = offers.get(i);
            g.renderItem(s, x + 8, ry + 1);
            String nm = s.isEmpty() ? "?" : s.getHoverName().getString();
            if (font.width(nm) > 88) nm = font.plainSubstrByWidth(nm, 84) + "…";
            g.drawString(font, nm, x + 28, ry + 5, 0xFFFFFFFF, false);
            String pr = String.format("%,d", prices.get(i)) + " G";
            boolean afford = ClientData.gold >= prices.get(i);
            g.drawString(font, pr, x + 156 - font.width(pr), ry + 5, afford ? 0xFFFFD24A : 0xFFFF5555, false);
            if (hover && !s.isEmpty()) {
                tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), s));
                tip.add(Component.literal("가격: " + String.format("%,d", prices.get(i)) + " G").withStyle(ChatFormatting.GOLD));
                tip.add(Component.literal(s.getMaxStackSize() > 1 ? "클릭 1개 · Shift+클릭 10개" : "클릭: 구매 (추가 옵션 무작위)").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (shown.size() > ROWS) g.drawString(font, "휠로 스크롤 (" + (scroll + 1) + "-" + Math.min(shown.size(), scroll + ROWS) + "/" + shown.size() + ")",
                x + 8, y + 50 + off + ROWS * ROW + 2, 0xFF808080, false);   // 목록 바로 아래 (겹치지 않게)

        // 팔기
        g.drawString(font, "팔기 (내 인벤)", x + 168, y + 38 + off, 0xFFA0A0A0, false);
        for (int i = 0; i < 36; i++) {
            int cx = cellX(i), cy = cellY(i);
            ItemStack s = inv(i);
            long p = sellPrice(s);
            boolean hover = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL;
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, hover ? 0x80FFFFFF : s.isEmpty() ? 0x30FFFFFF : p > 0 ? 0x5050C050 : 0x50803030);
            if (!s.isEmpty()) {
                g.renderItem(s, cx + 1, cy + 1);
                g.renderItemDecorations(font, s, cx + 1, cy + 1);
                if (hover) {
                    tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), s));
                    if (p > 0) {
                        tip.add(Component.literal("판매가: " + String.format("%,d", p) + " G (묶음 " + String.format("%,d", p * s.getCount()) + " G)").withStyle(ChatFormatting.GOLD));
                        tip.add(Component.literal("클릭 1개 · Shift+클릭 묶음 전부").withStyle(ChatFormatting.DARK_GRAY));
                    } else tip.add(Component.literal("팔 수 없음").withStyle(ChatFormatting.RED));
                }
            }
        }
        g.drawString(font, "초록 = 팔 수 있음", x + 168, y + 50 + off + 4 * CELL + 10, 0xFF60A060, false);
        super.render(g, mx, my, pt);
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        boolean shift = hasShiftDown();
        for (int r = 0; r < ROWS; r++) {
            if (r + scroll >= shown.size()) break;
            int i = shown.get(r + scroll);
            int ry = y + 50 + off + r * ROW;
            if (mx >= x + 6 && mx < x + 160 && my >= ry && my < ry + ROW - 2) {
                Net.CH.sendToServer(new Net.ShopAction(npc, 0, i, shift ? 10 : 1));
                return true;
            }
        }
        for (int i = 0; i < 36; i++) {
            int cx = cellX(i), cy = cellY(i);
            if (mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL) {
                if (!inv(i).isEmpty()) Net.CH.sendToServer(new Net.ShopAction(npc, 1, i, shift ? 1 : 0));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
