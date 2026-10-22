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
import net.minecraft.world.item.ItemStack;
import rpg.core.equip.Enhance;
import rpg.core.equip.Gear;
import rpg.core.equip.GearDefs;
import rpg.core.item.Scroll;
import rpg.core.net.Net;

/** 강화 창 (메뉴 M → 강화): 왼쪽 장비 목록, 위 탭 [스타포스] [주문서] [조각 합성] */
public class EnhanceScreen extends Screen {
    private static final int W = 340, H = 236, ROW = 20, ROWS = 8, CELL = 18;
    private List<Integer> locs;
    private List<ItemStack> gear;
    private List<Integer> slots;
    private List<ItemStack> scrolls;
    private int frags;
    private int x, y, scroll, tab, sel = -1, selScroll = -1;
    private boolean guard;
    private static int lastTab;

    private EnhanceScreen(List<Integer> locs, List<ItemStack> gear, List<Integer> slots, List<ItemStack> scrolls, int frags) {
        super(Component.literal("장비 강화"));
        this.tab = lastTab;
        set(locs, gear, slots, scrolls, frags);
        if (!gear.isEmpty()) sel = 0;
    }

    private void set(List<Integer> locs, List<ItemStack> gear, List<Integer> slots, List<ItemStack> scrolls, int frags) {
        this.locs = locs;
        this.gear = gear;
        this.slots = slots;
        this.scrolls = scrolls;
        this.frags = frags;
    }

