package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** HP·MP·EXP 바 (핫바 바로 위) + 사냥 보상 알림 */
public final class RpgHud {
    private RpgHud() {}

    private record Note(String text, long time) {}
    private static final java.util.List<Note> NOTES = new java.util.ArrayList<>();

    public static void addReward(long exp, long gold) {
        String t = "+" + String.format("%,d", exp) + " EXP" + (gold > 0 ? "   +" + String.format("%,d", gold) + " G" : "");
        NOTES.add(new Note(t, System.currentTimeMillis()));
        if (NOTES.size() > 5) NOTES.remove(0);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientData.ready || mc.player.isSpectator()) return;
        skillBar(g, mc.font, w, h);
        partyHud(g, mc.font, w, h);
        downHud(g, mc.font, w, h);
        if (mc.player.isCreative()) return;
        Font font = mc.font;
        int cx = w / 2;
        int left = cx - 91;

        // EXP 바 (핫바 바로 위, 얇게)
        int ey = h - 22 - 6;
        double ratio = ClientData.need <= 0 || ClientData.need == Long.MAX_VALUE ? 1.0 : (double) ClientData.exp / ClientData.need;
        ratio = Math.max(0, Math.min(1, ratio));
        g.fill(left, ey, left + 182, ey + 4, 0xFF1A1A1A);
        g.fill(left, ey, left + (int) (182 * ratio), ey + 4, 0xFFE8C030);
        String pct = String.format("%.2f%%", ratio * 100);
        g.drawString(font, pct, left + 182 + 4, ey - 2, 0xFFE8C030, true);

        // HP / MP 바 (그 위)
        int by = ey - 12;
        bar(g, font, left, by, 90, 10, ClientData.hp, ClientData.maxHp, 0xFFD02828, 0xFF4A1414, "HP");
        bar(g, font, left + 92, by, 90, 10, ClientData.mp, ClientData.maxMp, 0xFF2A68E0, 0xFF142448, "MP");

        // 레벨 (왼쪽)
        String lv = "Lv." + ClientData.level;
        g.drawString(font, lv, left - font.width(lv) - 5, by + 1, 0xFFFFD24A, true);
        // 보상 알림 (오른쪽 아래, 3초)
        long now = System.currentTimeMillis();
        NOTES.removeIf(n -> now - n.time() > 3000);
        int ny = h - 50;
        for (int i = NOTES.size() - 1; i >= 0; i--) {
            Note n = NOTES.get(i);
            long age = now - n.time();
            int alpha = age < 2000 ? 255 : (int) (255 * (1 - (age - 2000) / 1000.0));
            if (alpha > 8) g.drawString(font, n.text(), w - font.width(n.text()) - 6, ny, (alpha << 24) | 0xB0FF80, true);
            ny -= 10;
        }

