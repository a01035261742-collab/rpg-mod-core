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

    public static void addReward(long exp, long gold) {
        if (exp > 0) addNote("+" + String.format("%,d", exp) + " EXP", 0xFFB0FF80);
        if (gold > 0) addNote("+" + String.format("%,d", gold) + " G", 0xFFFFD86A);
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
        int right = boxX1;
        int ny = boxY0 - 12 - (ClientData.BUFFS.isEmpty() ? 0 : BOX + 14);
        for (int i = NOTES.size() - 1; i >= 0; i--) {
            Note n = NOTES.get(i);
            long age = now - n.time();
            int alpha = age < 2000 ? 255 : (int) (255 * (1 - (age - 2000) / 1000.0));
            if (alpha > 8) g.drawString(font, n.text(), right - font.width(n.text()), ny, (alpha << 24) | (n.color() & 0xFFFFFF), true);
            ny -= 10;
        }
    }

    // ───────── 아래 가운데 상자 (시안 ui_sym_A3): HP·MP + 물약2 | 스킬6 | 물약2 + EXP ─────────
    static final int SLOT = 22, SG = 2, DIV = 8;
    static int boxX0, boxX1, boxY0, boxY1;

    /** 칸 줄 폭 (물약 2+2, 스킬 6, 칸막이 2) */
    static int rowW() { return 10 * SLOT + 7 * SG + 2 * DIV; }

    static void layout(int w, int h) {
        int rw = rowW();
        boxX0 = w / 2 - rw / 2 - 6; boxX1 = boxX0 + rw + 12;
        boxY1 = h - 3; boxY0 = boxY1 - 56;
    }

    /** 스킬 6칸 시작 {x0, y0} */
    static int[] skillPos(Font font, int w, int h) {
        layout(w, h);
        return new int[]{boxX0 + 6 + 2 * (SLOT + SG) + DIV - SG, boxY1 - 32};
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientData.ready || mc.player.isSpectator() || !ClientRpg.inRpg()) return;   // 야생: RPG HUD 숨김
        Font font = mc.font;
        layout(w, h);
        partyHud(g, font, w, h);
        downHud(g, font, w, h);
        topY = goldBox(g, font, w, 4);
        questTracker(g, font, w, h);
        levelUpBanner(g, font, w);
        if (mc.player.isCreative()) { skillRowOnly(g, font, w, h); return; }

        Ui.hudBox(g, boxX0, boxY0, boxX1, boxY1);
        int bx = boxX0 + 6, bw = boxX1 - boxX0 - 12;
        // HP (보호막은 옅은 파랑으로 이어 붙임) · MP
        int hy = boxY0 + 5;
        double max = Math.max(1, ClientData.maxHp);
        float hr = (float) Math.max(0, Math.min(1, ClientData.hp / max));
        Ui.bar(g, bx, hy, bw, 9, hr, 0xFFC82832);
        if (ClientData.shield > 0) {
            int s0 = bx + Math.round(bw * hr), s1 = Math.min(bx + bw, s0 + (int) Math.round(bw * ClientData.shield / max));
            if (s1 > s0) g.fill(s0, hy, s1, hy + 9, 0xFF96AAC8);
        }
        g.fill(bx, hy, bx + Math.round(bw * hr), hy + 1, 0x60FFFFFF);
        String hs = (long) Math.ceil(ClientData.hp) + (ClientData.shield > 0 ? "+" + (long) Math.ceil(ClientData.shield) : "") + " / " + (long) Math.ceil(ClientData.maxHp);
        g.drawString(font, hs, bx + bw / 2 - font.width(hs) / 2, hy + 1, 0xFFFFFFFF, true);
        float mr = (float) (ClientData.maxMp <= 0 ? 0 : Math.max(0, Math.min(1, ClientData.mp / ClientData.maxMp)));
        Ui.bar(g, bx, hy + 11, bw, 3, mr, 0xFF3270DC);
        Ui.hsep(g, bx, bx + bw, hy + 17);
        // 칸 줄: 물약 1 2 | Q E R F Z X | 물약 3 4
        int ry = boxY1 - 32;
        int x = potions(g, font, bx, ry, 0, 2);
        x += DIV - SG;
        g.fill(x - DIV / 2 - 1, ry + 3, x - DIV / 2, ry + SLOT - 3, Ui.G3);
        x = skills(g, font, x, ry);
        x += DIV - SG;
        g.fill(x - DIV / 2 - 1, ry + 3, x - DIV / 2, ry + SLOT - 3, Ui.G3);
        potions(g, font, x, ry, 2, 2);
        // EXP
        double ratio = ClientData.need <= 0 || ClientData.need == Long.MAX_VALUE ? 1.0 : (double) ClientData.exp / ClientData.need;
        ratio = Math.max(0, Math.min(1, ratio));
        Ui.bar(g, bx, boxY1 - 7, bw, 2, (float) ratio, 0xFFF0C83C);
        // 레벨 · EXP % · 남은 스탯 (상자 왼쪽 바깥)
        String lv = "Lv." + ClientData.level;
        g.drawString(font, lv, boxX0 - 4 - font.width(lv), boxY0 + 5, Ui.VAL, true);
        String pc = String.format("%.1f%%", ratio * 100);
        g.drawString(font, pc, boxX0 - 4 - font.width(pc), boxY1 - 10, 0xFFF0C83C, true);
        if (ClientData.statPoints > 0) {
            String sp = "+" + ClientData.statPoints;
            g.drawString(font, sp, boxX0 - 4 - font.width(sp), boxY0 + 16, Ui.GREEN, true);
        }
        buffBar(g, font, w, -1, -1);
        notes(g, font, w, h);
        charge(g, font, w, h);
    }

    /** 크리에이티브: 상자 없이 스킬 줄만 (바닐라 핫바 위) */
    private static void skillRowOnly(GuiGraphics g, Font font, int w, int h) {
        skills(g, font, w / 2 - (6 * SLOT + 5 * SG) / 2, h - 22 - SLOT - 4);
        charge(g, font, w, h);
    }

    /** 물약 칸 n개 (1~4 키) → 다음 x */
    private static int potions(GuiGraphics g, Font font, int x, int y, int k0, int n) {
        for (int k = k0; k < k0 + n; k++) {
            Ui.slot(g, x, y, SLOT);
            var it = ClientPotions.item(k);
            g.pose().pushPose();
            if (it != null) {
                int c = ClientPotions.count(k);
                g.renderItem(new net.minecraft.world.item.ItemStack(it), x + 3, y + 3);
                g.pose().translate(0, 0, 200);
                if (c == 0) g.fill(x + 3, y + 3, x + 19, y + 19, 0xA0000000);
                float cd = ClientPotions.cooldown(k);
                if (cd > 0) g.fill(x + 2, y + 2, x + SLOT - 2, y + 2 + Math.round((SLOT - 4) * cd), 0xB0000000);
                String cs = String.valueOf(c);
                g.drawString(font, cs, x + SLOT - 2 - font.width(cs), y + SLOT - 9, c == 0 ? Ui.RED : 0xFFFFFFFF, true);
            } else g.pose().translate(0, 0, 200);
            g.drawString(font, String.valueOf(k + 1), x + 2, y + 2, Ui.VAL, true);
            g.pose().popPose();
            x += SLOT + SG;
        }
        return x;
    }

    /** 스킬 6칸 (Q E R F Z X) → 다음 x */
    private static int skills(GuiGraphics g, Font font, int x, int y) {
        for (int i = 0; i < 6; i++) {
            Ui.slot(g, x, y, SLOT);
            String id = ClientData.slots[i];
            if (id != null) {
                SkillIcons.draw(g, id, x + 3, y + 3);
                int left = ClientData.left(ClientData.cdLeft[i]);
                int len = ClientData.cdLen[i];
                if (left > 0 && len > 0) {   // 쿨타임: 시계 방향 쓸기
                    Ui.sweep(g, x + 3, y + 3, 16, 16, (double) left / len, 0xB0000000);
                    String sec = String.valueOf((left + 19) / 20);
                    g.drawString(font, sec, x + SLOT / 2 - font.width(sec) / 2, y + 7, 0xFFFFFFFF, true);
                }
            }
            g.drawString(font, ClientEvents.SLOT_LABELS[i], x + 2, y + SLOT - 9, Ui.VAL, true);
            x += SLOT + SG;
        }
        return x;
    }

    /** 차지 게이지 (화면 가운데 아래) */
    private static void charge(GuiGraphics g, Font font, int w, int h) {
        if (!ClientData.charging) return;
        int ticks = ClientData.chargeTicks + (int) ((System.currentTimeMillis() - ClientData.receivedAt) / 50);
        double r = Math.max(0, Math.min(1, (ticks - 6) / 24.0));
        int cx = w / 2, cy = h / 2 + 18;
        g.fill(cx - 31, cy - 1, cx + 31, cy + 5, 0xC0000000);
        g.fill(cx - 30, cy, cx - 30 + (int) (60 * r), cy + 4, r >= 1 ? 0xFFFF8030 : 0xFFFFD86A);
        if (r >= 1) g.drawString(font, "MAX", cx - font.width("MAX") / 2, cy + 7, 0xFFFF8030, true);
    }

    // ───────── 오른쪽 위: 버프 칸 + 골드 ─────────
    private static int topY = 58;
    static final int BOX = 18, BGAP = 4, PER_ROW = 8;
    private static final int[] KIND_COL = {0xFF5AC8FF, 0xFF6EDC6E, 0xFFFFC83C, 0xFF8C8C8C};   // 스킬 · 파티 · 트리 · 재사용 대기

    /** 버프 칸 (아래 상자 위 가운데): 좋은 것 = 가운데 왼쪽으로, 재사용 대기·나쁜 것 = 가운데 오른쪽으로.
     *  남은 시간은 칸 아래쪽 안 (어두운 띠). mx,my 가 칸 위면 툴팁. 맨 위 y 를 돌려줌 */
    /** 버프 이름 → 처음 본 남은 시간 (쓸기 비율용, 다시 걸리면 늘어남) */
    private static final java.util.Map<String, Integer> BUFF_MAX = new java.util.HashMap<>();
    /** 마크 효과 → 우리 아이콘 */
    private static String effIcon(net.minecraft.world.effect.MobEffect e) {
        if (e == net.minecraft.world.effect.MobEffects.POISON) return "독";
        if (e == net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) return "둔화";
        return null;
    }

    public static int buffBar(GuiGraphics g, Font font, int w, int mx, int my) {
        java.util.List<ClientData.Buff> good = new java.util.ArrayList<>(), bad = new java.util.ArrayList<>();
        for (ClientData.Buff b : ClientData.BUFFS)
            if (b.left() >= 900_000_000 || ClientData.left(b.left()) > 0) (b.kind() == 3 ? bad : good).add(b);
        java.util.List<net.minecraft.world.effect.MobEffectInstance> harm = new java.util.ArrayList<>();
        var pl = Minecraft.getInstance().player;
        if (pl != null) for (var e : pl.getActiveEffects())
            if (e.getEffect().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL && e.showIcon()) harm.add(e);
        if (good.isEmpty() && bad.isEmpty() && harm.isEmpty()) return boxY0;
        int base = boxY0 - 4 - BOX, top = base;
        ClientData.Buff hover = null;
        int hx = 0, hy = 0;
        for (int side = 0; side < 2; side++) {
            var list = side == 0 ? good : bad;
            for (int i = 0; i < list.size(); i++) {
                ClientData.Buff b = list.get(i);
                int row = i / PER_ROW, col = i % PER_ROW;
                int x = side == 0 ? w / 2 - 5 - (col + 1) * (BOX + 2) : w / 2 + 5 + col * (BOX + 2);
                int y = base - row * (BOX + 3);
                top = Math.min(top, y);
                boolean inf = b.left() >= 900_000_000;
                int left = inf ? Integer.MAX_VALUE : ClientData.left(b.left());
                int c = KIND_COL[Math.max(0, Math.min(3, b.kind()))];
                boolean blink = !inf && b.kind() != 3 && left < 60 && (System.currentTimeMillis() / 250) % 2 == 0;
                Ui.slot(g, x, y, BOX);
                if (!blink) {
                    int e = side == 1 ? 0xFFDC3232 : c;
                    g.fill(x, y, x + BOX, y + 1, e); g.fill(x, y + BOX - 1, x + BOX, y + BOX, e);
                    g.fill(x, y, x + 1, y + BOX, e); g.fill(x + BOX - 1, y, x + BOX, y + BOX, e);
                }
                if (!SkillIcons.drawBuff(g, b.name(), x + 1, y + 1)) {
                    String nm = b.name().replace(" ", "");
                    String two = nm.length() > 2 ? nm.substring(0, 2) : nm;
                    g.pose().pushPose();
                    g.pose().translate(x + BOX / 2f, y + 3, 0);
                    g.pose().scale(0.75f, 0.75f, 1);
                    g.drawCenteredString(font, two, 0, 0, c);
                    g.pose().popPose();
                }
                if (!inf) {   // 남은 시간 쓸기 (처음 본 길이 기준)
                    int mx0 = Math.max(BUFF_MAX.getOrDefault(b.name(), 0), left);
                    BUFF_MAX.put(b.name(), mx0);
                    Ui.sweep(g, x + 1, y + 1, 16, 16, 1 - (double) left / mx0, 0x90000000);
                }
                String ex = b.extra();
                String under = ex.startsWith("×") ? ex : !ex.isEmpty() ? ex : inf ? "" : left >= 1200 ? (left / 1200) + "m" : String.valueOf((left + 19) / 20);
                if (!under.isEmpty()) {
                    g.fill(x + 1, y + BOX - 8, x + BOX - 1, y + BOX - 1, 0xB0000000);
                    g.pose().pushPose();
                    g.pose().translate(x + BOX / 2f, y + BOX - 7, 0);
                    g.pose().scale(0.7f, 0.7f, 1);
                    boolean soon = !inf && left < 100 && !ex.startsWith("×");
                    g.drawCenteredString(font, under, 0, 0, side == 1 || soon ? 0xFFFF6060 : 0xFFFFFFFF);
                    g.pose().popPose();
                }
                if (mx >= x && mx < x + BOX && my >= y && my < y + BOX) { hover = b; hx = mx; hy = my; }
            }
        }
        // 마크 나쁜 효과 (둔화·독 등) = 디버프: 오른쪽, 재사용 대기 뒤에
        for (int i = 0; i < harm.size(); i++) {
            var e = harm.get(i);
            int n = bad.size() + i, row = n / PER_ROW, col = n % PER_ROW;
            int x = w / 2 + 5 + col * (BOX + 2), y = base - row * (BOX + 3);
            top = Math.min(top, y);
            Ui.slot(g, x, y, BOX);
            int e2 = 0xFFDC3232;
            g.fill(x, y, x + BOX, y + 1, e2); g.fill(x, y + BOX - 1, x + BOX, y + BOX, e2);
            g.fill(x, y, x + 1, y + BOX, e2); g.fill(x + BOX - 1, y, x + BOX, y + BOX, e2);
            String ei = effIcon(e.getEffect());
            if (ei == null || !SkillIcons.drawBuff(g, ei, x + 1, y + 1)) {
                var spr = Minecraft.getInstance().getMobEffectTextures().get(e.getEffect());
                g.blit(x + 1, y + 1, 0, BOX - 2, BOX - 2, spr);
            }
            int d = e.getDuration();
            String t = e.isInfiniteDuration() ? "" : d >= 1200 ? (d / 1200) + "m" : String.valueOf((d + 19) / 20);
            if (!t.isEmpty()) {
                g.fill(x + 1, y + BOX - 8, x + BOX - 1, y + BOX - 1, 0xB0000000);
                g.pose().pushPose(); g.pose().translate(x + BOX / 2f, y + BOX - 7, 0); g.pose().scale(0.7f, 0.7f, 1);
                g.drawCenteredString(font, t, 0, 0, 0xFFFF6060);
                g.pose().popPose();
            }
        }
        if (hover != null) {
            java.util.List<net.minecraft.network.chat.Component> tip = new java.util.ArrayList<>();
            String[] kinds = {"스킬", "파티원 버프", "패시브 트리", "재사용 대기"};
            final int hc = KIND_COL[Math.max(0, Math.min(3, hover.kind()))] & 0xFFFFFF;
            tip.add(net.minecraft.network.chat.Component.literal(hover.name()).withStyle(st -> st.withColor(hc)));
            tip.add(net.minecraft.network.chat.Component.literal("§7" + kinds[Math.max(0, Math.min(3, hover.kind()))] + (hover.extra().isEmpty() ? "" : "  §f" + hover.extra())));
            if (hover.left() < 900_000_000) tip.add(net.minecraft.network.chat.Component.literal("§7남은 시간 " + (ClientData.left(hover.left()) + 19) / 20 + "초"));
            g.renderComponentTooltip(font, tip, hx, hy);
        }
        return top;
    }

    /** 골드 박스 (오른쪽 위) */
    static int goldBox(GuiGraphics g, Font font, int w, int y) {
        Ui.goldBox(g, font, w - 4, y, String.format("%,d G", ClientData.gold), true);
        return y + 15;
    }

    /** 오른쪽: 진행 중 퀘스트 목표 (최대 3개) */
    private static void questTracker(GuiGraphics g, Font font, int w, int h) {
        if (Minecraft.getInstance().screen != null) return;
        var list = ClientQuests.tracked();   // 끝난 것 자동 → 고정한 것 (J창 [고정])
        if (list.isEmpty()) return;
        // 줄 모으기 (글자, 색, 제목 여부) → 배경 판 크기 계산 → 그리기
        java.util.List<Object[]> lines = new java.util.ArrayList<>();
        int shown = 0;
        for (var q : list) {
            if (shown >= 3) break;
            boolean done = ClientQuests.complete(q);
            if (shown > 0) lines.add(new Object[]{"", 0, false});
            lines.add(new Object[]{(done ? "✔ " : "") + q.name(), done ? 0xFF80E080 : 0xFFFFD86A, true});
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
        int more = ClientQuests.active().size() - shown;
        if (more > 0) lines.add(new Object[]{"+" + more + "개 더 (J에서 고정)", 0xFF909090, false});
        int width = font.width("퀘스트");
        for (Object[] l : lines) width = Math.max(width, font.width((String) l[0]) + ((boolean) l[2] ? 0 : 6));
        int pad = 6, right = w - 4, x0 = right - width - pad * 2, y = topY + 4;
        int height = 13 + lines.size() * 10 + pad;
        Ui.hudBox(g, x0, y, right, y + height);
        g.drawString(font, "퀘스트", x0 + pad, y + 4, Ui.VAL, true);
        int ty = y + 16;
        for (Object[] l : lines) {
            String s = (String) l[0];
            if (!s.isEmpty()) g.drawString(font, s, (boolean) l[2] ? x0 + pad : x0 + pad + 6, ty, (int) l[1], true);
            ty += 10;
        }
    }

    /** 왼쪽 중간 조금 위: 파티원 체력 (나 빼고) */
    private static void partyHud(GuiGraphics g, Font font, int w, int h) {
        String me = Minecraft.getInstance().getUser().getName();
        int y = h / 2 - 70;
        for (rpg.core.net.Net.Member m : ClientParty.MEMBERS) {
            if (m.name().equals(me)) continue;
            Ui.hudBox(g, 4, y, 112, y + 24);
            g.drawString(font, Ui.cut(font, (m.leader() ? "★" : "") + m.name(), 64), 9, y + 4, m.down() ? Ui.RED : m.near() ? Ui.TXT : Ui.GRAY, true);
            String r2 = m.down() ? "다운!" : "Lv." + m.level();
            g.drawString(font, r2, 107 - font.width(r2), y + 4, m.down() ? Ui.RED : Ui.GRAY, false);
            double r = m.maxHp() <= 0 ? 0 : Math.max(0, Math.min(1, m.hp() / m.maxHp()));
            Ui.bar(g, 9, y + 15, 98, 4, (float) r, m.down() ? 0xFF808080 : 0xFFC82832);
            y += 28;
        }
    }

    /** 내가 다운됐을 때 / 부활 진행 */
    private static void downHud(GuiGraphics g, Font font, int w, int h) {
        if (ClientData.downLeft < 0) return;
        int left = ClientData.left(ClientData.downLeft);
        g.fill(0, 0, w, h, 0x50300000);
        int bw = 220, x0 = w / 2 - bw / 2, y0 = h / 2 - 48;
        Ui.hudBox(g, x0, y0, x0 + bw, y0 + 42);
        g.drawCenteredString(font, "쓰러졌어요!", w / 2, y0 + 6, Ui.RED);
        g.drawCenteredString(font, (left + 19) / 20 + "초 안에 동료가 우클릭하면 부활", w / 2, y0 + 18, Ui.TXT);
        if (ClientData.reviveProgress >= 0) Ui.bar(g, x0 + 20, y0 + 32, bw - 40, 4, ClientData.reviveProgress / 100f, 0xFF60D060);
    }

    /** HP 바 (보호막 있을 때): 바는 보호막 색, 글자 "HP 현재+보호막/최대" */
    private static void shieldBar(GuiGraphics g, Font font, int x, int y, int bw, int bh) {
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xFF000000);
        g.fill(x, y, x + bw, y + bh, 0xFF4A3A10);
        double max = ClientData.maxHp;
        double r = max <= 0 ? 0 : Math.max(0, Math.min(1, ClientData.hp / max));
        g.fill(x, y, x + (int) (bw * r), y + bh, 0xFFE8B830);
        g.fill(x, y, x + (int) (bw * r), y + 2, 0xFFFFF0A0);   // 윗줄 반짝
        String s = "HP " + (long) Math.ceil(ClientData.hp) + "+" + (long) Math.ceil(ClientData.shield) + "/" + (long) Math.ceil(max);
        g.drawString(font, s, x + bw / 2 - font.width(s) / 2, y + 1, 0xFFFFFFFF, true);
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
