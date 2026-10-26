package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.data.PlayerData;
import rpg.core.net.Net;

/** 스탯 창 (K): 메이플식 작은 창 + "상세" 버튼으로 오른쪽에 상세 능력치 펼치기 */
public class StatScreen extends Screen {
    private static final int W = 150, H = 226, DW = 150;
    private static boolean detail = false;   // 펼침 상태 기억
    private int x, y;

    public StatScreen() {
        super(Component.literal("능력치"));
    }

    @Override
    protected void init() {
        int total = W + (detail ? DW + 4 : 0);
        x = Ui.cx(width, total);
        y = Ui.cy(height, H);
        for (int i = 0; i < 6; i++) {
            final int stat = i;
            int ry = y + 66 + i * 16;
            addRenderableWidget(Ui.button(Component.literal("+"), b -> send(stat, hasShiftDown() ? 5 : 1))
                    .bounds(x + W - 24, ry - 3, 16, 14).build());
        }
        addRenderableWidget(Ui.menu(x, y, H));
        addRenderableWidget(Ui.button(Component.literal(detail ? "상세 <" : "상세 >"), b -> {
            detail = !detail;
            rebuildWidgets();
        }).bounds(x + 50, y + H - Ui.FOOT, 46, Ui.BTN_H).build());
        addRenderableWidget(Ui.close(x, y, W, H, this::onClose));
    }

    private void send(int stat, int amount) {
        Net.CH.sendToServer(new Net.AllocStat(stat, amount));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // 기본 창
        Ui.frame(g, font, x, y, W, H, Ui.GOLD, "능력치 (K)", "", 0);
        g.drawString(font, "Lv." + ClientData.level + "  " + ClientData.jobName(), x + 8, y + 22, 0xFFFFFFFF, true);
        g.drawString(font, "HP " + (long) ClientData.maxHp + "   MP " + (long) ClientData.maxMp, x + 8, y + 34, 0xFFE0A0A0, true);
        g.drawString(font, "남은 포인트: " + ClientData.statPoints, x + 8, y + 48,
                ClientData.statPoints > 0 ? 0xFF80FF80 : 0xFFA0A0A0, true);
        for (int i = 0; i < 6; i++) {
            int ry = y + 66 + i * 16;
            g.drawString(font, PlayerData.STAT_NAMES[i], x + 10, ry, 0xFFFFFFFF, true);
            String v = ClientData.stats[i] + (ClientData.statBonus[i] != 0 ? " (+" + ClientData.statBonus[i] + ")" : "");
            g.drawString(font, v, x + W - 32 - font.width(v), ry, 0xFFFFFFFF, true);
        }
        g.drawString(font, "Shift+클릭 = 5씩", x + 8, y + H - Ui.FOOT - 13, 0xFF808080, false);
        Ui.footLine(g, x, y, W, H);

        // 상세 능력치
        if (detail) {
            int dx = x + W + 4;
            Ui.frame(g, font, dx, y, DW, H, Ui.GOLD, "상세 능력치", "", 0);
            int ry = y + 24;
            ry = line(g, dx, ry, ClientData.job == 3 ? "마력 스공" : "스공", String.format("%,.0f ~ %,.0f", ClientData.atkMin, ClientData.atkMax));
            ry = line(g, dx, ry, "최대 HP", String.format("%,.0f", ClientData.maxHp));
            ry = line(g, dx, ry, "최대 MP", String.format("%,.0f", ClientData.maxMp));
            ry = line(g, dx, ry, "방어력", String.format("%,.0f", ClientData.defPct));
            double k = rpg.core.balance.Balance.defBase + Math.max(1, ClientData.level) * rpg.core.balance.Balance.defPerMobLevel;
            ry = line(g, dx, ry, "피해 감소 (같은 Lv 몹)", String.format("%.0f%%", ClientData.defPct / (ClientData.defPct + k) * 100));
            ry = line(g, dx, ry, "치명타 확률", String.format("%.1f%%", ClientData.crit));
            ry = line(g, dx, ry, "치명타 피해", String.format("%.0f%%", ClientData.critDmgPct));
            ry = line(g, dx, ry, "보스 데미지", String.format("+%.0f%%", ClientData.bossPct));
            ry = line(g, dx, ry, "방어율 무시", String.format("%.1f%%", ClientData.ignoreDef));
            if (ClientData.overloadPct > 0) ry = line(g, dx, ry, "마력 과부하", String.format("+%.1f%%", ClientData.overloadPct));
            ry = line(g, dx, ry, "회복량 증가", String.format("+%.1f%%", ClientData.healBonus));
            ry = line(g, dx, ry, "드롭률 증가", String.format("+%.1f%%", ClientData.dropBonus));
            ry = line(g, dx, ry, "골드 획득 증가", String.format("+%.1f%%", ClientData.goldBonus));
            ry = line(g, dx, ry, "최대 스태미나", String.format("15 + %.0f", ClientData.staminaBonus));
            ry += 6;
            ry = line(g, dx, ry, "골드", String.format("%,d", ClientData.gold));
        }
        super.render(g, mx, my, pt);
    }

    private int line(GuiGraphics g, int dx, int ry, String k, String v) {
        g.drawString(font, Ui.cut(font, k, DW - 20 - font.width(v)), dx + 8, ry, 0xFFC8C8C8, true);
        g.drawString(font, v, dx + DW - 8 - font.width(v), ry, 0xFFFFFFFF, true);
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
