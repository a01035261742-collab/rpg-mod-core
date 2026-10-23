package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
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
import rpg.core.equip.Gear;
import rpg.core.net.Net;

/** 제작 창: 위 탭 · 왼쪽 레시피 목록 · 오른쪽 재료(가진 수/필요 수)·골드·[제작] */
public class CraftScreen extends Screen {
    private static final int W = 340, ROW = 20, ROWS = 8;
    private int H = 260, off;
    private final int npc;
    private final String name;
    private final List<String> tabs;
    private final List<Net.CraftLine> lines;
    private final List<Integer> shown = new ArrayList<>();   // 지금 탭의 레시피 번호
    private int x, y, scroll, tab, sel = -1;
    private Button craft1, craft10;

    public CraftScreen(int npc, String name, List<String> tabs, List<Net.CraftLine> lines) {
        super(Component.literal(name));
        this.npc = npc;
        this.name = name;
        this.tabs = tabs.isEmpty() ? List.of("") : tabs;
        this.lines = lines;
    }

    public static void open(int npc, String name, List<String> tabs, List<Net.CraftLine> lines) {
        Minecraft.getInstance().setScreen(new CraftScreen(npc, name, tabs, lines));
    }

    private static ItemStack stack(String id, int n) {
        return rpg.core.item.ItemIds.stack(id, n);
    }

    private void filter() {
        shown.clear();
        String t = tabs.get(tab);
        for (int i = 0; i < lines.size(); i++) if (t.isEmpty() || lines.get(i).tab().equals(t)) shown.add(i);
        scroll = 0;
        sel = shown.isEmpty() ? -1 : shown.get(0);
    }

    @Override
    protected void init() {
        List<String> labels = new ArrayList<>();
        for (String t : tabs) labels.add(t.isEmpty() ? "전체" : t);
        int[][] fl = Ui.flow(font, labels, 0, 0, W - 16, 14, 3, 18);
        off = (Math.max(1, Ui.rows(fl, 18)) - 1) * 18;
        H = 260 + off;
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        for (int i = 0; i < labels.size(); i++) {
            final int k = i;
            addRenderableWidget(Ui.button(Component.literal(labels.get(i)), b -> { tab = k; filter(); rebuildWidgets(); })
                    .bounds(x + 8 + fl[i][0], y + 20 + fl[i][1], fl[i][2], 16).build()).active = tab != i;
        }
        // 제작 버튼: 오른쪽 칸 아래 (버튼줄 위) · [닫기]: 버튼줄
        int cy = y + H - Ui.FOOT - 26;
        craft1 = addRenderableWidget(Ui.button(Component.literal("제작"), b -> send(1)).bounds(x + 182, cy, 70, Ui.BTN_H).build());
        craft10 = addRenderableWidget(Ui.button(Component.literal("10번 제작"), b -> send(10)).bounds(x + 256, cy, 76, Ui.BTN_H).build());
        addRenderableWidget(Ui.close(x, y, W, H, this::onClose));
        if (shown.isEmpty() && sel < 0) filter();
    }

    private void send(int times) {
        if (sel >= 0) Net.CH.sendToServer(new Net.CraftAction(npc, sel, times));
    }

