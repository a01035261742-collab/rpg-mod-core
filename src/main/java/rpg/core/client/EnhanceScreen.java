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
    private static final int W = 370, H = 236, ROW = 20, ROWS = 8, CELL = 18;
    private List<Integer> locs;
    private List<ItemStack> gear;
    private List<Integer> slots;
    private List<ItemStack> scrolls;
    private int frags;
    private List<Integer> extra = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private int x, y, scroll, tab, sel = -1, selScroll = -1;
    private boolean guard;
    /** 잠재 결과: 0.7초 동안 고르기 버튼 막기 · 등급업 기존 유지 확인 창 */
    private long readyAt;
    private String pendKey = "";
    private boolean waiting, confirmKeep;
    private static int lastTab;

    private EnhanceScreen(List<Integer> locs, List<ItemStack> gear, List<Integer> slots, List<ItemStack> scrolls, int frags, List<Integer> extra) {
        super(Component.literal("장비 강화"));
        this.tab = lastTab;
        set(locs, gear, slots, scrolls, frags, extra);
        if (!gear.isEmpty()) sel = 0;
    }

    private void set(List<Integer> locs, List<ItemStack> gear, List<Integer> slots, List<ItemStack> scrolls, int frags, List<Integer> extra) {
        this.extra = extra.size() >= 10 ? extra : List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        this.locs = locs;
        this.gear = gear;
        this.slots = slots;
        this.scrolls = scrolls;
        this.frags = frags;
    }

    /** 서버가 보낸 내용 받기: open 이면 새 창, 아니면 열린 창만 갱신 (선택 유지) */
    public static void receive(boolean open, List<Integer> locs, List<ItemStack> gear, List<Integer> slots, List<ItemStack> scrolls, int frags, List<Integer> extra) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EnhanceScreen es) {
            int keepLoc = es.sel >= 0 && es.sel < es.locs.size() ? es.locs.get(es.sel) : -1;
            int keepSlot = es.selScroll >= 0 && es.selScroll < es.slots.size() ? es.slots.get(es.selScroll) : -1;
            es.set(locs, gear, slots, scrolls, frags, extra);
            es.sel = locs.indexOf(keepLoc);
            if (es.sel < 0 && !gear.isEmpty()) es.sel = 0;
            es.selScroll = slots.indexOf(keepSlot);
            es.rebuildWidgets();
        } else if (open) {
            mc.setScreen(new EnhanceScreen(locs, gear, slots, scrolls, frags, extra));
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
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        String[] names = {"스타포스", "주문서", "조각", "잠재능력", "추가옵션"};
        int[] order = {0, 1, 3, 4, 2};   // 조각은 맨 뒤
        int tx = x + 158;
        for (int i : order) {
            final int k = i;
            int w = font.width(names[i]) + 8;
            Button b = Ui.button(Component.literal(names[i]), bt -> { tab = k; lastTab = k; rebuildWidgets(); }).bounds(tx, y + 19, w, 14).build();
            b.active = tab != i;
            addRenderableWidget(b);
            tx += w + 2;
        }
        addRenderableWidget(Ui.menu(x, y, H));
        addRenderableWidget(Ui.close(x, y, W, H, this::onClose));

        ItemStack s = cur();
        GearDefs.GearDef d = Gear.def(s);
        int bx = x + 158, by = y + H - 48;
        if (tab == 0 && d != null) {
            if (Enhance.broken(s)) {
                Button b = Ui.button(Component.literal("복구 (★" + Enhance.restoreStar + ")"), bt ->
                        Net.CH.sendToServer(new Net.EnhanceAction(3, curLoc(), material()))).bounds(bx, by, 110, 18).build();
                b.active = material() >= 0 && ClientData.gold >= Enhance.restoreCost(d);
                addRenderableWidget(b);
            } else {
                int st = Enhance.star(s);
                boolean can = st < Enhance.maxStar(d);
                if (can && Enhance.destroyRate(st) > 0) {
                    addRenderableWidget(Ui.button(Component.literal((guard ? "☑" : "☐") + " 파괴 방지"), bt -> { guard = !guard; rebuildWidgets(); })
                            .bounds(bx + 114, by, 66, 18).build());
                }
                Button b = Ui.button(Component.literal("강화"), bt ->
                        Net.CH.sendToServer(new Net.EnhanceAction(1, curLoc(), guard ? 1 : 0))).bounds(bx, by, 110, 18).build();
                b.active = can && ClientData.gold >= Enhance.cost(d, st, guard);
                addRenderableWidget(b);
            }
        } else if (tab == 1 && d != null) {
            Button b = Ui.button(Component.literal("주문서 바르기"), bt ->
                    Net.CH.sendToServer(new Net.EnhanceAction(2, curLoc(), slots.get(selScroll)))).bounds(bx, by, 110, 18).build();
            b.active = selScroll >= 0 && selScroll < scrolls.size() && Enhance.scrollBlock(s, scrolls.get(selScroll)) == null;
            addRenderableWidget(b);
        } else if (tab == 3 && d != null && rpg.core.equip.Potential.can(d)) {
            if (!rpg.core.equip.Potential.has(s)) {
                Button b1 = Ui.button(Component.literal("레어 부여 (" + extra.get(1) + ")"), bt -> Net.CH.sendToServer(new Net.EnhanceAction(5, curLoc(), 0))).bounds(bx, by, 86, 18).build();
                b1.active = extra.get(1) > 0;
                addRenderableWidget(b1);
                Button b2 = Ui.button(Component.literal("에픽 부여 (" + extra.get(2) + ")"), bt -> Net.CH.sendToServer(new Net.EnhanceAction(5, curLoc(), 1))).bounds(bx + 90, by, 86, 18).build();
                b2.active = extra.get(2) > 0;
                addRenderableWidget(b2);
            } else if (rpg.core.equip.Potential.pending(s)) {
                String key = curLoc() + ":" + rpg.core.equip.Potential.pot(s).getCompound("new");
                if (!key.equals(pendKey)) { pendKey = key; readyAt = System.currentTimeMillis() + 700; confirmKeep = false; }
                waiting = System.currentTimeMillis() < readyAt;
                boolean up = rpg.core.equip.Potential.pendingUp(s);
                // 등급업이면 버튼을 아래 줄 가운데로 (재설정 버튼 자리 연타해도 안 눌리게)
                int ax = up ? x + 170 : bx, ay = up ? y + H - Ui.FOOT : by;
                Button b1, b2;
                if (up && confirmKeep) {
                    b1 = Ui.button(Component.literal("유지할게요"), bt -> { confirmKeep = false; Net.CH.sendToServer(new Net.EnhanceAction(9, curLoc(), 1)); }).bounds(ax, ay, 70, 18).build();
                    b2 = Ui.button(Component.literal("취소"), bt -> { confirmKeep = false; rebuildWidgets(); }).bounds(ax + 74, ay, 66, 18).build();
                } else {
                    b1 = Ui.button(Component.literal(up ? "새 옵션 적용 ▲" : "새 옵션 적용"), bt -> Net.CH.sendToServer(new Net.EnhanceAction(8, curLoc(), 0))).bounds(ax, ay, up ? 70 : 86, 18).build();
                    b2 = Ui.button(Component.literal("기존 유지"), bt -> {
                        if (up) { confirmKeep = true; readyAt = System.currentTimeMillis() + 700; rebuildWidgets(); }
                        else Net.CH.sendToServer(new Net.EnhanceAction(9, curLoc(), 0));
                    }).bounds(up ? ax + 74 : bx + 90, ay, up ? 66 : 86, 18).build();
                }
                b1.active = b2.active = !waiting;
                addRenderableWidget(b1);
                addRenderableWidget(b2);
            } else {
                long c = rpg.core.equip.Potential.cost(d, rpg.core.equip.Potential.grade(s));
                Button b1 = Ui.button(Component.literal(String.format("%,d G", c)), bt -> Net.CH.sendToServer(new Net.EnhanceAction(6, curLoc(), 0))).bounds(bx, by, 86, 18).build();
                b1.active = ClientData.gold >= c;
                addRenderableWidget(b1);
                Button b2 = Ui.button(Component.literal("큐브 (" + extra.get(0) + ")"), bt -> Net.CH.sendToServer(new Net.EnhanceAction(7, curLoc(), 0))).bounds(bx + 90, by, 86, 18).build();
                b2.active = extra.get(0) > 0;
                addRenderableWidget(b2);
            }
        } else if (tab == 4 && d != null && rpg.core.equip.Flame.can(d) && !Enhance.broken(s)) {
            if (rpg.core.equip.Flame.pending(s)) {
                addRenderableWidget(Ui.button(Component.literal("새 옵션 적용"), bt -> Net.CH.sendToServer(new Net.EnhanceAction(11, curLoc(), 0))).bounds(bx, by, 100, 18).build());
                addRenderableWidget(Ui.button(Component.literal("이전 유지"), bt -> Net.CH.sendToServer(new Net.EnhanceAction(11, curLoc(), 1))).bounds(bx + 104, by, 100, 18).build());
            } else {
                String[] nm = {"강력한", "영원한", "검은"};
                long c = rpg.core.equip.Flame.cost(d);
                for (int i = 0; i < 3; i++) {
                    final int k = i;
                    Button b = Ui.button(Component.literal(nm[i] + " (" + extra.get(6 + i) + ")"), bt -> Net.CH.sendToServer(new Net.EnhanceAction(10, curLoc(), k)))
                            .bounds(bx + i * 70, by, 66, 18).build();
                    b.active = extra.get(6 + i) > 0 && ClientData.gold >= c;
                    addRenderableWidget(b);
                }
            }
        } else if (tab == 2) {
            Button b = Ui.button(Component.literal("합성 (조각 " + Enhance.fragments + "개)"), bt ->
                    Net.CH.sendToServer(new Net.EnhanceAction(4, 0, 0))).bounds(bx, by, 130, 18).build();
            b.active = frags >= Enhance.fragments;
            addRenderableWidget(b);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (waiting && System.currentTimeMillis() >= readyAt) { waiting = false; rebuildWidgets(); }   // 0.7초 지나면 버튼 켜기
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Ui.frame(g, font, x, y, W, H, Ui.GOLD, "장비 강화", "전투력 " + String.format("%,d", extra.get(9)) + "   " + String.format("%,d", ClientData.gold) + " G", Ui.GOLD);
        Ui.footLine(g, x, y, W, H);

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
        } else if (tab == 3) {
            renderPot(g, s, d, px, py);
        } else if (tab == 4) {
            renderFlame(g, s, d, px, py);
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

    private void renderPot(GuiGraphics g, ItemStack s, GearDefs.GearDef d, int px, int py) {
        g.drawString(font, s.getHoverName(), px, py, 0xFFFFFFFF, false);
        if (!rpg.core.equip.Potential.can(d)) { g.drawString(font, "잠재능력 불가", px, py + 14, 0xFFFF5555, false); return; }
        if (!rpg.core.equip.Potential.has(s)) {
            g.drawString(font, "잠재능력 없음", px, py + 14, 0xFFA0A0A0, false);
            g.drawString(font, "부여 주문서로 처음 부여해요", px, py + 26, 0xFFA0A0A0, false);
            return;
        }
        var pt = rpg.core.equip.Potential.pot(s);
        int gr = Math.max(0, Math.min(3, pt.getInt("g")));
        int y0 = py + 13;
        boolean pend = rpg.core.equip.Potential.pending(s);
        g.drawString(font, (pend ? "지금 " : "") + "[" + rpg.core.equip.Potential.GRADE[gr] + "]", px, y0, 0xFF000000 | rpg.core.equip.Potential.COLOR[gr], false);
        int yy = y0 + 10;
        for (var c : rpg.core.equip.Potential.lines(pt)) { g.drawString(font, c, px, yy, 0xFFFFFFFF, false); yy += 10; }
        if (pend) {
            var nw = pt.getCompound("new");
            int ng = Math.max(0, Math.min(3, nw.getInt("g")));
            yy += 2;
            g.drawString(font, "새 옵션 [" + rpg.core.equip.Potential.GRADE[ng] + "]" + (ng > gr ? " ▲" : ""), px, yy, 0xFF000000 | rpg.core.equip.Potential.COLOR[ng], false);
            yy += 10;
            for (var c : rpg.core.equip.Potential.lines(nw)) { g.drawString(font, c, px, yy, 0xFFFFFFFF, false); yy += 10; }
            powLine(g, s, "rpgPowPot", px, yy + 2);
            if (confirmKeep && ng > gr) {
                g.drawString(font, "등급이 올랐어요 (" + rpg.core.equip.Potential.GRADE[gr] + " → " + rpg.core.equip.Potential.GRADE[ng] + ")", px, y + H - 60, 0xFFFF8040, false);
                g.drawString(font, "정말 이전 옵션 유지? 오른 등급은 사라져요", px, y + H - 48, 0xFFFF8040, false);
            }
        } else if (gr < 3) {
            yy += 4;
            g.drawString(font, "등급 상승 " + fmt(rpg.core.equip.Potential.upChance[gr]) + "% · 천장 " + extra.get(3 + gr) + " / " + rpg.core.equip.Potential.ceiling[gr], px, yy, 0xFFA0A0A0, false);
        }
    }

    private void renderFlame(GuiGraphics g, ItemStack s, GearDefs.GearDef d, int px, int py) {
        g.drawString(font, s.getHoverName(), px, py, 0xFFFFFFFF, false);
        if (!rpg.core.equip.Flame.can(d)) { g.drawString(font, "추가 옵션 불가 (훈장)", px, py + 14, 0xFFFF5555, false); return; }
        if (Enhance.broken(s)) { g.drawString(font, "파괴된 장비예요", px, py + 14, 0xFFFF5555, false); return; }
        int yy = py + 13;
        boolean pend = rpg.core.equip.Flame.pending(s);
        g.drawString(font, pend ? "이전 옵션" : "지금 옵션", px, yy, 0xFF55FF55, false);
        yy = flameLines(g, Gear.opts(s), rpg.core.equip.Flame.tiers(s), px, yy + 10);
        if (pend) {
            var nw = rpg.core.equip.Flame.pendingTag(s);
            g.drawString(font, "새 옵션", px, yy + 2, 0xFFB070FF, false);
            int ny = flameLines(g, nw.getCompound("o"), nw.getCompound("t"), px, yy + 12);
            powLine(g, s, "rpgPowFlame", px, ny + 2);
        } else {
            long c = rpg.core.equip.Flame.cost(d);
            g.drawString(font, "강력한 3~6단계 · 영원한 4~7 · 검은 4~7 (선택)", px, y + H - 76, 0xFFA0A0A0, false);
            g.drawString(font, "비용: 불꽃 1개 + " + String.format("%,d", c) + " G", px, y + H - 66, ClientData.gold >= c ? 0xFFFFD24A : 0xFFFF5555, false);
        }
    }

    /** 전투력 변화 (서버가 계산해서 장비 복사본에 붙여 보냄) */
    private void powLine(GuiGraphics g, ItemStack s, String key, int px, int yy) {
        String w = where(curLoc());
        boolean weapon = "weapon".equals(Gear.slotOf(s));   // 무기는 손에 없어도 든 걸로 계산
        if (!w.equals("착용") && !weapon) { g.drawString(font, "전투력: 착용하면 표시", px, yy, 0xFF808080, false); return; }
        long dlt = s.hasTag() ? s.getTag().getLong(key) : 0;
        String t = dlt == 0 ? "전투력 변화 없음" : String.format("전투력 %+,d %s", dlt, dlt > 0 ? "▲" : "▼");
        g.drawString(font, t, px, yy, dlt > 0 ? 0xFF55FF55 : dlt < 0 ? 0xFFFF5555 : 0xFFA0A0A0, false);
    }

    private int flameLines(GuiGraphics g, net.minecraft.nbt.CompoundTag o, net.minecraft.nbt.CompoundTag t, int px, int yy) {
        if (o.isEmpty()) { g.drawString(font, " 없음", px, yy, 0xFF808080, false); return yy + 10; }
        for (String k : o.getAllKeys()) {
            g.drawString(font, " " + rpg.core.equip.Flame.text(k, o.getInt(k), t.getInt(k)), px, yy, 0xFFFFFFFF, false);
            yy += 10;
        }
        return yy;
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
