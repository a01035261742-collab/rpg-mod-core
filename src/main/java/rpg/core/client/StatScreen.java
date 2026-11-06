package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.data.PlayerData;
import rpg.core.net.Net;

/** 스탯 탭 (K): 왼쪽 = 스탯 찍기 · 오른쪽 = 능력치 2쪽 (시안 ui_d2) */
public class StatScreen extends Screen {
    private static int page = 0;   // 오른쪽 쪽수 기억
    private int x, y;

    public StatScreen() {
        super(Component.literal("능력치"));
    }

    @Override
    protected void init() {
        x = Ui.cx(width, CharWin.W);
        y = Ui.cy(height, CharWin.H);
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.STAT);
        for (int i = 0; i < 6; i++) {
            final int stat = i;
            addRenderableWidget(Ui.button(Component.literal("+"), b -> send(stat, hasShiftDown() ? 5 : 1))
                    .bounds(x + 150, y + 56 + i * 15, 14, 13).accent(ClientData.statPoints > 0).build()).active = ClientData.statPoints > 0;
        }
        addRenderableWidget(Ui.button(Component.literal("◀"), b -> { page = 0; rebuildWidgets(); })
                .bounds(x + 236, y + 158, 22, 14).build()).active = page > 0;
        addRenderableWidget(Ui.button(Component.literal("▶"), b -> { page = 1; rebuildWidgets(); })
                .bounds(x + 262, y + 158, 22, 14).build()).active = page < 1;
    }

    private void send(int stat, int amount) {
        Net.CH.sendToServer(new Net.AllocStat(stat, amount));
    }

    @Override
    public void tick() {
        // 포인트가 생기거나 다 쓰면 + 버튼 켜기/끄기
        boolean on = ClientData.statPoints > 0;
        for (var c : children()) if (c instanceof Button b && "+".equals(b.getMessage().getString()) && b.active != on) { rebuildWidgets(); return; }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CharWin.draw(g, font, x, y);
        int lx = x + 76;
        g.drawString(font, "Lv." + ClientData.level + "  " + ClientData.jobName(), lx, y + 30, Ui.VAL, true);
        g.drawString(font, "남은 포인트", lx, y + 42, Ui.GRAY, false);
        String pt2 = String.valueOf(ClientData.statPoints);
        g.drawString(font, pt2, x + 164 - font.width(pt2), y + 42, ClientData.statPoints > 0 ? Ui.GREEN : Ui.GRAY, true);
        for (int i = 0; i < 6; i++) {
            int ry = y + 56 + i * 15;
            Ui.row(g, lx - 2, ry - 1, x + 147, ry + 12, false);
            g.drawString(font, PlayerData.STAT_NAMES[i], lx + 2, ry + 2, Ui.TXT, true);
            String v = ClientData.stats[i] + (ClientData.statBonus[i] != 0 ? "+" + ClientData.statBonus[i] : "");
            g.drawString(font, v, x + 145 - font.width(v), ry + 2, Ui.VAL, true);
        }
        g.drawString(font, "Shift+클릭 = 5씩", lx, y + 150, Ui.GRAY, false);
        Ui.vsep(g, x + 169, y + 28, y + H_IN);

        int rx = x + 175, rr = x + 284, ry = y + 30;
        g.drawString(font, page == 0 ? "능력치" : "상세 능력치", rx, ry, Ui.VAL, true);
        String pg = (page + 1) + "/2";
        g.drawString(font, pg, rr - font.width(pg), ry, Ui.GRAY, false);
        Ui.hsep(g, rx, rr, ry + 11);
        ry += 16;
        if (page == 0) {
            ry = line(g, rx, rr, ry, ClientData.job == 3 ? "마력 스공" : "스공", String.format("%,.0f~%,.0f", ClientData.atkMin, ClientData.atkMax));
            ry = line(g, rx, rr, ry, "최대 HP", String.format("%,.0f", ClientData.maxHp));
            ry = line(g, rx, rr, ry, "최대 MP", String.format("%,.0f", ClientData.maxMp));
            ry = line(g, rx, rr, ry, "방어력", String.format("%,.0f", ClientData.defPct));
            double k = rpg.core.balance.Balance.defBase + Math.max(1, ClientData.level) * rpg.core.balance.Balance.defPerMobLevel;
            ry = line(g, rx, rr, ry, "피해 감소", String.format("%.0f%%", ClientData.defPct / (ClientData.defPct + k) * 100));
            ry = line(g, rx, rr, ry, "치명타 확률", String.format("%.1f%%", ClientData.crit));
            ry = line(g, rx, rr, ry, "치명타 피해", String.format("%.0f%%", ClientData.critDmgPct));
            ry = line(g, rx, rr, ry, "보스 데미지", String.format("+%.0f%%", ClientData.bossPct));
        } else {
            ry = line(g, rx, rr, ry, "방어율 무시", String.format("%.1f%%", ClientData.ignoreDef));
            if (ClientData.overloadPct > 0) ry = line(g, rx, rr, ry, "마력 과부하", String.format("+%.1f%%", ClientData.overloadPct));
            ry = line(g, rx, rr, ry, "회복량", String.format("+%.1f%%", ClientData.healBonus));
            ry = line(g, rx, rr, ry, "드롭률", String.format("+%.1f%%", ClientData.dropBonus));
            ry = line(g, rx, rr, ry, "골드 획득", String.format("+%.1f%%", ClientData.goldBonus));
            ry = line(g, rx, rr, ry, "스태미나", String.format("15+%.0f", ClientData.staminaBonus));
        }
        super.render(g, mx, my, pt);
    }

    private static final int H_IN = CharWin.H - 18;

    private int line(GuiGraphics g, int rx, int rr, int ry, String k, String v) {
        g.drawString(font, Ui.cut(font, k, rr - rx - 4 - font.width(v)), rx, ry, Ui.TXT, false);
        g.drawString(font, v, rr - font.width(v), ry, Ui.VAL, true);
        return ry + 12;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientEvents.STAT_KEY.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