        if (ClientData.statPoints > 0) {
            String sp = "+" + ClientData.statPoints;
            g.drawString(font, sp, left - font.width(sp) - 5, by + 11, 0xFF80FF80, true);
        }
    }

    /** 스킬바: 화면 오른쪽 아래 3칸 × 2줄 (Q E R / F Z X) + 구르기 */
    private static void skillBar(GuiGraphics g, Font font, int w, int h) {
        int size = 20, gap = 2;
        int x0 = w - 3 * (size + gap) - 6;
        int y0 = h - 2 * (size + gap) - 14;
        for (int i = 0; i < 6; i++) {
            int x = x0 + (i % 3) * (size + gap), y = y0 + (i / 3) * (size + gap);
            g.fill(x, y, x + size, y + size, 0xB0101018);
            g.fill(x, y, x + size, y + 1, 0xFF505068);
            String id = ClientData.slots[i];
            if (id != null) {
                SkillIcons.draw(g, id, x + 2, y + 2);
                int left = ClientData.left(ClientData.cdLeft[i]);
                int len = ClientData.cdLen[i];
                if (left > 0 && len > 0) {
                    int fh = (int) Math.ceil(size * (double) left / len);
                    g.fill(x, y + size - fh, x + size, y + size, 0xA0000000);
                    String sec = String.valueOf((left + 19) / 20);
                    g.drawString(font, sec, x + size / 2 - font.width(sec) / 2, y + 6, 0xFFFF7070, true);
                }
            }
            g.drawString(font, rpg.core.client.ClientEvents.SLOT_LABELS[i], x + 1, y + size - 8, 0xFFC8C8C8, true);
        }
        // 구르기 (왼쪽 작은 칸)
        int dx = x0 - 18, dy = y0 + size + gap + 4;
        g.fill(dx, dy, dx + 14, dy + 14, 0xB0101018);
        int left = ClientData.left(ClientData.dodgeLeft);
        if (left > 0 && ClientData.dodgeLen > 0) {
            int fh = (int) Math.ceil(14.0 * left / ClientData.dodgeLen);
            g.fill(dx, dy + 14 - fh, dx + 14, dy + 14, 0xA0FF6060);
        } else {
            g.fill(dx + 2, dy + 2, dx + 12, dy + 12, 0xFF60C0FF);
        }
        g.drawString(font, "Alt", dx - 1, dy - 9, 0xFFC8C8C8, true);
        // 질주 버프 남은 시간
        int sl = ClientData.left(ClientData.sprintLeft);
        if (sl > 0) {
            String t = "질주 " + (sl + 19) / 20 + "s";
            g.drawString(font, t, x0, y0 - 10, 0xFF80E0FF, true);
        }
    }

    /** 왼쪽: 파티원 체력 (나 빼고) */
    private static void partyHud(GuiGraphics g, Font font, int w, int h) {
        String me = Minecraft.getInstance().getUser().getName();
        int y = h / 2 - 40;
        for (rpg.core.net.Net.Member m : ClientParty.MEMBERS) {
            if (m.name().equals(me)) continue;
            String name = (m.leader() ? "★" : "") + m.name() + " Lv." + m.level();
            g.drawString(font, name, 6, y, m.down() ? 0xFFFF6060 : m.near() ? 0xFFFFFFFF : 0xFF909090, true);
            double r = m.maxHp() <= 0 ? 0 : Math.max(0, Math.min(1, m.hp() / m.maxHp()));
            g.fill(6, y + 10, 86, y + 14, 0xFF401010);
            g.fill(6, y + 10, 6 + (int) (80 * r), y + 14, m.down() ? 0xFF808080 : 0xFFD03030);
            if (m.down()) g.drawString(font, "다운!", 90, y + 7, 0xFFFF6060, true);
            y += 20;
        }
    }

    /** 내가 다운됐을 때 / 부활 진행 */
    private static void downHud(GuiGraphics g, Font font, int w, int h) {
        if (ClientData.downLeft < 0) return;
        int left = ClientData.left(ClientData.downLeft);
        g.fill(0, 0, w, h, 0x40FF0000);
        String t = "쓰러졌어요! " + (left + 19) / 20 + "초 안에 동료가 우클릭하면 부활";
        g.drawCenteredString(font, t, w / 2, h / 2 - 30, 0xFFFF8080);
        if (ClientData.reviveProgress >= 0) {
            int bw = 120, bx = w / 2 - bw / 2, by = h / 2 - 16;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF000000);
            g.fill(bx, by, bx + bw * ClientData.reviveProgress / 100, by + 6, 0xFF60FF60);
        }
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, int bw, int bh, double v, double max, int fg, int bg, String label) {
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000);
        g.fill(x, y, x + bw, y + bh, bg);
        double r = max <= 0 ? 0 : Math.max(0, Math.min(1, v / max));
        g.fill(x, y, x + (int) (bw * r), y + bh, fg);
        String s = label + " " + (long) Math.ceil(v) + "/" + (long) Math.ceil(max);
        g.drawString(font, s, x + bw / 2 - font.width(s) / 2, y + 1, 0xFFFFFFFF, true);
    }
}
