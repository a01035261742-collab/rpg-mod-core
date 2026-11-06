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

    // ───────── 스타포스 연출 (마법진 회전 · 누르면 빨라짐 · 결과 섬광) ─────────
    private static final net.minecraft.resources.ResourceLocation CIRCLE = new net.minecraft.resources.ResourceLocation("rpgcore", "textures/gui/ui/magic_circle.png");
    private static boolean skipFx;
    private static double ang;
    private static long lastFrame, pressAt, fxAt;
    private static int fxKind, prevStar = -1, prevLoc = -1;   // fxKind 1 성공 · 2 실패 · 3 파괴
    private static boolean fxSound;
    private static final int CHARGE = 800, SHOW = 1700;

    private void startFx(int st) {
        pressAt = System.currentTimeMillis(); fxKind = 0; fxAt = 0; fxSound = false;
        prevStar = st; prevLoc = curLoc();
        if (!skipFx) Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f, 0.8f));
    }

    /** 연출 중엔 강화 버튼 막기 (연타 방지) */
    private static boolean busy() {
        long now = System.currentTimeMillis();
        if (pressAt == 0) return false;
        if (fxKind == 0) return now - pressAt < 5000;   // 결과 기다리는 중
        return !skipFx && now < fxAt + SHOW;
    }

    /** 서버 새 정보 → 결과 판정 */
    private void judge() {
        if (pressAt == 0 || fxKind != 0 || prevLoc < 0) return;
        int i = locs.indexOf(prevLoc);
        if (i < 0) return;
        ItemStack s = gear.get(i);
        fxKind = Enhance.broken(s) ? 3 : Enhance.star(s) > prevStar ? 1 : 2;
        fxAt = skipFx ? System.currentTimeMillis() : Math.max(System.currentTimeMillis(), pressAt + CHARGE);
    }

    private static void sound(net.minecraft.sounds.SoundEvent e, float pitch) {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(e, pitch, 0.9f));
    }

    /** 마법진 + 효과 (가운데 cx, cy) */
    private void circle(GuiGraphics g, int cx, int cy) {
        long now = System.currentTimeMillis();
        long dt = lastFrame == 0 ? 0 : Math.min(100, now - lastFrame);
        lastFrame = now;
        boolean charging = pressAt > 0 && (fxKind == 0 || now < fxAt) && now - pressAt < 5000 && !skipFx;
        double k = charging ? Math.min(1, (now - pressAt) / (double) CHARGE) : 0;
        ang += (18 + k * k * 520) * dt / 1000.0;   // 시계 방향
        var ps = g.pose();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        if (charging) {   // 빛남 (조금 큰 마법진을 겹쳐)
            ps.pushPose(); ps.translate(cx, cy, 0); ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) -ang * 0.7f));
            g.setColor(1f, 0.9f, 0.6f, (float) (0.25 + 0.45 * k));
            g.blit(CIRCLE, -62, -62, 124, 124, 0, 0, 128, 128, 128, 128);
            g.setColor(1f, 1f, 1f, 1f);
            ps.popPose();
        }
        ps.pushPose(); ps.translate(cx, cy, 0); ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) ang));
        g.blit(CIRCLE, -56, -56, 112, 112, 0, 0, 128, 128, 128, 128);
        ps.popPose();
        // 결과
        if (fxKind != 0 && now >= fxAt) {
            if (!fxSound) {
                fxSound = true;
                if (fxKind == 1) sound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2f);
                else if (fxKind == 2) sound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.7f);
                else sound(net.minecraft.sounds.SoundEvents.ANVIL_DESTROY, 0.8f);
            }
            long t = now - fxAt;
            int show = skipFx ? 900 : SHOW;
            if (t < show) {
                float f = t / (float) show;
                if (!skipFx && fxKind != 2 && t < 700) {   // 섬광 + 가루
                    float a = 1f - t / 700f;
                    int col = fxKind == 1 ? 0xFFE68C : 0xFF3030;
                    ps.pushPose(); ps.translate(cx, cy, 0);
                    float sc = 1f + t / 700f * 0.6f;
                    ps.scale(sc, sc, 1);
                    g.setColor(((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f, a * 0.8f);
                    g.blit(CIRCLE, -56, -56, 112, 112, 0, 0, 128, 128, 128, 128);
                    g.setColor(1f, 1f, 1f, 1f);
                    ps.popPose();
                    java.util.Random r = new java.util.Random(7);
                    for (int i = 0; i < 36; i++) {
                        double dir = r.nextDouble() * Math.PI * 2, sp = 30 + r.nextDouble() * 60;
                        double d = sp * t / 700.0;
                        int px = (int) (cx + Math.cos(dir) * d), py = (int) (cy + Math.sin(dir) * d + (fxKind == 3 ? t * t / 9000.0 : 0));
                        int pc = fxKind == 1 ? (i % 3 == 0 ? 0xFFFFFFFF : 0xFFFFE070) : (i % 2 == 0 ? 0xFFDC3C3C : 0xFF786050);
                        int al = (int) (255 * a);
                        if (al > 8) g.fill(px, py, px + 2, py + 2, (al << 24) | (pc & 0xFFFFFF));
                    }
                }
                if (fxKind == 2 && !skipFx) g.fill(cx - 9, cy - 9, cx + 9, cy + 9, ((int) (140 * Math.min(1, t / 400f)) << 24));
                // 글자 (끝에 서서히 사라짐)
                String lb = fxKind == 1 ? "강화 성공!" : fxKind == 2 ? "강화 실패" : "장비 파괴!";
                int lc = fxKind == 1 ? 0xFFD86A : fxKind == 2 ? 0xAA968F : 0xFF6060;
                float alpha = f < 0.6f ? 1f : 1f - (f - 0.6f) / 0.4f;
                int al = (int) (255 * alpha);
                if (al > 10) {
                    ps.pushPose(); ps.translate(cx, cy + 40, 300); ps.scale(2f, 2f, 1);
                    int w = font.width(lb);
                    int oc = (al << 24) | 0x28080C;
                    for (int[] o : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) g.drawString(font, lb, -w / 2 + o[0], o[1], oc, false);
                    g.drawString(font, lb, -w / 2, 0, (al << 24) | lc, false);
                    ps.popPose();
                }
            } else if (pressAt != 0 && t >= show) { pressAt = 0; rebuildWidgets(); }
        }
    }

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
            es.judge();
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
    private static final int CX = 262, BY = 262;
    /** 입은 장비 칸: 열마다 위→아래 (위치 번호) — 1열 모자·상의·하의·신발·무기 / 2열 장갑·망토·벨트·펜던트·훈장 / 3열 반지 4·귀걸이 */
    private static final int[][] GRID = {{103, 102, 101, 100, 0}, {201, 202, 203, 209, 210}, {205, 206, 207, 208, 204}};

    private int idxOf(int loc) { return locs.indexOf(loc); }

    private List<Integer> bagIdx() {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < locs.size(); i++) { int lc = locs.get(i); if (lc > 0 && lc < 36) l.add(i); }
        return l;
    }

    private Button btn(String t, int bx, int w, boolean accent, Button.OnPress p) {
        Button b = Ui.button(Component.literal(t), p).bounds(bx, y + BY, w, 16).accent(accent).build();
        addRenderableWidget(b);
        return b;
    }

    @Override
    protected void init() {
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.ENHANCE);
        addRenderableWidget(Ui.button(Component.literal("가방"), b -> { bag = !bag; rebuildWidgets(); })
                .bounds(x + 380, y + BY, 80, 16).accent(bag).build());
        if (bag) return;   // 가방 팝업 동안은 강화 버튼 숨김
        if (tab == 0) addRenderableWidget(Ui.button(Component.literal((skipFx ? "☑" : "☐") + " 연출 끄기"), b -> { skipFx = !skipFx; rebuildWidgets(); })
                .bounds(x + CX + 40, y + BY + 1, 66, 14).build());

        ItemStack s = cur();
        GearDefs.GearDef d = Gear.def(s);
        int c1 = x + CX - 30, l2 = x + CX - 56, r2 = x + CX + 6;
        if (tab == 0 && d != null) {
            if (Enhance.broken(s)) {
                btn("복구", c1, 60, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(3, curLoc(), material())))
                        .active = material() >= 0 && ClientData.gold >= Enhance.restoreCost(d);
            } else {
                int st = Enhance.star(s);
                boolean can = st < Enhance.maxStar(d);
                if (can && Enhance.destroyRate(st) > 0) {
                    addRenderableWidget(Ui.button(Component.literal((guard ? "☑" : "☐") + " 파괴 방지"), bt -> { guard = !guard; rebuildWidgets(); })
                            .bounds(x + CX - 40, y + 240, 80, 14).build());
                }
                btn("강화", c1, 60, true, bt -> { startFx(st); Net.CH.sendToServer(new Net.EnhanceAction(1, curLoc(), guard ? 1 : 0)); rebuildWidgets(); })
                        .active = can && ClientData.gold >= Enhance.cost(d, st, guard) && !busy();
            }
        } else if (tab == 1 && d != null) {
            btn("주문서 바르기", x + CX - 40, 80, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(2, curLoc(), slots.get(selScroll))))
                    .active = selScroll >= 0 && selScroll < scrolls.size() && Enhance.scrollBlock(s, scrolls.get(selScroll)) == null;
        } else if (tab == 3 && d != null && rpg.core.equip.Potential.can(d)) {
            if (!rpg.core.equip.Potential.has(s)) {
                btn("레어 (" + extra.get(1) + ")", l2, 50, false, bt -> Net.CH.sendToServer(new Net.EnhanceAction(5, curLoc(), 0))).active = extra.get(1) > 0;
                btn("에픽 (" + extra.get(2) + ")", r2, 50, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(5, curLoc(), 1))).active = extra.get(2) > 0;
            } else if (rpg.core.equip.Potential.pending(s)) {
                String key = curLoc() + ":" + rpg.core.equip.Potential.pot(s).getCompound("new");
                boolean up = rpg.core.equip.Potential.pendingUp(s);
                if (!key.equals(pendKey)) { pendKey = key; readyAt = up ? System.currentTimeMillis() + 700 : 0; confirmKeep = false; }
                waiting = System.currentTimeMillis() < readyAt;
                Button b1, b2;
                if (up && confirmKeep) {
                    b1 = btn("취소", l2, 50, false, bt -> { confirmKeep = false; rebuildWidgets(); });
                    b2 = btn("유지할게요", r2, 50, false, bt -> { confirmKeep = false; Net.CH.sendToServer(new Net.EnhanceAction(9, curLoc(), 1)); });
                } else {
                    b1 = btn("이전 유지", l2, 50, false, bt -> {
                        if (up) { confirmKeep = true; readyAt = System.currentTimeMillis() + 700; rebuildWidgets(); }
                        else Net.CH.sendToServer(new Net.EnhanceAction(9, curLoc(), 0));
                    });
                    b2 = btn(up ? "새 옵션▲" : "새 옵션", r2, 50, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(8, curLoc(), 0)));
                }
                b1.active = b2.active = !waiting;
            } else {
                long c = rpg.core.equip.Potential.cost(d, rpg.core.equip.Potential.grade(s));
                btn(shortG(c), l2, 50, false, bt -> Net.CH.sendToServer(new Net.EnhanceAction(6, curLoc(), 0))).active = ClientData.gold >= c;
                btn("큐브 " + extra.get(0), r2, 50, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(7, curLoc(), 0))).active = extra.get(0) > 0;
            }
        } else if (tab == 4 && d != null && rpg.core.equip.Flame.can(d) && !Enhance.broken(s)) {
            if (rpg.core.equip.Flame.pending(s)) {
                btn("이전 유지", l2, 50, false, bt -> Net.CH.sendToServer(new Net.EnhanceAction(11, curLoc(), 1)));
                btn("새 옵션", r2, 50, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(11, curLoc(), 0)));
            } else {
                long c = rpg.core.equip.Flame.cost(d);
                btn("불꽃 쓰기", x + CX - 35, 70, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(10, curLoc(), flameKind)))
                        .active = extra.get(6 + flameKind) > 0 && ClientData.gold >= c;
            }
        } else if (tab == 2) {
            btn("합성 (" + fragCost() + ")", c1 - 10, 80, true, bt -> Net.CH.sendToServer(new Net.EnhanceAction(4, fragKind, 0))).active = frags >= fragCost();
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
        String c = Ui.cut(font, t, 200);
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
            int ry = y + 36 + i * 20;
            boolean on = mx >= x + 85 && mx < x + 148 && my >= ry - 4 && my < ry + 13;
            if (i == tab || on) Ui.row(g, x + 85, ry - 4, x + 148, ry + 13, i == tab);
            g.drawString(font, KINDS[i], x + 91, ry, i == tab ? Ui.VAL : Ui.GRAY, i == tab);
        }
        Ui.vsep(g, x + 152, y + 28, y + 282);
        Ui.vsep(g, x + 372, y + 28, y + 282);
        // 오른쪽: 입은 장비
        g.drawString(font, "입은 장비", x + 418 - font.width("입은 장비") / 2, y + 34, Ui.TXT, true);
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 5; r++) {
                int lc = GRID[c][r], i = idxOf(lc), cx = x + 384 + c * 26, cy = y + 50 + r * 26;
                ItemStack it = i >= 0 ? gear.get(i) : ItemStack.EMPTY;
                cell(g, cx, cy, it, i >= 0 && i == sel);
                if (i >= 0 && mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18 && !bag) tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), it));
            }
        // 가운데
        if (!bag) {
            ItemStack s = cur();
            GearDefs.GearDef d = Gear.def(s);
            if (tab == 2) renderFrag(g);
            else if (d == null) { center(g, "강화할 장비를", y + 130, Ui.GRAY); center(g, "오른쪽에서 고르세요", y + 144, Ui.GRAY); }
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
        int bx = x + 160, by = y + 46, bw = 206, bh = 160;
        Ui.window(g, bx, by, bw, bh);
        g.drawString(font, "가방 속 장비", bx + 10, by + 8, Ui.VAL, true);
        List<Integer> l = bagIdx();
        List<Component> tip = null;
        for (int k = 0; k < l.size() && k < 48; k++) {
            int i = l.get(k), cx = bx + 11 + (k % 8) * 23, cy = by + 24 + (k / 8) * 22;
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

    /** 조각 합성: 0 무기 주문서 · 1 스탯 주문서 */
    private static int fragKind = 0;
    private static final String[] GR_NAME = {"100%", "60%", "30%", "10%"};

    private int fragCost() { return fragKind == 0 ? Enhance.fragWeapon : Enhance.fragments; }

    private void renderFrag(GuiGraphics g) {
        center(g, "종류를 고르면 그 안에서 무작위", y + 34, Ui.VAL);
        String[][] k = {{"무기 주문서", "공격력 · 마력"}, {"스탯 주문서", "힘·민첩·지력·운·체력"}};
        String[] icon = {"warrior_sword_40", "warrior_top_40"};
        for (int i = 0; i < 2; i++) {
            int bx = x + CX - 100 + i * 104, by = y + 48;
            boolean on = i == fragKind;
            g.fill(bx, by, bx + 96, by + 76, Ui.IN1);
            int e = on ? Ui.G4 : Ui.SEP;
            g.fill(bx, by, bx + 96, by + 1, e); g.fill(bx, by + 75, bx + 96, by + 76, e); g.fill(bx, by, bx + 1, by + 76, e); g.fill(bx + 95, by, bx + 96, by + 76, e);
            var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("rpgcore", icon[i]));
            Ui.slot(g, bx + 39, by + 6);
            if (it != null) g.renderItem(new ItemStack(it), bx + 40, by + 7);
            g.drawCenteredString(font, k[i][0], bx + 48, by + 30, on ? Ui.VAL : Ui.TXT);
            int cost = i == 0 ? Enhance.fragWeapon : Enhance.fragments;
            g.drawCenteredString(font, "조각 " + cost, bx + 48, by + 44, frags >= cost ? Ui.GREEN : Ui.GRAY);
            g.drawCenteredString(font, Ui.cut(font, k[i][1], 90), bx + 48, by + 58, Ui.GRAY);
        }
        g.drawString(font, "등급 확률", x + CX - 100, y + 134, Ui.GRAY, false);
        double[] ch = Enhance.gradeChance(ClientData.level);
        int row = 0;
        for (int i = 0; i < ch.length && i < GR_NAME.length; i++) {
            if (Enhance.synthWeight[i] <= 0) continue;   // 100% 는 합성에서 안 나옴
            int ly = y + 148 + row++ * 12;
            g.drawString(font, (fragKind == 0 ? "공격력·마력 " : "스탯 ") + GR_NAME[i], x + CX - 92, ly, ch[i] > 0 ? Ui.TXT : Ui.GRAY, false);
            String p = fmt(Math.round(ch[i] * 10) / 10.0) + "%";
            g.drawString(font, p, x + CX + 100 - font.width(p), ly, ch[i] > 0 ? Ui.VAL : Ui.GRAY, false);
        }
        center(g, "가진 조각 " + frags, y + 204, frags >= fragCost() ? Ui.GREEN : Ui.RED);
        Ui.bar(g, x + CX - 60, y + 218, 120, 4, frags / (float) Math.max(1, fragCost()), 0xFF7CE07C);
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
        if (max > 0) stars(g, Math.min(st, max), max, y + 34);
        int ccy = y + 102;
        circle(g, x + CX, ccy);
        Ui.slot(g, x + CX - 9, ccy - 9);
        g.renderItem(s, x + CX - 8, ccy - 8);
        Ui.selBox(g, x + CX - 9, ccy - 9, 18, 18);
        center(g, s.getHoverName().getString(), y + 164, Enhance.broken(s) ? Ui.RED : Ui.VAL);
        int yy = y + 178;
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
        center(g, String.format("%,d G", c), yy + 34, ClientData.gold >= c ? Ui.VAL : Ui.RED);
    }

    private List<Component> renderScroll(GuiGraphics g, ItemStack s, GearDefs.GearDef d, int mx, int my, List<Component> tip) {
        int yy = head(g, s, y + 54) + 14;
        int um = Enhance.upgMax(d);
        if (um == 0) { center(g, "주문서 사용 불가", yy, Ui.RED); return tip; }
        int fail = Enhance.upgFail(s), left = Enhance.upgLeft(s), done = Math.max(0, um - left - fail);
        int pw = Math.min(16, 200 / Math.max(1, um)), px0 = x + CX - (um * pw) / 2;
        for (int i = 0; i < um; i++) {   // 성공 초록 · 실패 빨강 · 남은 칸 빈칸
            int c = i < done ? 0xFF7CE07C : i < done + fail ? 0xFFDC3C3C : 0xFF281C18;
            g.fill(px0 + i * pw, yy - 14, px0 + i * pw + pw - 2, yy - 7, Ui.G3);
            g.fill(px0 + i * pw + 1, yy - 13, px0 + i * pw + pw - 3, yy - 8, c);
        }
        center(g, "업그레이드 남은 " + left + "/" + um + " · 실패 " + fail, yy, Ui.TXT);
        Ui.hsep(g, x + 170, x + 360, yy + 12);
        int px = x + 217, py = yy + 16;
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
            center(g, scrolls.get(selScroll).getHoverName().getString(), y + 158, why == null ? Ui.GREEN : Ui.RED);
            if (why != null) center(g, why, y + 169, Ui.RED);
        }
        return tip;
    }

    /** 위: 작은 칸 + 이름 + 부제 (잠재·불꽃) */
    private static final net.minecraft.resources.ResourceLocation FLAME_RING = new net.minecraft.resources.ResourceLocation("rpgcore", "textures/gui/ui/flame_ring.png");

    /** 잠재: 등급 색 빛무리 두 겹 */
    private void aura(GuiGraphics g, int cx, int cy, int col) {
        for (int r : new int[]{22, 26}) {
            for (int a = 0; a < 160; a++) {
                double t = a / 160.0 * Math.PI * 2;
                int px = (int) Math.round(cx + Math.cos(t) * r), py = (int) Math.round(cy + Math.sin(t) * r);
                g.fill(px, py, px + 1, py + 1, (r == 22 ? 0xE0000000 : 0x80000000) | (col & 0xFFFFFF));
            }
        }
    }

    /** 불꽃: 불꽃 고리 (살짝 일렁임) */
    private void flameRing(GuiGraphics g, int cx, int cy) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        float k = 1f + 0.03f * (float) Math.sin(System.currentTimeMillis() / 160.0);
        g.pose().pushPose(); g.pose().translate(cx, cy, 0); g.pose().scale(k, k, 1);
        g.blit(FLAME_RING, -48, -48, 96, 96, 0, 0, 96, 96, 96, 96);
        g.pose().popPose();
    }

    private void small(GuiGraphics g, ItemStack s, String sub, int col) {
        int cx = x + CX, cy = y + 70;
        if (tab == 3) aura(g, cx, cy, 0xFFFFD86A);   // 시안: 금빛 두 겹 고리
        else if (tab == 4) flameRing(g, cx, cy);
        Ui.slot(g, cx - 14, cy - 14, 28);
        g.pose().pushPose(); g.pose().translate(cx - 12, cy - 12, 0); g.pose().scale(1.5f, 1.5f, 1);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
        center(g, s.getHoverName().getString() + "  §7" + sub, y + 124, Ui.VAL);
    }


    private void box(GuiGraphics g, int bx, String title, int col, List<Component> lines, boolean on) {
        int bw = 100, t0 = y + 138, t1 = y + 210;
        g.fill(bx, t0, bx + bw, t1, Ui.IN1);
        int e = on ? Ui.G4 : Ui.SEP;
        g.fill(bx, t0, bx + bw, t0 + 1, e); g.fill(bx, t1 - 1, bx + bw, t1, e); g.fill(bx, t0, bx + 1, t1, e); g.fill(bx + bw - 1, t0, bx + bw, t1, e);
        String t = Ui.cut(font, title, bw - 4);
        g.drawString(font, t, bx + bw / 2 - font.width(t) / 2, t0 + 4, col, true);
        int yy = t0 + 17;
        for (Component c : lines) { g.drawString(font, Ui.cut(font, c.getString(), bw - 6), bx + 4, yy, Ui.TXT, false); yy += 11; }
    }

    private void renderPot(GuiGraphics g, ItemStack s, GearDefs.GearDef d) {
        if (!rpg.core.equip.Potential.can(d)) { small(g, s, "잠재능력 불가", Ui.RED); return; }
        if (!rpg.core.equip.Potential.has(s)) {
            small(g, s, "잠재능력 없음", Ui.GRAY);
            center(g, "부여 주문서로", y + 166, Ui.GRAY); center(g, "처음 부여해요", y + 178, Ui.GRAY);
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
            box(g, x + 158, "이전", gc, rpg.core.equip.Potential.lines(pt), false);
            box(g, x + 266, (ng > gr ? "▲" : "") + rpg.core.equip.Potential.GRADE[ng], 0xFF000000 | rpg.core.equip.Potential.COLOR[ng], rpg.core.equip.Potential.lines(nw), true);
            powLine(g, s, "rpgPowPot", y + 218);
            if (confirmKeep && ng > gr) { center(g, "오른 등급이 사라져요", y + 232, 0xFFFF8040); center(g, "정말 이전 유지?", y + 244, 0xFFFF8040); }
        } else {
            int yy = y + 142;
            for (var c : rpg.core.equip.Potential.lines(pt)) { g.drawString(font, Ui.cut(font, c.getString(), 190), x + 170, yy, Ui.TXT, false); yy += 11; }
            if (gr < 3) center(g, "등급 상승 " + fmt(rpg.core.equip.Potential.upChance[gr]) + "% · 천장 " + rpg.core.equip.Potential.ceil(s)[gr] + "/" + rpg.core.equip.Potential.ceiling[gr], y + 210, Ui.GRAY);
            long c = rpg.core.equip.Potential.cost(d, gr);
            center(g, "골드 " + shortG(c) + " 또는 큐브 1개", y + 224, Ui.VAL);
        }
    }

    private static int flameKind = 1;

    private void renderFlame(GuiGraphics g, ItemStack s, GearDefs.GearDef d) {
        if (!rpg.core.equip.Flame.can(d)) { small(g, s, "추가 옵션 불가", Ui.RED); return; }
        if (Enhance.broken(s)) { small(g, s, "파괴된 장비예요", Ui.RED); return; }
        boolean pend = rpg.core.equip.Flame.pending(s);
        small(g, s, pend ? "검은 불꽃: 골라요" : "추가 옵션", pend ? Ui.RED : Ui.BLUE);
        if (pend) {
            var nw = rpg.core.equip.Flame.pendingTag(s);
            box(g, x + 158, "이전", Ui.GRAY, flameList(Gear.opts(s), rpg.core.equip.Flame.tiers(s)), false);
            box(g, x + 266, "새", Ui.VAL, flameList(nw.getCompound("o"), nw.getCompound("t")), true);
            powLine(g, s, "rpgPowFlame", y + 218);
        } else {
            // 시안: 옵션 줄 = 이름 · 단계 칸 7개 · 수치
            var o = Gear.opts(s); var t = rpg.core.equip.Flame.tiers(s);
            int yy = y + 140;
            if (o.isEmpty()) center(g, "추가 옵션 없음", yy, Ui.GRAY);
            for (String k : o.getAllKeys()) {
                int n = t.getInt(k), v = o.getInt(k);
                g.drawString(font, Ui.cut(font, GearDefs.label(k), 90), x + 166, yy, Ui.TXT, false);
                for (int i = 0; i < 7; i++) {
                    int px = x + 258 + i * 10;
                    g.fill(px, yy, px + 8, yy + 8, i < n ? 0xFFF0A030 : 0xFF3A2626);
                    if (i < n) g.fill(px, yy, px + 8, yy + 1, 0xFFFFD070);
                }
                String vs = (k.equals("reqLv") ? "-" : "+") + v + (k.endsWith("Pct") ? "%" : "");
                g.drawString(font, vs, x + 364 - font.width(vs), yy, Ui.BLUE, false);
                yy += 12;
            }
            // 불꽃 고르기 (아이콘 + 개수)
            String[] kn = {"powerful", "eternal", "black"};
            for (int i = 0; i < 3; i++) {
                int ix = x + CX - 51 + i * 40, iy = y + 194;
                Ui.slot(g, ix, iy, 22);
                if (i == flameKind) { int e = Ui.G4; g.fill(ix - 1, iy - 1, ix + 23, iy, e); g.fill(ix - 1, iy + 22, ix + 23, iy + 23, e); g.fill(ix - 1, iy, ix, iy + 22, e); g.fill(ix + 22, iy, ix + 23, iy + 22, e); }
                g.renderItem(new ItemStack(rpg.core.equip.Flame.item(kn[i])), ix + 3, iy + 3);
                String cnt = String.valueOf(extra.get(6 + i));
                g.pose().pushPose(); g.pose().translate(0, 0, 200);
                g.drawString(font, cnt, ix + 21 - font.width(cnt), iy + 13, extra.get(6 + i) > 0 ? Ui.TXT : Ui.RED, true);
                g.pose().popPose();
            }
            long c = rpg.core.equip.Flame.cost(d);
            String[] nm = {"강력한 불꽃", "영원한 불꽃", "검은 불꽃"};
            center(g, nm[flameKind] + " 1개 + " + shortG(c), y + 228, ClientData.gold >= c ? Ui.VAL : Ui.RED);
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
            int bx = x + 160, by = y + 46;
            List<Integer> l = bagIdx();
            for (int k = 0; k < l.size() && k < 48; k++) {
                int cx = bx + 11 + (k % 8) * 23, cy = by + 24 + (k / 8) * 22;
                if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) { sel = l.get(k); selScroll = -1; bag = false; rebuildWidgets(); return true; }
            }
            if (mx >= bx && mx < bx + 206 && my >= by && my < by + 160) return true;
            if (super.mouseClicked(mx, my, button)) return true;
            bag = false; rebuildWidgets();
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == 4) for (int i = 0; i < 3; i++) {
            int ix = x + CX - 51 + i * 40, iy = y + 194;
            if (mx >= ix && mx < ix + 22 && my >= iy && my < iy + 22) { flameKind = i; rebuildWidgets(); return true; }
        }
        for (int i = 0; i < KINDS.length; i++) {
            int ry = y + 36 + i * 20;
            if (mx >= x + 85 && mx < x + 148 && my >= ry - 4 && my < ry + 13) { tab = i; lastTab = i; selScroll = -1; rebuildWidgets(); return true; }
        }
        for (int c = 0; c < 3; c++)
            for (int r = 0; r < 5; r++) {
                int cx = x + 384 + c * 26, cy = y + 50 + r * 26, i = idxOf(GRID[c][r]);
                if (i >= 0 && mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) { sel = i; selScroll = -1; rebuildWidgets(); return true; }
            }
        if (tab == 2) {
            for (int i = 0; i < 2; i++) {
                int bx = x + CX - 100 + i * 104, by = y + 48;
                if (mx >= bx && mx < bx + 96 && my >= by && my < by + 76) { fragKind = i; rebuildWidgets(); return true; }
            }
        }
        if (tab == 1) {
            int px = x + 217, py = y + 54 + 34 + 16;
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
