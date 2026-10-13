package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;
import rpg.core.skill.Defs;

/** 스킬 창 (L): 쓸 수 있는 스킬 목록 + 레벨업 버튼 */
public class SkillScreen extends Screen {
    private static final int W = 230, ROW = 26;
    private int x, y, h;
    private final List<Defs.SkillDef> list = new ArrayList<>();

    public SkillScreen() {
        super(Component.literal("스킬"));
    }

    private static boolean available(Defs.SkillDef s) {
        int tier = ClientData.jobTier();
        if (s.tier() == 0) return tier < 2;
        return s.tier() <= tier;
    }

    @Override
    protected void init() {
        list.clear();
        for (Defs.SkillDef s : Defs.SKILLS.values()) if (available(s)) list.add(s);
        h = 40 + Math.max(1, list.size()) * ROW + 24;
        x = width / 2 - W / 2;
        y = height / 2 - h / 2;
        for (int i = 0; i < list.size(); i++) {
            String id = list.get(i).id();
            addRenderableWidget(Button.builder(Component.literal("+"), b -> Net.CH.sendToServer(new Net.Learn(0, id)))
                    .bounds(x + W - 24, y + 30 + i * ROW + 4, 16, 14).build());
        }
        addRenderableWidget(Button.builder(Component.literal("패시브 트리 (N)"), b -> minecraft.setScreen(new PassiveScreen()))
                .bounds(x + W - 96, y + h - 20, 88, 14).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x, y, x + W, y + h, 0xF0141420);
        g.fill(x, y, x + W, y + 1, 0xFFFFD24A);
        g.drawString(font, "스킬", x + 8, y + 6, 0xFFFFD24A, true);
        String sp = "스킬 포인트: " + ClientData.skillPoints;
        g.drawString(font, sp, x + W - 8 - font.width(sp), y + 6, ClientData.skillPoints > 0 ? 0xFF80FF80 : 0xFFA0A0A0, true);
        g.drawString(font, ClientData.jobName() + " 스킬", x + 8, y + 18, 0xFFA0A0C0, false);
        if (list.isEmpty()) g.drawString(font, "배울 수 있는 스킬이 없어요", x + 8, y + 36, 0xFF808080, false);
        for (int i = 0; i < list.size(); i++) {
            Defs.SkillDef s = list.get(i);
            int ry = y + 30 + i * ROW;
            int lv = ClientData.skills.getOrDefault(s.id(), 0);
            g.fill(x + 6, ry, x + W - 6, ry + ROW - 2, 0x40FFFFFF);
            SkillIcons.draw(g, s.id(), x + 9, ry + 3);
            g.drawString(font, s.name() + "  " + lv + "/" + s.maxLevel(), x + 30, ry + 2, lv > 0 ? 0xFFFFFFFF : 0xFF909090, true);
            g.drawString(font, s.describe(Math.max(1, lv)), x + 30, ry + 13, 0xFFB0B0B0, false);
            if (mx >= x + 6 && mx < x + W - 30 && my >= ry && my < ry + ROW - 2) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(s.name()));
                tip.add(Component.literal("현재: " + (lv > 0 ? s.describe(lv) : "안 배움")));
                if (lv < s.maxLevel()) tip.add(Component.literal("다음: " + s.describe(lv + 1)));
                tip.add(Component.literal("MP " + Defs.fmt(s.mp()) + " · 쿨타임 " + Defs.fmt(s.cooldown()) + "초"));
                g.renderComponentTooltip(font, tip, mx, my);
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.SKILL_WINDOW_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
