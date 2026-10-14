package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** HP·MP·EXP 바 (핫바 바로 위) + 사냥 보상 알림 */
public final class RpgHud {
    private RpgHud() {}

    private record Note(String text, int color, long time) {}
    private static final java.util.List<Note> NOTES = new java.util.ArrayList<>();
    /** 스킬바 크기·위치 (GUI 크기 2 기준, Epic Fight 스태미나 바 왼쪽) */
    static final int SIZE = 24, GAP = 2, EF_RIGHT = 128;

    public static void addReward(long exp, long gold) {
        if (exp > 0) addNote("+" + String.format("%,d", exp) + " EXP", 0xFFB0FF80);
        if (gold > 0) addNote("+" + String.format("%,d", gold) + " G", 0xFFFFD24A);
    }

    public static void addNote(String text, int color) {
        NOTES.add(new Note(text, color, System.currentTimeMillis()));
        while (NOTES.size() > 7) NOTES.remove(0);
    }

    // ───────── 레벨업 연출 (화면 위쪽 가운데) ─────────
    private static int lvUpLevel;
    private static long lvUpTime;

    public static void levelUp(int level) {
        lvUpLevel = level;
        lvUpTime = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f));
    }

    private static void levelUpBanner(GuiGraphics g, Font font, int w) {
        if (lvUpTime == 0) return;
        long age = System.currentTimeMillis() - lvUpTime;
        if (age > 3500) return;
        float a = age < 250 ? age / 250f : age > 2800 ? 1f - (age - 2800) / 700f : 1f;
        int alpha = Math.max(8, (int) (255 * a)) << 24;
        float pop = age < 250 ? 1.4f - 0.4f * (age / 250f) : 1f;   // 처음에 크게 → 제자리
        var pose = g.pose();
        String t1 = "LEVEL UP!";
        pose.pushPose();
        pose.translate(w / 2f, 34, 0);
        pose.scale(3f * pop, 3f * pop, 1);
        g.drawString(font, t1, -font.width(t1) / 2, -4, alpha | 0xFFD24A, true);
        pose.popPose();
        String t2 = "Lv." + lvUpLevel;
        pose.pushPose();
        pose.translate(w / 2f, 58, 0);
        pose.scale(1.6f, 1.6f, 1);
        g.drawString(font, t2, -font.width(t2) / 2, -4, alpha | 0xFFFFFF, true);
        pose.popPose();
        String t3 = "스탯·스킬·패시브 포인트를 받았어요  (K · L · N)";
        g.drawString(font, t3, w / 2 - font.width(t3) / 2, 72, alpha | 0xA0E0A0, true);
    }

    /** 획득 알림: 스킬바 위, 오른쪽 정렬, 3초 */
    private static void notes(GuiGraphics g, Font font, int w, int h) {
        long now = System.currentTimeMillis();
        NOTES.removeIf(n -> now - n.time() > 3000);
        int right = w - EF_RIGHT;
        int ny = h - 2 * (SIZE + GAP) - 4 - 12;
        for (int i = NOTES.size() - 1; i >= 0; i--) {
            Note n = NOTES.get(i);
            long age = now - n.time();
            int alpha = age < 2000 ? 255 : (int) (255 * (1 - (age - 2000) / 1000.0));
            if (alpha > 8) g.drawString(font, n.text(), right - font.width(n.text()), ny, (alpha << 24) | (n.color() & 0xFFFFFF), true);
            ny -= 10;
        }
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientData.ready || mc.player.isSpectator()) return;
        skillBar(g, mc.font, w, h);
        partyHud(g, mc.font, w, h);
        downHud(g, mc.font, w, h);
        notes(g, mc.font, w, h);
        questTracker(g, mc.font, w, h);
        levelUpBanner(g, mc.font, w);
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
        // 검은 테두리(1칸) 포함 바깥 끝이 핫바(182칸) 양 끝과 딱 맞게
        bar(g, font, left + 1, by, 88, 10, ClientData.hp, ClientData.maxHp, 0xFFD02828, 0xFF4A1414, "HP");
        bar(g, font, left + 93, by, 88, 10, ClientData.mp, ClientData.maxMp, 0xFF2A68E0, 0xFF142448, "MP");

        // 레벨 (왼쪽)
        String lv = "Lv." + ClientData.level;
        g.drawString(font, lv, left - font.width(lv) - 5, by + 1, 0xFFFFD24A, true);

        if (ClientData.statPoints > 0) {
            String sp = "+" + ClientData.statPoints;
            g.drawString(font, sp, left - font.width(sp) - 5, by + 11, 0xFF80FF80, true);
        }
    }

    /** 스킬바: 화면 오른쪽 아래 3칸 × 2줄 (Q E R / F Z X) + 구르기 */
    private static void skillBar(GuiGraphics g, Font font, int w, int h) {
        int size = SIZE, gap = GAP;
        int x0 = w - EF_RIGHT - 3 * (size + gap);
        int y0 = h - 2 * (size + gap) - 4;
        for (int i = 0; i < 6; i++) {
            int x = x0 + (i % 3) * (size + gap), y = y0 + (i / 3) * (size + gap);
            g.fill(x, y, x + size, y + size, 0xB0101018);
            g.fill(x, y, x + size, y + 1, 0xFF505068);
            String id = ClientData.slots[i];
            if (id != null) {
                SkillIcons.draw(g, id, x + 4, y + 4);
                int left = ClientData.left(ClientData.cdLeft[i]);
                int len = ClientData.cdLen[i];
                if (left > 0 && len > 0) {
                    int fh = (int) Math.ceil(size * (double) left / len);
                    g.fill(x, y + size - fh, x + size, y + size, 0xA0000000);
                    String sec = String.valueOf((left + 19) / 20);
                    g.drawString(font, sec, x + size / 2 - font.width(sec) / 2, y + 8, 0xFFFF7070, true);
                }
            }
            g.drawString(font, rpg.core.client.ClientEvents.SLOT_LABELS[i], x + 1, y + size - 8, 0xFFC8C8C8, true);
        }
        // 구르기는 Epic Fight (Left Alt) — 자체 표시 사용
        // 질주 버프 남은 시간
        // 버프 남은 시간 (스킬바 왼쪽, 위에서부터)
        int by = y0;
        for (ClientData.Buff b : ClientData.BUFFS) {
            int bl = ClientData.left(b.left());
            if (bl <= 0) continue;
            String t = b.name() + " " + (bl + 19) / 20 + "s";
            g.drawString(font, t, x0 - font.width(t) - 4, by, 0xFF80E0FF, true);
            by += 10;
        }
        // 차지 게이지 (화면 가운데 아래)
        if (ClientData.charging) {
            int ticks = ClientData.chargeTicks + (int) ((System.currentTimeMillis() - ClientData.receivedAt) / 50);
            double r = Math.max(0, Math.min(1, (ticks - 6) / 24.0));
            int cx = w / 2, cy = h / 2 + 18;
            g.fill(cx - 31, cy - 1, cx + 31, cy + 5, 0xC0000000);
            g.fill(cx - 30, cy, cx - 30 + (int) (60 * r), cy + 4, r >= 1 ? 0xFFFF8030 : 0xFFFFD24A);
            if (r >= 1) g.drawString(font, "MAX", cx - font.width("MAX") / 2, cy + 7, 0xFFFF8030, true);
        }
    }

    /** 오른쪽: 진행 중 퀘스트 목표 (최대 3개) */
    private static void questTracker(GuiGraphics g, Font font, int w, int h) {
        if (Minecraft.getInstance().screen != null) return;
        var list = ClientQuests.active();
        if (list.isEmpty()) return;
        // 줄 모으기 (글자, 색, 제목 여부) → 배경 판 크기 계산 → 그리기
        java.util.List<Object[]> lines = new java.util.ArrayList<>();
        int shown = 0;
        for (var q : list) {
            if (shown >= 3) break;
            boolean done = ClientQuests.complete(q);
            if (shown > 0) lines.add(new Object[]{"", 0, false});
            lines.add(new Object[]{(done ? "✔ " : "") + q.name(), done ? 0xFF80E080 : 0xFFFFD24A, true});
            if (done) {
                lines.add(new Object[]{"→ " + rpg.core.quest.Quests.finishName(q) + (q.toPoint() ? "에 두고 오기" : "에게 가기"), 0xFFFFC040, false});
            } else if (q.goals().isEmpty()) {
                lines.add(new Object[]{rpg.core.quest.Quests.npcName(q.turnIn()) + "에게 말 걸기", 0xFFE0E0E0, false});
            } else for (int i = 0; i < q.goals().size(); i++) {
                boolean ok = ClientQuests.current(q, i) >= q.goals().get(i).count();
                lines.add(new Object[]{ClientQuests.goalText(q, i), ok ? 0xFF80E080 : 0xFFE0E0E0, false});
            }
            shown++;
        }
        if (list.size() > 3) lines.add(new Object[]{"+" + (list.size() - 3) + "개 더 (J)", 0xFF909090, false});
        int width = font.width("퀘스트");
        for (Object[] l : lines) width = Math.max(width, font.width((String) l[0]));
        int pad = 5, right = w - 6, x0 = right - width - pad * 2, y = 58;
        int height = 13 + lines.size() * 10 + pad;
        g.fill(x0, y, right, y + height, 0x90101018);
        g.fill(x0, y, right, y + 1, 0xC0FFD24A);
        g.drawString(font, "퀘스트", x0 + pad, y + 4, 0xFFFFD24A, true);
        int ty = y + 16;
        for (Object[] l : lines) {
            String s = (String) l[0];
            if (!s.isEmpty()) g.drawString(font, s, (boolean) l[2] ? x0 + pad : x0 + pad + 6, ty, (int) l[1], true);
            ty += 10;
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
