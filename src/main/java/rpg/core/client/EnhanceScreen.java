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

/** 강화 탭 (시안 ui_d1): 왼쪽 강화 종류 · 가운데 강화 · 오른쪽 입은 장비 칸 + [가방] 팝업 */
public class EnhanceScreen extends ScaledScreen {
    private static final int W = CharWin.W, H = CharWin.H, CELL = 18;
    private List<Integer> locs;
    private List<ItemStack> gear;
    private List<Integer> slots;
    private List<ItemStack> scrolls;
    private int frags;
    private List<Integer> extra = List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    private int x, y, tab, sel = -1, selScroll = -1;
    private boolean bag;
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

    // 왼쪽 종류 (tab 번호: 0 스타포스 · 1 주문서 · 2 조각 · 3 잠재능력 · 4 추가 옵션)
    private static final String[] KINDS = {"스타포스", "주문서", "조각", "잠재능력", "추가 옵션"};
    private static final int CX = 172, BY = 156;
    /** 입은 장비 칸: 열마다 위→아래 (위치 번호) — 1열 모자·상의·하의·신발·무기 / 2열 장갑·망토·벨트·펜던트·훈장 / 3열 반지 4·귀걸이 */
    private static final int[][] GRID = {{103, 102, 101, 100, 0}, {201, 202, 203, 209, 210}, {205, 206, 207, 208, 204}};

    private int idxOf(int loc) { return locs.indexOf(loc); }