    private int have(String id) {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        Item it = rpg.core.item.ItemIds.item(id);
        if (it == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
        return n;
    }

    private boolean canMake(Net.CraftLine l) {
        for (int i = 0; i < l.mats().size(); i++) if (have(l.mats().get(i)) < l.counts().get(i)) return false;
        return ClientData.gold >= l.gold();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.frame(g, font, x, y, W, H, 0xFFFFAA30, name, String.format("%,d", ClientData.gold) + " G", Ui.GOLD);
        Ui.footLine(g, x, y, W, H);

        List<Component> tip = null;
        // 목록
        int top = y + 42 + off;
        for (int r = 0; r < ROWS; r++) {
            int k = r + scroll;
            if (k >= shown.size()) break;
            int i = shown.get(k);
            Net.CraftLine l = lines.get(i);
            int ry = top + r * ROW;
            boolean hover = mx >= x + 6 && mx < x + 174 && my >= ry && my < ry + ROW - 2;
            g.fill(x + 6, ry, x + 174, ry + ROW - 2, i == sel ? 0x70FFAA30 : hover ? 0x50FFFFFF : 0x28FFFFFF);
            ItemStack s = stack(l.result(), l.count());
            g.renderItem(s, x + 8, ry + 1);
            String nm = s.isEmpty() ? l.result() : s.getHoverName().getString() + (l.count() > 1 ? " x" + l.count() : "");
            if (font.width(nm) > 140) nm = font.plainSubstrByWidth(nm, 136) + "…";
            g.drawString(font, nm, x + 28, ry + 5, canMake(l) ? 0xFFFFFFFF : 0xFF909090, false);
            if (hover && !s.isEmpty()) tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), s));
        }
        if (shown.size() > ROWS) g.drawString(font, "휠로 스크롤 (" + (scroll + 1) + "-" + Math.min(shown.size(), scroll + ROWS) + "/" + shown.size() + ")",
                x + 8, top + ROWS * ROW + 2, 0xFF808080, false);

        // 오른쪽: 고른 레시피
        int rx = x + 182;
        boolean ok = false;
        if (sel >= 0) {
            Net.CraftLine l = lines.get(sel);
            ItemStack s = stack(l.result(), l.count());
            g.fill(rx - 2, top, x + W - 6, top + 22, 0x30FFFFFF);
            g.renderItem(s, rx, top + 3);
            String nm = s.getHoverName().getString() + (l.count() > 1 ? " x" + l.count() : "");
            if (font.width(nm) > 130) nm = font.plainSubstrByWidth(nm, 126) + "…";
            g.drawString(font, nm, rx + 20, top + 7, 0xFFFFFFFF, true);
            if (mx >= rx - 2 && mx < x + W - 6 && my >= top && my < top + 22 && !s.isEmpty()) {
                tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), s));
                if (Gear.def(s) != null) tip.add(Component.literal("제작 장비: 추가 옵션 4줄 (종류·수치 무작위)").withStyle(ChatFormatting.AQUA));
            }
            g.drawString(font, "재료", rx, top + 28, 0xFFA0A0A0, false);
            int ly = top + 40;
            for (int i = 0; i < l.mats().size(); i++) {
                ItemStack m = stack(l.mats().get(i), 1);
                int h = have(l.mats().get(i)), need = l.counts().get(i);
                g.renderItem(m, rx, ly - 4);
                String mn = m.isEmpty() ? l.mats().get(i) : m.getHoverName().getString();
                if (font.width(mn) > 84) mn = font.plainSubstrByWidth(mn, 80) + "…";
                g.drawString(font, mn, rx + 20, ly, 0xFFDDDDDD, false);
                String c = h + " / " + need;
                g.drawString(font, c, x + W - 10 - font.width(c), ly, h >= need ? 0xFF70E070 : 0xFFFF6060, false);
                if (mx >= rx && mx < x + W - 6 && my >= ly - 4 && my < ly + 12 && !m.isEmpty())
                    tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), m));
                ly += 18;
            }
            if (l.gold() > 0) {
                String gc = String.format("%,d", l.gold()) + " G";
                g.drawString(font, "골드", rx + 20, ly, 0xFFDDDDDD, false);
                g.drawString(font, gc, x + W - 10 - font.width(gc), ly, ClientData.gold >= l.gold() ? 0xFFFFD24A : 0xFFFF6060, false);
            }
            ok = canMake(l);
            craft10.visible = Gear.def(s) == null;
        } else {
            g.drawString(font, "만들 수 있는 게 없어요", rx, top + 8, 0xFF909090, false);
            craft10.visible = false;
        }
        craft1.active = ok;
        craft10.active = ok;
        super.render(g, mx, my, pt);
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int top = y + 42 + off;
        for (int r = 0; r < ROWS; r++) {
            int k = r + scroll;
            if (k >= shown.size()) break;
            int ry = top + r * ROW;
            if (mx >= x + 6 && mx < x + 174 && my >= ry && my < ry + ROW - 2) {
                sel = shown.get(k);
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
