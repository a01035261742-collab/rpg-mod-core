package rpg.core.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** 보스 체력바 (시안 m1): 맨 위, 장식 바 1.25배 (보스마다 장식 색), 위 가운데 이름 · 바 위 오른쪽 퍼센트 (2배).
 *  나올 때 1초 펼쳐지는 애니메이션 → 이름·퍼센트 0.2초 나타남. 깎인 자리 흰색으로 남았다 줄어듦.
 *  2페이즈 = 서버가 보라색으로 바꿈 → 채움 보라. 레이드 남은 시간(눈금 없는 바)은 마크 기본 그대로 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class BossBars {
    private BossBars() {}

    private static final Pattern NAME = Pattern.compile("(.*?) Lv\\.(\\d+)(?: · (.*))?");
    private static final Map<UUID, double[]> TRAIL = new HashMap<>();   // [흰 자리, 마지막 시각]
    /** 지금 보스바가 떠 있는 보스 이름 (머리 위 바 안 그리기) */
    public static final Set<String> ACTIVE = new HashSet<>();
    private static long activeAt;

    /** 바 그림 (팩 bossbar3, 12프레임 세로, 384×32), 프레임 11 의 채움 칸은 비워둠 → 코드로 채움 */
    private static final int FW = 384, FH = 32, FRAMES = 12;
    private static final int[][] FILL = {{14, 31, 352}, {15, 30, 353}, {16, 29, 354}, {17, 30, 353}, {18, 31, 352}};
    private static final int FL = 29, FR = 355, FTOP = 14;
    private static final Map<UUID, Long> START = new HashMap<>();
    /** 보스 이름 → 색 (없으면 보라) */
    private record Look(net.minecraft.resources.ResourceLocation tex, int text) {}
    private static final Look GREEN = new Look(RpgCore.id("textures/gui/bossbar/green.png"), 0xFF96EB78),
            ORANGE = new Look(RpgCore.id("textures/gui/bossbar/orange.png"), 0xFFFFB95A),
            PURPLE = new Look(RpgCore.id("textures/gui/bossbar/purple.png"), 0xFFE1A0F0);
    private static Look look(String name) {
        if (name.contains("숲")) return GREEN;
        if (name.contains("버섯")) return ORANGE;
        return PURPLE;
    }

    @SubscribeEvent
    public static void bar(CustomizeGuiOverlayEvent.BossEventProgress e) {
        BossEvent be = e.getBossEvent();
        if (be.getOverlay() != BossEvent.BossBarOverlay.NOTCHED_10) return;
        e.setCanceled(true);
        GuiGraphics g = e.getGuiGraphics();
        Font f = Minecraft.getInstance().font;
        int w = e.getWindow().getGuiScaledWidth();
        String raw = be.getName().getString(), name = raw, lv = null;
        Matcher m = NAME.matcher(raw);
        if (m.matches()) { name = m.group(1); lv = m.group(2); }
        long now = System.currentTimeMillis();
        if (now - activeAt > 1000) { ACTIVE.clear(); activeAt = now; }
        ACTIVE.add(name);
        boolean purple = be.getColor() == BossEvent.BossBarColor.PURPLE;
        Look lk = look(name);
        float k = Math.min(1.25f, (w - 24) / 372f);   // 바 1.25배 (좁은 화면은 맞춤)
        int barH = Math.round(31 * k);
        float ox = Math.round(w / 2f - 192 * k), oy = Math.round(e.getY() + 2 + 17 - 4 * k);   // 이름(2배, 17) 밑이 장식 뿔(5칸) 바로 위   // 그림 맨 위(1칸 투명) 를 y+4 에, 글자는 장식 사이
        e.setIncrement(barH + 22);
        // 등장 애니메이션 1초 (12프레임), 다 펼쳐지면 이름·퍼센트 0.2초 동안 나타남
        long st = START.computeIfAbsent(be.getId(), u -> now);
        if (START.size() > 32) { START.clear(); TRAIL.clear(); START.put(be.getId(), st); }
        long age = now - st;
        int frame = (int) Math.min(FRAMES - 1, age * FRAMES / 1000);
        float r = Math.max(0, Math.min(1, be.getProgress()));
        // [흰 자리, 마지막 그린 시각, 마지막 깎인 시각, 지난 체력] — 깎이면 0.6초 그대로 있다가 줄어듦
        double[] t = TRAIL.computeIfAbsent(be.getId(), u -> new double[]{r, now, 0, r});
        if (r > t[0]) t[0] = r;
        if (r < t[3] - 1e-4) t[2] = now;
        t[3] = r;
        double dt = Math.max(0, Math.min(0.1, (now - t[1]) / 1000.0));   // (시각은 double — float 은 ms 가 뭉개짐)
        t[1] = now;
        if (now - t[2] > 600) t[0] = Math.max(r, t[0] - dt * 0.5);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(k, k, 1);
        g.blit(lk.tex(), 0, 0, 0, frame * FH, FW, FH, FW, FH * FRAMES);
        if (frame == FRAMES - 1) {
            int span = FR - FL, xr = FL + Math.round(span * r), xt = FL + (int) Math.round(span * t[0]);
            for (int[] row : FILL) {
                int y = row[0], a = row[1], b = row[2] + 1;
                int c = y == 14 ? (purple ? 0xFFC896FF : 0xFFFF8030) : y == 18 ? (purple ? 0xFF46205E : 0xFF47151F) : (purple ? 0xFF9650DC : 0xFFAC3232);
                int e1 = Math.max(a, Math.min(b, xr)), e2 = Math.max(a, Math.min(b, xt));
                if (e1 > a) g.fill(a, y, e1, y + 1, c);
                if (e2 > e1) g.fill(e1, y, e2, y + 1, 0xFFFAF0D7);
                if (b > e2) g.fill(e2, y, b, y + 1, 0xFF280E12);
            }
        }
        g.pose().popPose();
        // 이름(왼쪽) · 퍼센트(오른쪽), 1.5배 + 검은 테
        int ta = (int) Math.max(0, Math.min(255, (age - 1000) * 255 / 200));
        if (ta > 8) {
            String title = lv != null ? "Lv." + lv + "  " + name : name;
            String pct = Math.round(r * 100) + "%";
            float ty = Math.round(oy + FTOP * k - 21);
            outlined(g, f, title, Math.round(w / 2f - f.width(title)), e.getY() + 2, (ta << 24) | (lk.text() & 0xFFFFFF), ta);
            outlined(g, f, pct, Math.round(ox + FR * k - 12 * k) - f.width(pct) * 2, ty, (ta << 24) | 0xFFF0C8, ta);
        }
    }

    private static void outlined(GuiGraphics g, Font f, String s, float x, float y, int col, int a) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2f, 2f, 1);   // 2배 (한글 글꼴은 정수 배율이어야 안 깨짐)
        int dark = (a << 24) | 0x140C10;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) {   // 테두리 1칸 (= 0.5 글자칸)
            g.pose().pushPose(); g.pose().translate(dx * 0.5f, dy * 0.5f, 0); g.drawString(f, s, 0, 0, dark, false); g.pose().popPose();
        }
        g.drawString(f, s, 0, 0, col, false);
        g.pose().popPose();
    }
}