    private List<Integer> bagIdx() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < locs.size(); i++) { int lc = locs.get(i); if (lc > 0 && lc < 36) l.add(i); }
        return l;
    }

    private Button btn(String t, int bx, int w, boolean accent, Button.OnPress p) {
        Button b = Ui.button(Component.literal(t), p).bounds(bx, y + BY, w, 15).accent(accent).build();
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.ENHANCE);
        addRenderableWidget(Ui.button(Component.literal("가방"), b -> { bag = !bag; rebuildWidgets(); })
                .bounds(x + 228, y + BY, 54, 15).accent(bag).build());
        if (bag) return;   // 가방 팝업 동안은 강화 버튼 숨김

        ItemStack s = cur();
        GearDefs.GearDef d = Gear.def(s);
        int c1 = x + CX - 30, l2 = x + 126, r2 = x + 174;
        if (tab == 0 && d != null) {
            if (Enhance.broken(s)) {
                btn("복구", c1, 60, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(3, curLoc(), material())))
                        .active = material() >= 0 && ClientData.gold >= Enhance.restoreCost(d);
            } else {
                int st = Enhance.star(s);
                boolean can = st < Enhance.maxStar(d);
                if (can && Enhance.destroyRate(st) > 0) {
                    addRenderableWidget(Ui.button(Component.literal((guard ? "☑" : "☐") + " 파괴 방지"), bt -> { guard = !guard; rebuildWidgets(); })
                            .bounds(x + CX - 34, y + 120, 68, 13).build());
                }
                btn("강화", c1, 60, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(1, curLoc(), guard ? 1 : 0)))
                        .active = can && ClientData.gold >= Enhance.cost(d, st, guard);
            }
        } else if (tab == 1 && d != null) {
            btn("주문서 바르기", x + CX - 40, 80, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(2, curLoc(), slots.get(selScroll))))
                    .active = selScroll >= 0 && selScroll < scrolls.size() && Enhance.scrollBlock(s, scrolls.get(selScroll)) == null;
        } else if (tab == 3 && d != null && rpg.core.equip.Potential.can(d)) {
            if (!rpg.core.equip.Potential.has(s)) {
                btn("레어 (" + extra.get(1) + ")", l2, 46, false, bt -> Net.CH.sendToServer(new Net.EnhanceAction(5, curLoc(), 0))).active = extra.get(1) > 0;
                btn("에픽 (" + extra.get(2) + ")", r2, 46, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(5, curLoc(), 1))).active = extra.get(2) > 0;
            } else if (rpg.core.equip.Potential.pending(s)) {
                String key = curLoc() + ":" + rpg.core.equip.Potential.pot(s).getCompound("new");
                boolean up = rpg.core.equip.Potential.pendingUp(s);
                if (!key.equals(pendKey)) { pendKey = key; readyAt = up ? System.currentTimeMillis() + 700 : 0; confirmKeep = false; }
                waiting = System.currentTimeMillis() < readyAt;
                Button b1, b2;
                if (up && confirmKeep) {
                    b1 = btn("취소", l2, 46, false, bt -> { confirmKeep = false; rebuildWidgets(); });
                    b2 = btn("유지할게요", r2, 46, false, bt -> { confirmKeep = false; Net.CH.sendToServer(new Net.EnhanceAction(9, curLoc(), 1)); });
                } else {
                    b1 = btn("이전 유지", l2, 46, false, bt -> {
                        if (up) { confirmKeep = true; readyAt = System.currentTimeMillis() + 700; rebuildWidgets(); }
                        else Net.CH.sendToServer(new Net.EnhanceAction(9, curLoc(), 0));
                    });
                    b2 = btn(up ? "새 옵션▲" : "새 옵션", r2, 46, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(8, curLoc(), 0)));
                }
                b1.active = b2.active = !waiting;
            } else {
                long c = rpg.core.equip.Potential.cost(d, rpg.core.equip.Potential.grade(s));
                btn(shortG(c), l2, 46, false, bt -> Net.CH.sendToServer(new Net.EnhanceAction(6, curLoc(), 0))).active = ClientData.gold >= c;
                btn("큐브 " + extra.get(0), r2, 46, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(7, curLoc(), 0))).active = extra.get(0) > 0;
            }
        } else if (tab == 4 && d != null && rpg.core.equip.Flame.can(d) && !Enhance.broken(s)) {
            if (rpg.core.equip.Flame.pending(s)) {
                btn("이전 유지", l2, 46, false, bt -> Net.CH.sendToServer(new Net.EnhanceAction(11, curLoc(), 1)));
                btn("새 옵션", r2, 46, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(11, curLoc(), 0)));
            } else {
                String[] nm = {"강력", "영원", "검은"};
                long c = rpg.core.equip.Flame.cost(d);
                for (int i = 0; i < 3; i++) {
                    final int k = i;
                    btn(nm[i] + " " + extra.get(6 + i), x + 125 + i * 33, 31, i == 1, bt -> Net.CH.sendToServer(new Net.EnhanceAction(10, curLoc(), k)))
                            .active = extra.get(6 + i) > 0 && ClientData.gold >= c;
                }
            }
        } else if (tab == 2) {
            btn("합성", c1, 60, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(4, 0, 0))).active = frags >= Enhance.fragments;
        }
    }

    private static String shortG(long c) {
        return c >= 100000 ? (c / 10000) + "만 G" : String.format("%,d G", c);
    }

    @Override
    public void tick() {
        super.tick();
        if (waiting && System.currentTimeMillis() >= readyAt) { waiting = false; rebuildWidgets(); }
    }

    private void center(GuiGraphics g, String t, int yy, int col) {
        String c = Ui.cut(font, t, 96);
        g.drawString(font, c, x + CX - font.width(c) / 2, yy, col, true);
    }

    /** 작은 별 (5×5) */
    private static void star(GuiGraphics g, int sx, int sy, int col) {
        g.fill(sx + 2, sy, sx + 3, sy + 1, col);
        g.fill(sx, sy + 1, sx + 5, sy + 3, col);
        g.fill(sx + 1, sy + 3, sx + 4, sy + 4, col);
        g.fill(sx, sy + 4, sx + 2, sy + 5, col); g.fill(sx + 3, sy + 4, sx + 5, sy + 5, col);
    }

    private void stars(GuiGraphics g, int n, int max, int yy) {
        int sp = max > 15 ? 4 : 6, w = max * sp + (max - 1) / 5 * 3;
        int sx = x + CX - w / 2;
        for (int i = 0; i < max; i++) star(g, sx + i * sp + (i / 5) * 3, yy, i < n ? 0xFFFFD86A : 0xFF3A2A2A);
    }

    /** 장비 칸 + 아이템 (+ 고르면 금 테두리) */
    private void cell(GuiGraphics g, int cx, int cy, ItemStack it, boolean on) {
        Ui.slot(g, cx, cy);
        if (!it.isEmpty()) { g.renderItem(it, cx + 1, cy + 1); g.renderItemDecorations(font, it, cx + 1, cy + 1); }
        if (on) Ui.selBox(g, cx, cy, 18, 18);
        if (!it.isEmpty() && Enhance.broken(it)) g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0x70FF2020);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CharWin.draw(g, font, x, y);
        List<Component> tip = null;
        // 왼쪽: 종류
        for (int i = 0; i < KINDS.length; i++) {
            int ry = y + 30 + i * 15;
            boolean on = mx >= x + 72 && mx < x + 120 && my >= ry - 2 && my < ry + 12;
            if (i == tab || on) Ui.row(g, x + 72, ry - 2, x + 120, ry + 11, i == tab);
            g.drawString(font, KINDS[i], x + 76, ry, i == tab ? Ui.VAL : Ui.GRAY, i == tab);
        }
        Ui.vsep(g, x + 122, y + 28, y + 172);
        Ui.vsep(g, x + 223, y + 28, y + 172);
        // 오른쪽: 입은 장비
        g.drawString(font, "입은 장비", x + 255 - font.width("입은 장비") / 2, y + 29, Ui.TXT, true);
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 5; r++) {
                int lc = GRID[c][r], i = idxOf(lc), cx = x + 228 + c * 18, cy = y + 41 + r * 19;
                ItemStack it = i >= 0 ? gear.get(i) : ItemStack.EMPTY;
                cell(g, cx, cy, it, i >= 0 && i == sel);
                if (i >= 0 && mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18 && !bag) tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), it));
            }
        // 가운데
        if (!bag) {
            ItemStack s = cur();
            GearDefs.GearDef d = Gear.def(s);
            if (tab == 2) renderFrag(g);
            else if (d == null) { center(g, "강화할 장비를", y + 80, Ui.GRAY); center(g, "오른쪽에서 고르세요", y + 92, Ui.GRAY); }
            else if (tab == 0) renderStar(g, s, d);
            else if (tab == 1) tip = renderScroll(g, s, d, mx, my, tip);
            else if (tab == 3) renderPot(g, s, d);
            else renderFlame(g, s, d);
        }
        super.render(g, mx, my, pt);
        if (bag) tip = renderBag(g, mx, my);
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }

    private List<Component> renderBag(GuiGraphics g, int mx, int my) {
        g.pose().pushPose(); g.pose().translate(0, 0, 300);
        int bx = x + 76, by = y + 40, bw = 146, bh = 110;
        Ui.window(g, bx, by, bw, bh);
        g.drawString(font, "가방 속 장비", bx + 10, by + 8, Ui.VAL, true);
        List<Integer> l = bagIdx();
        List<Component> tip = null;
        for (int k = 0; k < l.size() && k < 28; k++) {
            int i = l.get(k), cx = bx + 9 + (k % 7) * 18, cy = by + 21 + (k / 7) * 19;
            cell(g, cx, cy, gear.get(i), i == sel);
            if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) {
                tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), gear.get(i)));
                tip.add(Component.literal("클릭: 이 장비 강화").withStyle(ChatFormatting.GRAY));
            }
        }
        if (l.isEmpty()) g.drawString(font, "가방에 장비가 없어요", bx + 10, by + 30, Ui.GRAY, false);
        g.pose().popPose();
        return tip;
    }

    private void renderFrag(GuiGraphics g) {
        var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("rpgcore", "scroll_fragment"));
        Ui.slot(g, x + CX - 9, y + 34);
        if (it != null) g.renderItem(new ItemStack(it), x + CX - 8, y + 35);
        center(g, "주문서 조각 합성", y + 58, Ui.VAL);
        center(g, "조각 " + Enhance.fragments + "개 → 주문서 1장", y + 74, Ui.TXT);
        center(g, "등급: 내 레벨 기준", y + 86, Ui.GRAY);
        center(g, "가진 조각 " + frags + " / " + Enhance.fragments, y + 106, frags >= Enhance.fragments ? Ui.GREEN : Ui.RED);
        Ui.bar(g, x + CX - 40, y + 120, 80, 4, frags / (float) Math.max(1, Enhance.fragments), 0xFF7CE07C);
    }

    /** 가운데 위: 고른 장비 칸 + 이름 */
    private int head(GuiGraphics g, ItemStack s, int top) {
        Ui.slot(g, x + CX - 9, top);
        g.renderItem(s, x + CX - 8, top + 1);
        Ui.selBox(g, x + CX - 9, top, 18, 18);
        center(g, s.getHoverName().getString(), top + 22, Enhance.broken(s) ? Ui.RED : Ui.VAL);
        return top + 34;
    }

    private void renderStar(GuiGraphics g, ItemStack s, GearDefs.GearDef d) {
        int st = Enhance.star(s), max = Enhance.maxStar(d);
        if (max > 0) stars(g, Math.min(st, max), max, y + 30);
        int yy = head(g, s, y + 40);
        if (max == 0) { center(g, "스타포스 강화 불가", yy, Ui.RED); return; }
        if (Enhance.broken(s)) {
            center(g, "파괴된 장비", yy, Ui.RED);
            center(g, "같은 장비 1개 + 골드", yy + 14, Ui.TXT);
            center(g, "→ ★" + Enhance.restoreStar + " 로 복구", yy + 26, Ui.VAL);
            boolean has = material() >= 0;
            center(g, has ? "재료 있음 (가방)" : "재료 없음", yy + 40, has ? Ui.GREEN : Ui.RED);
            long c = Enhance.restoreCost(d);
            center(g, String.format("%,d G", c), yy + 54, ClientData.gold >= c ? Ui.VAL : Ui.RED);
            return;
        }
        if (st >= max) { center(g, "★" + max + " 최대 강화", yy, Ui.VAL); center(g, "더 강화할 수 없어요", yy + 12, Ui.GRAY); return; }
        double ok = Enhance.rate(st), br = guard ? 0 : Enhance.destroyRate(st);
        center(g, "★" + st + " → ★" + (st + 1), yy, Ui.TXT);
        int bx = x + CX - 42;
        Ui.bar(g, bx, yy + 13, 84, 4, (float) (ok / 100), 0xFF5AC85A);
        if (br > 0) g.fill(bx + (int) (84 * ok / 100), yy + 13, bx + (int) (84 * ok / 100) + Math.max(1, (int) (84 * br / 100)), yy + 17, 0xFFDC3232);
        center(g, "성공 " + fmt(ok) + "%" + (Enhance.destroyRate(st) > 0 ? " · 파괴 " + fmt(br) + "%" : ""), yy + 21, Ui.TXT);
        long c = Enhance.cost(d, st, guard);
        center(g, String.format("%,d G", c), y + 138, ClientData.gold >= c ? Ui.VAL : Ui.RED);
    }

    private List<Component> renderScroll(GuiGraphics g, ItemStack s, GearDefs.GearDef d, int mx, int my, List<Component> tip) {
        int yy = head(g, s, y + 28);
        int um = Enhance.upgMax(d);
        if (um == 0) { center(g, "주문서 사용 불가", yy, Ui.RED); return tip; }
        center(g, "업그레이드 " + Enhance.upgLeft(s) + "/" + um + " · 실패 " + Enhance.upgFail(s), yy, Ui.TXT);
        Ui.hsep(g, x + 126, x + 220, yy + 12);
        int px = x + 127, py = yy + 16;
        for (int i = 0; i < scrolls.size() && i < 10; i++) {
            int cx = px + (i % 5) * 19, cy = py + (i / 5) * 19;
            ItemStack sc = scrolls.get(i);
            boolean ok = Enhance.scrollBlock(s, sc) == null;
            cell(g, cx, cy, sc, i == selScroll);
            if (!ok) g.fill(cx + 1, cy + 1, cx + 17, cy + 17, 0x90000000);
            if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) {
                tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), sc));
                String why = Enhance.scrollBlock(s, sc);
                tip.add(Component.literal(why == null ? "클릭해서 선택" : why).withStyle(why == null ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
        }
        if (scrolls.isEmpty()) center(g, "주문서가 없어요", py + 6, Ui.GRAY);
        if (selScroll >= 0 && selScroll < scrolls.size()) {
            String why = Enhance.scrollBlock(s, scrolls.get(selScroll));
            center(g, scrolls.get(selScroll).getHoverName().getString(), y + 132, why == null ? Ui.GREEN : Ui.RED);
            if (why != null) center(g, why, y + 143, Ui.RED);
        }
        return tip;
    }

    /** 위: 작은 칸 + 이름 + 부제 (잠재·불꽃) */
    private void small(GuiGraphics g, ItemStack s, String sub, int col) {
        Ui.slot(g, x + 126, y + 28);
        g.renderItem(s, x + 127, y + 29);
        g.drawString(font, Ui.cut(font, s.getHoverName().getString(), 74), x + 147, y + 29, Ui.VAL, true);
        g.drawString(font, Ui.cut(font, sub, 74), x + 147, y + 39, col, false);
    }

    private void box(GuiGraphics g, int bx, String title, int col, List<Component> lines, boolean on) {
        g.fill(bx, y + 52, bx + 47, y + 112, Ui.IN1);
        int e = on ? Ui.G4 : Ui.SEP;
        g.fill(bx, y + 52, bx + 47, y + 53, e); g.fill(bx, y + 111, bx + 47, y + 112, e); g.fill(bx, y + 52, bx + 1, y + 112, e); g.fill(bx + 46, y + 52, bx + 47, y + 112, e);
        String t = Ui.cut(font, title, 44);
        g.drawString(font, t, bx + 24 - font.width(t) / 2, y + 55, col, true);
        int yy = y + 67;
        for (Component c : lines) { g.drawString(font, Ui.cut(font, c.getString(), 43), bx + 3, yy, Ui.TXT, false); yy += 10; }
    }

    private void renderPot(GuiGraphics g, ItemStack s, GearDefs.GearDef d) {
        if (!rpg.core.equip.Potential.can(d)) { small(g, s, "잠재능력 불가", Ui.RED); return; }
        if (!rpg.core.equip.Potential.has(s)) {
            small(g, s, "잠재능력 없음", Ui.GRAY);
            center(g, "부여 주문서로", y + 80, Ui.GRAY); center(g, "처음 부여해요", y + 92, Ui.GRAY);
            return;
        }
        var pt = rpg.core.equip.Potential.pot(s);
        int gr = Math.max(0, Math.min(3, pt.getInt("g")));
        int gc = 0xFF000000 | rpg.core.equip.Potential.COLOR[gr];
        boolean pend = rpg.core.equip.Potential.pending(s);
        small(g, s, "잠재 [" + rpg.core.equip.Potential.GRADE[gr] + "]", gc);
        if (pend) {
            var nw = pt.getCompound("new");
            int ng = Math.max(0, Math.min(3, nw.getInt("g")));
            box(g, x + 125, "이전", gc, rpg.core.equip.Potential.lines(pt), false);
            box(g, x + 174, (ng > gr ? "▲" : "") + rpg.core.equip.Potential.GRADE[ng], 0xFF000000 | rpg.core.equip.Potential.COLOR[ng], rpg.core.equip.Potential.lines(nw), true);
            powLine(g, s, "rpgPowPot", y + 118);
            if (confirmKeep && ng > gr) { center(g, "오른 등급이 사라져요", y + 130, 0xFFFF8040); center(g, "정말 이전 유지?", y + 141, 0xFFFF8040); }
        } else {
            int yy = y + 56;
            for (var c : rpg.core.equip.Potential.lines(pt)) { g.drawString(font, Ui.cut(font, c.getString(), 94), x + 127, yy, Ui.TXT, false); yy += 11; }
            if (gr < 3) center(g, "등급 상승 " + fmt(rpg.core.equip.Potential.upChance[gr]) + "% · 천장 " + rpg.core.equip.Potential.ceil(s)[gr] + "/" + rpg.core.equip.Potential.ceiling[gr], y + 124, Ui.GRAY);
            long c = rpg.core.equip.Potential.cost(d, gr);
            center(g, "골드 " + shortG(c) + " 또는 큐브 1개", y + 138, Ui.VAL);
        }
    }

    private void renderFlame(GuiGraphics g, ItemStack s, GearDefs.GearDef d) {
        if (!rpg.core.equip.Flame.can(d)) { small(g, s, "추가 옵션 불가", Ui.RED); return; }
        if (Enhance.broken(s)) { small(g, s, "파괴된 장비예요", Ui.RED); return; }
        boolean pend = rpg.core.equip.Flame.pending(s);
        small(g, s, pend ? "검은 불꽃: 골라요" : "추가 옵션", pend ? Ui.RED : Ui.BLUE);
        if (pend) {
            var nw = rpg.core.equip.Flame.pendingTag(s);
            box(g, x + 125, "이전", Ui.GRAY, flameList(Gear.opts(s), rpg.core.equip.Flame.tiers(s)), false);
            box(g, x + 174, "새", Ui.VAL, flameList(nw.getCompound("o"), nw.getCompound("t")), true);
            powLine(g, s, "rpgPowFlame", y + 118);
        } else {
            int yy = y + 56;
            for (Component c : flameList(Gear.opts(s), rpg.core.equip.Flame.tiers(s))) { g.drawString(font, Ui.cut(font, c.getString(), 94), x + 127, yy, Ui.BLUE, false); yy += 11; }
            long c = rpg.core.equip.Flame.cost(d);
            center(g, "불꽃 1개 + " + shortG(c), y + 140, ClientData.gold >= c ? Ui.VAL : Ui.RED);
        }
    }

    private static List<Component> flameList(net.minecraft.nbt.CompoundTag o, net.minecraft.nbt.CompoundTag t) {
        List<Component> l = new ArrayList<>();
        if (o.isEmpty()) l.add(Component.literal("없음"));
        for (String k : o.getAllKeys()) l.add(Component.literal(rpg.core.equip.Flame.text(k, o.getInt(k), t.getInt(k))));
        return l;
    }

    /** 전투력 변화 (서버가 계산해서 장비 복사본에 붙여 보냄) */
    private void powLine(GuiGraphics g, ItemStack s, String key, int yy) {
        String w = where(curLoc());
        boolean weapon = "weapon".equals(Gear.slotOf(s));
        if (!w.equals("착용") && !weapon) { center(g, "입으면 전투력 표시", yy, Ui.GRAY); return; }
        long dlt = s.hasTag() ? s.getTag().getLong(key) : 0;
        String t = dlt == 0 ? "전투력 변화 없음" : String.format("전투력 %+,d %s", dlt, dlt > 0 ? "▲" : "▼");
        center(g, t, yy, dlt > 0 ? Ui.GREEN : dlt < 0 ? Ui.RED : Ui.GRAY);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (bag) {
            int bx = x + 76, by = y + 40;
            List<Integer> l = bagIdx();
            for (int k = 0; k < l.size() && k < 28; k++) {
                int cx = bx + 9 + (k % 7) * 18, cy = by + 21 + (k / 7) * 19;
                if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) { sel = l.get(k); selScroll = -1; bag = false; rebuildWidgets(); return true; }
            }
            if (mx >= bx && mx < bx + 146 && my >= by && my < by + 110) return true;
            if (super.mouseClicked(mx, my, button)) return true;
            bag = false; rebuildWidgets();
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = 0; i < KINDS.length; i++) {
            int ry = y + 30 + i * 15;
            if (mx >= x + 72 && mx < x + 120 && my >= ry - 2 && my < ry + 12) { tab = i; lastTab = i; selScroll = -1; rebuildWidgets(); return true; }
        }
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 5; r++) {
                int cx = x + 228 + c * 18, cy = y + 41 + r * 19, i = idxOf(GRID[c][r]);
                if (i >= 0 && mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) { sel = i; selScroll = -1; rebuildWidgets(); return true; }
            }
        if (tab == 1) {
            int px = x + 127, py = y + 28 + 34 + 16;
            for (int i = 0; i < scrolls.size() && i < 10; i++) {
                int cx = px + (i % 5) * 19, cy = py + (i / 5) * 19;
                if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) { selScroll = i; rebuildWidgets(); return true; }
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
