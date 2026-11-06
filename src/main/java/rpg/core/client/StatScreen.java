package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import rpg.core.data.PlayerData;
import rpg.core.net.Net;

/** 스탯 탭 (K, 시안 uiA2_02): 왼쪽 = 스탯 찍기 (한 줄 설명) · 오른쪽 = 능력치 전부 */
public class StatScreen extends ScaledScreen {
    private int x, y;
    private static final int W = CharWin.W, LX = 88, ROW = 24, DW = 180;
    private static boolean detail = false;   // 상세 창 펼침 기억

    @Override public int baseW() { return W + 4 + DW; }

    public StatScreen() {
        super(Component.literal("능력치"));
    }

    @Override
    protected void init() {
        x = Math.min(Ui.cx(width, W), Math.max(Ui.MARGIN, width - Ui.MARGIN - (detail ? W + 4 + DW : W)));
        y = Ui.cy(height, CharWin.H);
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.STAT);
        boolean on = ClientData.statPoints > 0;
        addRenderableWidget(Ui.button(Component.literal(detail ? "상세 ◀" : "상세 ▶"), b -> { detail = !detail; rebuildWidgets(); })
                .bounds(x + W - 84, y + 260, 64, 18).accent(detail).build());
        for (int i = 0; i < 6; i++) {
            final int stat = i;
            addRenderableWidget(Ui.button(Component.literal("+"), b -> send(stat, hasShiftDown() ? 5 : 1))
                    .bounds(x + LX + 134, y + 64 + i * ROW, 16, 16).accent(on).build()).active = on;
        }
    }

    private void send(int stat, int amount) {
        Net.CH.sendToServer(new Net.AllocStat(stat, amount));
    }

    @Override
    public void tick() {
        boolean on = ClientData.statPoints > 0;
        for (var c : children()) if (c instanceof Button b && "+".equals(b.getMessage().getString()) && b.active != on) { rebuildWidgets(); return; }
    }

    private static int mainIdx() { return switch (ClientData.job) { case 2 -> 2; case 3 -> 3; case 4 -> 4; default -> 1; }; }
    private static int subIdx() { return switch (ClientData.job) { case 2 -> 1; case 3 -> 4; case 4 -> 2; default -> 2; }; }

    private static String desc(int i) {
        if (i == 0) return "최대 HP ↑";
        if (i == 5) return "스태미나 ↑ (구르기·달리기)";
        if (i == mainIdx()) return "주 스탯 · 공격력 크게 ↑";
        if (i == subIdx()) return "부 스탯 · 공격력 ↑";
        return "지금 직업엔 효과 적음";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CharWin.draw(g, font, x, y);
        int lx = x + LX;
        g.drawString(font, "Lv." + ClientData.level + "  " + ClientData.jobName(), lx, y + 34, Ui.VAL, true);
        g.drawString(font, "남은 포인트", lx, y + 48, Ui.GRAY, false);
        String p = String.valueOf(ClientData.statPoints);
        g.drawString(font, p, lx + 150 - font.width(p), y + 48, ClientData.statPoints > 0 ? Ui.GREEN : Ui.GRAY, true);
        for (int i = 0; i < 6; i++) {
            int ry = y + 62 + i * ROW;
            boolean key = i == mainIdx();
            Ui.row(g, lx - 2, ry, lx + 130, ry + ROW - 3, key);
            g.drawString(font, PlayerData.STAT_NAMES[i], lx + 3, ry + 2, Ui.TXT, true);
            String v = ClientData.stats[i] + (ClientData.statBonus[i] != 0 ? "+" + ClientData.statBonus[i] : "");
            g.drawString(font, v, lx + 126 - font.width(v), ry + 2, Ui.VAL, true);
            g.drawString(font, desc(i), lx + 3, ry + 11, Ui.GRAY, false);
        }
        g.drawString(font, "Shift+클릭 = 5씩", lx, y + 214, Ui.GRAY, false);

        int rx = x + 270, rr = x + W - 20, ry = y + 34;
        Ui.vsep(g, rx - 8, y + 30, y + H_IN);
        g.drawString(font, "능력치", rx, ry, Ui.VAL, true);
        Ui.hsep(g, rx, rr, ry + 12);
        ry += 18;
        ry = line(g, rx, rr, ry, ClientData.job == 3 ? "마력 스공" : "스공", String.format("%,.0f ~ %,.0f", ClientData.atkMin, ClientData.atkMax));
        ry = line(g, rx, rr, ry, "최대 HP", String.format("%,.0f", ClientData.maxHp));
        ry = line(g, rx, rr, ry, "최대 MP", String.format("%,.0f", ClientData.maxMp));
        ry = line(g, rx, rr, ry, "방어력", String.format("%,.0f", ClientData.defPct));
        ry = line(g, rx, rr, ry, "치명타 확률", String.format("%.1f%%", ClientData.crit));
        ry = line(g, rx, rr, ry, "치명타 피해", String.format("%.0f%%", ClientData.critDmgPct));
        ry = line(g, rx, rr, ry, "보스 데미지", String.format("+%.0f%%", ClientData.bossPct));
        // 상세 창 (오른쪽에 붙어서 펼침 · 캐릭터 창은 안 움직임)
        if (detail) {
            int dx = x + W + 4, lx2 = dx + 12, lr = dx + DW - 14, ly = y + 30;
            Ui.window(g, dx, y, DW, CharWin.H);
            Ui.plaque(g, font, dx + DW / 2, y + 1, "상세 능력치");
            ly = line(g, lx2, lr, ly, ClientData.job == 3 ? "마력 스공" : "스공", String.format("%,.0f~%,.0f", ClientData.atkMin, ClientData.atkMax));
            ly = line(g, lx2, lr, ly, "최대 HP", String.format("%,.0f", ClientData.maxHp));
            ly = line(g, lx2, lr, ly, "최대 MP", String.format("%,.0f", ClientData.maxMp));
            ly = line(g, lx2, lr, ly, "방어력", String.format("%,.0f", ClientData.defPct));
            double k = rpg.core.balance.Balance.defBase + Math.max(1, ClientData.level) * rpg.core.balance.Balance.defPerMobLevel;
            ly = line(g, lx2, lr, ly, "피해 감소", String.format("%.0f%%", ClientData.defPct / (ClientData.defPct + k) * 100));
            ly = line(g, lx2, lr, ly, "치명타 확률", String.format("%.1f%%", ClientData.crit));
            ly = line(g, lx2, lr, ly, "치명타 피해", String.format("%.0f%%", ClientData.critDmgPct));
            ly = line(g, lx2, lr, ly, "보스 데미지", String.format("+%.0f%%", ClientData.bossPct));
            ly = line(g, lx2, lr, ly, "방어율 무시", String.format("%.1f%%", ClientData.ignoreDef));
            if (ClientData.overloadPct > 0) ly = line(g, lx2, lr, ly, "마력 과부하", String.format("+%.1f%%", ClientData.overloadPct));
            ly = line(g, lx2, lr, ly, "회복량 증가", String.format("+%.1f%%", ClientData.healBonus));
            ly = line(g, lx2, lr, ly, "드롭률 증가", String.format("+%.1f%%", ClientData.dropBonus));
            ly = line(g, lx2, lr, ly, "골드 획득 증가", String.format("+%.1f%%", ClientData.goldBonus));
            ly = line(g, lx2, lr, ly, "최대 스태미나", String.format("15 + %.0f", ClientData.staminaBonus));
        }
        super.render(g, mx, my, pt);
    }

    private static final int H_IN = CharWin.H - 18;

    private int line(GuiGraphics g, int rx, int rr, int ry, String k, String v) {
        g.drawString(font, Ui.cut(font, k, rr - rx - 6 - font.width(v)), rx, ry, Ui.TXT, false);
        g.drawString(font, v, rr - font.width(v), ry, Ui.VAL, true);
        return ry + 14;
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
