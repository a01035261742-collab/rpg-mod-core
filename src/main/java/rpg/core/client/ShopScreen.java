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
    private static final int W = 330, H = 200, ROW = 20, ROWS = 8, CELL = 18;
    private final int npc;
    private final String name;
    private final List<ItemStack> offers = new ArrayList<>();
    private final List<Long> prices;
    private final Map<String, Long> sell;
    private int x, y, scroll;

    public ShopScreen(int npc, String name, List<String> items, List<Long> prices, Map<String, Long> sell) {
        super(Component.literal(name));
        this.npc = npc;
        this.name = name;
        this.prices = prices;
        this.sell = sell;
        for (String id : items) {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            offers.add(it == null ? ItemStack.EMPTY : new ItemStack(it));
        }
    }

    public static void open(int npc, String name, List<String> items, List<Long> prices, Map<String, Long> sell) {
        Minecraft.getInstance().setScreen(new ShopScreen(npc, name, items, prices, sell));
    }

    @Override
    protected void init() {
        x = width / 2 - W / 2;
        y = height / 2 - H / 2;
        addRenderableWidget(Button.builder(Component.literal("재료 전부 팔기"), b ->
                Net.CH.sendToServer(new Net.ShopAction(npc, 2, 0, 0))).bounds(x + 168, y + H - 26, 100, 18).build());
        addRenderableWidget(Button.builder(Component.literal("닫기"), b -> onClose()).bounds(x + W - 56, y + H - 26, 48, 18).build());
    }

    private long sellPrice(ItemStack s) {
        if (s.isEmpty()) return 0;
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(s.getItem());
        return rl == null ? 0 : sell.getOrDefault(rl.toString(), 0L);
    }

    private ItemStack inv(int i) {
        var p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getInventory().items.get(i);
    }

    /** 인벤 칸 i 의 화면 위치: 위 3줄 = 9~35, 아래 1줄 = 핫바 0~8 */
    private int cellX(int i) { return x + 168 + (i % 9) * CELL; }
    private int cellY(int i) { return i < 9 ? y + 34 + 3 * CELL + 6 : y + 34 + (i / 9 - 1) * CELL; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x, y, x + W, y + H, 0xF0141420);
        g.fill(x, y, x + W, y + 1, 0xFF55FFFF);
        g.drawString(font, name, x + 8, y + 7, 0xFF55FFFF, true);
        String gold = String.format("%,d", ClientData.gold) + " G";
        g.drawString(font, gold, x + W - 8 - font.width(gold), y + 7, 0xFFFFD24A, true);

        // 사기
        g.drawString(font, "사기", x + 8, y + 22, 0xFFA0A0A0, false);
        List<Component> tip = null;
        for (int r = 0; r < ROWS; r++) {
            int i = r + scroll;
            if (i >= offers.size()) break;
            int ry = y + 34 + r * ROW;
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
        if (offers.size() > ROWS) g.drawString(font, "휠로 스크롤 (" + (scroll + 1) + "-" + Math.min(offers.size(), scroll + ROWS) + "/" + offers.size() + ")",
                x + 8, y + H - 20, 0xFF808080, false);

        // 팔기
        g.drawString(font, "팔기 (내 인벤)", x + 168, y + 22, 0xFFA0A0A0, false);
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
        g.drawString(font, "초록 = 팔 수 있음", x + 168, y + 34 + 4 * CELL + 10, 0xFF60A060, false);
        super.render(g, mx, my, pt);
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        boolean shift = hasShiftDown();
        for (int r = 0; r < ROWS; r++) {
            int i = r + scroll;
            if (i >= offers.size()) break;
            int ry = y + 34 + r * ROW;
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
        scroll = Math.max(0, Math.min(Math.max(0, offers.size() - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