    /** 서버가 보낸 내용 받기: open 이면 새 창, 아니면 열린 창만 갱신 (선택 유지) */
    public static void receive(boolean open, List<Integer> locs, List<ItemStack> gear, List<Integer> slots, List<ItemStack> scrolls, int frags) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EnhanceScreen es) {
            int keepLoc = es.sel >= 0 && es.sel < es.locs.size() ? es.locs.get(es.sel) : -1;
            int keepSlot = es.selScroll >= 0 && es.selScroll < es.slots.size() ? es.slots.get(es.selScroll) : -1;
            es.set(locs, gear, slots, scrolls, frags);
            es.sel = locs.indexOf(keepLoc);
            if (es.sel < 0 && !gear.isEmpty()) es.sel = 0;
            es.selScroll = slots.indexOf(keepSlot);
            es.rebuildWidgets();
        } else if (open) {
            mc.setScreen(new EnhanceScreen(locs, gear, slots, scrolls, frags));
        }
    }

    public static void request() {
        Net.CH.sendToServer(new Net.EnhanceAction(0, 0, 0));
    }

    private ItemStack cur() {
        return sel >= 0 && sel < gear.size() ? gear.get(sel) : ItemStack.EMPTY;
    }

    private int curLoc() {
        return sel >= 0 && sel < locs.size() ? locs.get(sel) : -1;
    }

    private static String where(int loc) {
        if (loc >= 100 && loc < 104) return "착용";
        if (loc > 200) return "착용";
        return loc >= 0 && loc < 9 ? "핫바" : "인벤";
    }

    /** 복구 재료: 인벤에 있는 같은 장비 (파괴 안 된 것) */
    private int material() {
        ItemStack s = cur();
        for (int i = 0; i < gear.size(); i++) {
            int l = locs.get(i);
            if (i == sel || l < 0 || l >= 36) continue;
            ItemStack m = gear.get(i);
            if (m.getItem() == s.getItem() && !Enhance.broken(m)) return l;
        }
        return -1;
    }

    @Override
    protected void init() {
        x = width / 2 - W / 2;
        y = height / 2 - H / 2;
        String[] names = {"스타포스", "주문서", "조각 합성"};
        int tx = x + 158;
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            int w = font.width(names[i]) + 12;
            Button b = Button.builder(Component.literal(names[i]), bt -> { tab = k; lastTab = k; rebuildWidgets(); }).bounds(tx, y + 19, w, 14).build();
            b.active = tab != i;
            addRenderableWidget(b);
            tx += w + 2;
        }
        addRenderableWidget(Button.builder(Component.literal("메뉴"), b -> Minecraft.getInstance().setScreen(new MenuScreen())).bounds(x + 6, y + H - 24, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal("닫기"), b -> onClose()).bounds(x + W - 52, y + H - 24, 46, 18).build());

        ItemStack s = cur();
        GearDefs.GearDef d = Gear.def(s);
        int bx = x + 158, by = y + H - 48;
        if (tab == 0 && d != null) {
            if (Enhance.broken(s)) {
                Button b = Button.builder(Component.literal("복구 (★" + Enhance.restoreStar + ")"), bt ->
                        Net.CH.sendToServer(new Net.EnhanceAction(3, curLoc(), material()))).bounds(bx, by, 110, 18).build();
                b.active = material() >= 0 && ClientData.gold >= Enhance.restoreCost(d);
                addRenderableWidget(b);
            } else {
                int st = Enhance.star(s);
                boolean can = st < Enhance.maxStar(d);
                if (can && Enhance.destroyRate(st) > 0) {
                    addRenderableWidget(Button.builder(Component.literal((guard ? "☑" : "☐") + " 파괴 방지"), bt -> { guard = !guard; rebuildWidgets(); })
                            .bounds(bx + 114, by, 66, 18).build());
                }
                Button b = Button.builder(Component.literal("강화"), bt ->
                        Net.CH.sendToServer(new Net.EnhanceAction(1, curLoc(), guard ? 1 : 0))).bounds(bx, by, 110, 18).build();
                b.active = can && ClientData.gold >= Enhance.cost(d, st, guard);
                addRenderableWidget(b);
            }
        } else if (tab == 1 && d != null) {
            Button b = Button.builder(Component.literal("주문서 바르기"), bt ->
                    Net.CH.sendToServer(new Net.EnhanceAction(2, curLoc(), slots.get(selScroll)))).bounds(bx, by, 110, 18).build();
            b.active = selScroll >= 0 && selScroll < scrolls.size() && Enhance.scrollBlock(s, scrolls.get(selScroll)) == null;
            addRenderableWidget(b);
        } else if (tab == 2) {
            Button b = Button.builder(Component.literal("합성 (조각 " + Enhance.fragments + "개)"), bt ->
                    Net.CH.sendToServer(new Net.EnhanceAction(4, 0, 0))).bounds(bx, by, 130, 18).build();
            b.active = frags >= Enhance.fragments;
            addRenderableWidget(b);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x, y, x + W, y + H, 0xF0141420);
        g.fill(x, y, x + W, y + 1, 0xFFFFD24A);
        g.drawString(font, "장비 강화", x + 8, y + 7, 0xFFFFD24A, true);
        String gold = String.format("%,d", ClientData.gold) + " G";
        g.drawString(font, gold, x + W - 8 - font.width(gold), y + 7, 0xFFFFD24A, true);

        // 왼쪽: 장비 목록
        List<Component> tip = null;
        g.drawString(font, "장비 (착용 → 인벤)", x + 8, y + 24, 0xFFA0A0A0, false);
        if (gear.isEmpty()) g.drawString(font, "장비가 없어요", x + 10, y + 40, 0xFF808080, false);
        for (int r = 0; r < ROWS; r++) {
            int i = r + scroll;
            if (i >= gear.size()) break;
            int ry = y + 36 + r * ROW;
            boolean hover = mx >= x + 6 && mx < x + 152 && my >= ry && my < ry + ROW - 2;
            g.fill(x + 6, ry, x + 152, ry + ROW - 2, i == sel ? 0x70FFD24A : hover ? 0x50FFFFFF : 0x30FFFFFF);
            ItemStack s = gear.get(i);
            g.renderItem(s, x + 8, ry + 1);
            String nm = s.getHoverName().getString();
            if (font.width(nm) > 92) nm = font.plainSubstrByWidth(nm, 88) + "…";
            g.drawString(font, nm, x + 28, ry + 5, Enhance.broken(s) ? 0xFFFF5555 : 0xFFFFFFFF, false);
            String w = where(locs.get(i));
            g.drawString(font, w, x + 150 - font.width(w), ry + 5, w.equals("착용") ? 0xFF55FF55 : 0xFF808080, false);
            if (hover) tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), s));
        }
        if (gear.size() > ROWS) g.drawString(font, "휠로 스크롤 (" + (scroll + 1) + "-" + Math.min(gear.size(), scroll + ROWS) + "/" + gear.size() + ")",
                x + 8, y + 36 + ROWS * ROW + 2, 0xFF808080, false);

        // 오른쪽
        int px = x + 158, py = y + 40;
        g.fill(px - 2, py - 4, x + W - 6, y + H - 54, 0x30FFFFFF);
        ItemStack s = cur();
        GearDefs.GearDef d = Gear.def(s);
        if (tab == 2) {
            g.drawString(font, "주문서 조각 합성", px, py, 0xFF55FFFF, false);
            g.drawString(font, "주문서 조각 " + Enhance.fragments + "개 → 무작위 주문서 1장", px, py + 14, 0xFFFFFFFF, false);
            g.drawString(font, "등급은 내 레벨 기준", px, py + 26, 0xFFA0A0A0, false);
            g.drawString(font, "가진 조각: " + frags + "개", px, py + 44, frags >= Enhance.fragments ? 0xFF55FF55 : 0xFFFF5555, false);
        } else if (d == null) {
            g.drawString(font, "왼쪽에서 장비를 고르세요", px, py, 0xFF808080, false);
        } else if (tab == 0) {
            renderStar(g, s, d, px, py);
        } else {
            tip = renderScroll(g, s, d, px, py, mx, my, tip);
        }
        super.render(g, mx, my, pt);
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    private void renderStar(GuiGraphics g, ItemStack s, GearDefs.GearDef d, int px, int py) {
        int st = Enhance.star(s), max = Enhance.maxStar(d);
        g.drawString(font, s.getHoverName(), px, py, 0xFFFFFFFF, false);
        if (max == 0) { g.drawString(font, "스타포스 강화 불가", px, py + 14, 0xFFFF5555, false); return; }
        g.drawString(font, "★".repeat(Math.min(st, max)) + "☆".repeat(Math.max(0, max - st)), px, py + 12, 0xFFFFD24A, false);
        if (Enhance.broken(s)) {
            g.drawString(font, "파괴된 장비", px, py + 28, 0xFFFF5555, false);
            g.drawString(font, "같은 장비 1개 + 골드 → ★" + Enhance.restoreStar, px, py + 40, 0xFFFFFFFF, false);
            boolean has = material() >= 0;
            g.drawString(font, has ? "재료: 인벤의 같은 장비 (자동 선택)" : "재료 없음: 인벤에 같은 장비 필요", px, py + 52, has ? 0xFF55FF55 : 0xFFFF5555, false);
            long c = Enhance.restoreCost(d);
            g.drawString(font, "비용 " + String.format("%,d", c) + " G", px, py + 64, ClientData.gold >= c ? 0xFFFFD24A : 0xFFFF5555, false);
            return;
        }
        if (st >= max) { g.drawString(font, "최대 ★" + max + " 달성", px, py + 28, 0xFF55FF55, false); return; }
        double ok = Enhance.rate(st), br = guard ? 0 : Enhance.destroyRate(st);
        g.drawString(font, "★" + st + " → ★" + (st + 1), px, py + 26, 0xFFFFFFFF, false);
        g.drawString(font, "성공 " + fmt(ok) + "%", px, py + 38, 0xFF55FF55, false);
        g.drawString(font, "실패(유지) " + fmt(100 - ok - br) + "%", px + 62, py + 38, 0xFFA0A0A0, false);
        if (Enhance.destroyRate(st) > 0) g.drawString(font, guard ? "파괴 0% (방지)" : "파괴 " + fmt(br) + "%", px, py + 50, guard ? 0xFF55FF55 : 0xFFFF5555, false);
        // 다음 ★ 상승치
        Map<String, Integer> now = Enhance.starStats(d, Enhance.scr(s), st), next = Enhance.starStats(d, Enhance.scr(s), st + 1);
        StringBuilder sb = new StringBuilder();
        for (var e : next.entrySet()) {
            int diff = e.getValue() - now.getOrDefault(e.getKey(), 0);
            if (diff == 0) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(GearDefs.label(e.getKey())).append(" +").append(diff);
        }
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal("성공 시: " + sb), x + W - 10 - px);
        for (int i = 0; i < lines.size() && i < 3; i++) g.drawString(font, lines.get(i), px, py + 64 + i * 10, 0xFFFFE080, false);
        long c = Enhance.cost(d, st, guard);
        g.drawString(font, "비용 " + String.format("%,d", c) + " G" + (guard && Enhance.destroyRate(st) > 0 ? " (방지 ×" + fmt(Enhance.safeguardMul) + ")" : ""),
                px, py + 98, ClientData.gold >= c ? 0xFFFFD24A : 0xFFFF5555, false);
    }

    private List<Component> renderScroll(GuiGraphics g, ItemStack s, GearDefs.GearDef d, int px, int py, int mx, int my, List<Component> tip) {
        g.drawString(font, s.getHoverName(), px, py, 0xFFFFFFFF, false);
        int um = Enhance.upgMax(d);
        if (um == 0) { g.drawString(font, "주문서 사용 불가", px, py + 14, 0xFFFF5555, false); return tip; }
        g.drawString(font, "업그레이드 가능 " + Enhance.upgLeft(s) + " / " + um + " (실패 " + Enhance.upgFail(s) + ")", px, py + 12, 0xFFA0A0A0, false);
        g.drawString(font, "내 주문서 (초록 = 사용 가능)", px, py + 26, 0xFFA0A0A0, false);
        int cols = 9;
        for (int i = 0; i < scrolls.size() && i < 27; i++) {
            int cx = px + (i % cols) * CELL, cy = py + 38 + (i / cols) * CELL;
            ItemStack sc = scrolls.get(i);
            boolean ok = Enhance.scrollBlock(s, sc) == null;
            boolean hover = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL;
            g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, i == selScroll ? 0xA0FFD24A : hover ? 0x80FFFFFF : ok ? 0x5050C050 : 0x50803030);
            g.renderItem(sc, cx + 1, cy + 1);
            g.renderItemDecorations(font, sc, cx + 1, cy + 1);
            if (hover) {
                tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), sc));
                String why = Enhance.scrollBlock(s, sc);
                tip.add(Component.literal(why == null ? "클릭해서 선택" : why).withStyle(why == null ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        if (scrolls.isEmpty()) g.drawString(font, "주문서가 없어요", px, py + 40, 0xFF808080, false);
        if (selScroll >= 0 && selScroll < scrolls.size()) {
            String why = Enhance.scrollBlock(s, scrolls.get(selScroll));
            g.drawString(font, "선택: " + scrolls.get(selScroll).getHoverName().getString(), px, py + 98, 0xFFFFE080, false);
            if (why != null) g.drawString(font, why, px, py + 108, 0xFFFF5555, false);
        }
        return tip;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int r = 0; r < ROWS; r++) {
            int i = r + scroll;
            if (i >= gear.size()) break;
            int ry = y + 36 + r * ROW;
            if (mx >= x + 6 && mx < x + 152 && my >= ry && my < ry + ROW - 2) {
                sel = i;
                selScroll = -1;
                rebuildWidgets();
                return true;
            }
        }
        if (tab == 1) {
            int px = x + 158, py = y + 40;
            for (int i = 0; i < scrolls.size() && i < 27; i++) {
                int cx = px + (i % 9) * CELL, cy = py + 38 + (i / 9) * CELL;
                if (mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL) {
                    selScroll = i;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, gear.size() - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
