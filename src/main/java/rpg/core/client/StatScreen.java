package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.data.PlayerData;
import rpg.core.net.Net;

/** 스탯 창 (K): 메이플식 작은 창 + "상세" 버튼으로 오른쪽에 상세 능력치 펼치기 */
public class StatScreen extends Screen {
    private static final int W = 150, H = 198, DW = 150;
    private static boolean detail = false;   // 펼침 상태 기억
    private int x, y;

    public StatScreen() {
        super(Component.literal("능력치"));
    }

    @Override
    protected void init() {
        int total = W + (detail ? DW + 4 : 0);
        x = width / 2 - total / 2;
        y = height / 2 - H / 2;
        for (int i = 0; i < 6; i++) {
            final int stat = i;
            int ry = y + 66 + i * 16;
            addRenderableWidget(Button.builder(Component.literal("+"), b -> send(stat, hasShiftDown() ? 5 : 1))
                    .bounds(x + W - 24, ry - 3, 16, 14).build());
        }
        addRenderableWidget(Button.builder(Component.literal(detail ? "상세 <" : "상세 >"), b -> {
            detail = !detail;
            rebuildWidgets();
        }).bounds(x + W - 52, y + H - 20, 46, 14).build());
    }

    private void send(int stat, int amount) {
        Net.CH.sendToServer(new Net.AllocStat(stat, amount));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        // 기본 창
        panel(g, x, y, W, H);
        g.drawString(font, "능력치", x + 8, y + 6, 0xFFFFD24A, true);
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
        g.drawString(font, "Shift+클릭 = 5씩", x + 8, y + H - 17, 0xFF808080, false);

        // 상세 능력치
        if (detail) {
            int dx = x + W + 4;
            panel(g, dx, y, DW, H);
            g.drawString(font, "상세 능력치", dx + 8, y + 6, 0xFFFFD24A, true);
            int ry = y + 24;
            ry = line(g, dx, ry, "스공", String.format("%,.0f ~ %,.0f", ClientData.atkMin, ClientData.atkMax));
            ry = line(g, dx, ry, "최대 HP", String.format("%,.0f", ClientData.maxHp));
            ry = line(g, dx, ry, "최대 MP", String.format("%,.0f", ClientData.maxMp));
            ry = line(g, dx, ry, "방어력", String.format("%,.0f", ClientData.defPct));
            ry = line(g, dx, ry, "치명타 확률", String.format("%.1f%%", ClientData.crit));
            ry = line(g, dx, ry, "치명타 피해", String.format("%.0f%%", ClientData.critDmgPct));
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
        g.drawString(font, k, dx + 8, ry, 0xFFC8C8C8, true);
        g.drawString(font, v, dx + DW - 8 - font.width(v), ry, 0xFFFFFFFF, true);
        return ry + 12;
    }

    private static void panel(GuiGraphics g, int px, int py, int pw, int ph) {
        g.fill(px, py, px + pw, py + ph, 0xF0141420);
        g.fill(px, py, px + pw, py + 1, 0xFFFFD24A);
        g.fill(px, py + ph - 1, px + pw, py + ph, 0xFF505060);
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
