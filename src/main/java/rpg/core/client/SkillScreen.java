package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;
import rpg.core.skill.Defs;

/** 스킬 탭 (L): 1차 · 2차 · 패시브 + 스킬 줄 (시안 ui_d2) */
public class SkillScreen extends ScaledScreen {
    private static final int ROW = 36, TOP = 52, ROWS = 6;
    private static int tab = 0;
    private int x, y, rows, scroll;
    private final int W = CharWin.W;
    private final List<Defs.SkillDef> list = new ArrayList<>();

    public SkillScreen() {
        super(Component.literal("스킬"));
    }

    private static boolean available(Defs.SkillDef s) {
        if (s.job() != 0 && s.job() != ClientData.job) return false;
        if (s.tier() > ClientData.jobTier()) return false;
        return s.branch() == 0 || s.branch() == ClientData.branch;
    }

    private static boolean inTab(Defs.SkillDef s) {
        return tab == 0 ? s.tier() < 2 : s.tier() >= 2;   // 패시브도 1차·2차 안에
    }

    @Override
    protected void init() {
        clearWidgets();
        list.clear();
        for (Defs.SkillDef s : Defs.SKILLS.values()) if (available(s) && inTab(s)) list.add(s);
        rows = Math.min(ROWS, list.size());
        scroll = Math.max(0, Math.min(scroll, list.size() - rows));
        x = Ui.cx(width, W);
        y = Ui.cy(height, CharWin.H);
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.SKILL);
        String[] tn = {"1차", "2차"};
        if (tab > 1) tab = 0;
        for (int i = 0; i < 2; i++) {
            final int k = i;
            addRenderableWidget(Ui.button(Component.literal(tn[i]), b -> { tab = k; scroll = 0; init(); })
                    .bounds(x + 86 + i * 50, y + 30, 46, 16).accent(i == tab).build());
        }
        for (int r = 0; r < rows && r + scroll < list.size(); r++) {
            Defs.SkillDef sd = list.get(r + scroll);
            String id = sd.id();
            int lv = ClientData.skills.getOrDefault(id, 0);
            addRenderableWidget(Ui.button(Component.literal("+"), b -> Net.CH.sendToServer(new Net.Learn(0, id)))
                    .bounds(x + W - 42, y + TOP + r * ROW + 8, 18, 16).accent(ClientData.skillPoints > 0 && lv < sd.maxLevel()).build())
                    .active = ClientData.skillPoints > 0 && lv < sd.maxLevel();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CharWin.draw(g, font, x, y);
        String sp = "SP " + ClientData.skillPoints;
        g.drawString(font, sp, x + W - 20 - font.width(sp), y + 34, ClientData.skillPoints > 0 ? Ui.GREEN : Ui.GRAY, true);
        if (list.isEmpty()) g.drawString(font, tab == 1 ? "2차 전직 후 열려요" : "배울 수 있는 스킬이 없어요", x + 90, y + 70, Ui.GRAY, false);
        if (list.size() > rows) {
            String sc = (scroll + 1) + "-" + Math.min(list.size(), scroll + rows) + "/" + list.size() + " (휠)";
            g.drawString(font, sc, x + W - 30 - font.width(sp) - font.width(sc), y + 34, Ui.GRAY, false);
        }
        String hover = null;
        for (int r = 0; r < rows && r + scroll < list.size(); r++) {
            Defs.SkillDef s = list.get(r + scroll);
            int ry = y + TOP + r * ROW;
            int lv = ClientData.skills.getOrDefault(s.id(), 0);
            boolean on = mx >= x + 85 && mx < x + W - 46 && my >= ry && my < ry + ROW - 3;
            Ui.row(g, x + 85, ry, x + W - 20, ry + ROW - 3, on);
            Ui.slot(g, x + 89, ry + 4, 25);
            g.pose().pushPose(); g.pose().translate(x + 93, ry + 8, 0); g.pose().scale(17 / 16f, 17 / 16f, 1);
            SkillIcons.draw(g, s.id(), 0, 0);
            g.pose().popPose();
            if (lv == 0) g.fill(x + 91, ry + 6, x + 112, ry + 27, 0x90000000);
            g.drawString(font, Ui.cut(font, s.name() + (s.passive() ? " §7(패시브)" : ""), 90), x + 120, ry + 5, lv > 0 ? Ui.TXT : Ui.GRAY, true);
            g.drawString(font, lv > 0 ? "Lv " + lv + "/" + s.maxLevel() + (lv >= s.maxLevel() ? "  MAX" : "") : "안 배움", x + 120, ry + 18, lv > 0 ? Ui.VAL : Ui.GRAY, false);
            g.drawString(font, Ui.cut(font, s.describe(Math.max(1, lv)), 150), x + 216, ry + 5, Ui.GRAY, false);
            String sub = s.passive() ? "항상 켜짐" : "MP " + Defs.fmt(s.mp(Math.max(1, lv))) + " · 쿨 " + Defs.fmt(s.cooldown(Math.max(1, lv))) + "초";
            g.drawString(font, sub, x + 216, ry + 18, s.passive() ? Ui.GRAY : Ui.BLUE, false);
            String key = keyOf(s.id());
            if (!s.passive()) {
                int kx = x + W - 70;
                Ui.slot(g, kx, ry + 7);
                if (key != null) g.drawString(font, key, kx + 9 - font.width(key) / 2, ry + 12, Ui.VAL, true);
            }
            if (on) hover = s.id();
        }
        super.render(g, mx, my, pt);
        if (hover != null) {
            Defs.SkillDef s = Defs.SKILLS.get(hover);
            int lv = ClientData.skills.getOrDefault(s.id(), 0);
            String key = keyOf(s.id());
            List<Component> tip = new ArrayList<>();
            String tag = s.passive() ? "" : Defs.ATTACK.contains(s.id()) ? "  §c[공격]" : "  §9[보조]";   // 딜 스킬인지 편의 스킬인지
            tip.add(Component.literal("§6" + s.name() + tag + (lv > 0 ? "§6  Lv " + lv + "/" + s.maxLevel() : "")));
            tip.add(Component.literal("현재: " + (lv > 0 ? s.describe(lv) : "안 배움")));
            if (lv < s.maxLevel()) tip.add(Component.literal("§7다음: " + s.describe(lv + 1)));
            if (s.passive()) {
                tip.add(Component.literal("§8패시브: 배우면 항상 켜져요"));
            } else {
                tip.add(Component.literal("§bMP " + Defs.fmt(s.mp(Math.max(1, lv))) + " · 쿨타임 " + Defs.fmt(s.cooldown(Math.max(1, lv))) + "초"));
                tip.add(Component.literal(lv > 0 ? (key == null ? "§8마우스 올리고 Q E R F Z X = 그 칸에 넣기" : "§8" + key + " 칸 · 같은 키 = 빼기")
                        : "§8배워야 칸에 넣을 수 있어요"));
            }
            g.renderComponentTooltip(font, tip, mx, my);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String keyOf(String id) {
        for (int i = 0; i < 6; i++) if (id.equals(ClientData.slots[i])) return ClientEvents.SLOT_LABELS[i];
        return null;
    }

    private int mx, my;

    @Override
    public void mouseMoved(double mx, double my) {
        this.mx = (int) (mx / uiS);
        this.my = (int) (my / uiS);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int old = scroll;
        scroll = Math.max(0, Math.min(list.size() - rows, scroll - (int) Math.signum(delta)));
        if (scroll != old) init();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        // 마우스 올린 스킬 + 스킬 키 = 그 칸에 넣기
        for (int k = 0; k < 6; k++) {
            if (!ClientEvents.SKILL_KEYS[k].matches(key, scan)) continue;
            for (int r = 0; r < rows && r + scroll < list.size(); r++) {
                int ry = y + TOP + r * ROW;
                if (mx >= x + 85 && mx < x + W - 46 && my >= ry && my < ry + ROW - 3) {
                    Defs.SkillDef sd = list.get(r + scroll);
                    String id = sd.id();
                    if (!sd.passive() && ClientData.skills.getOrDefault(id, 0) > 0) Net.CH.sendToServer(new Net.SetSlot(k, id));
                    return true;
                }
            }
            return true;
        }
        if (ClientEvents.SKILL_WINDOW_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
