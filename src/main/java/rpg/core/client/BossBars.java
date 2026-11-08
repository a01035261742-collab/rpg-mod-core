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

/** 보스 체력바 (시안 L210): 위 가운데, 1.5배 현판 이름 + 바로 밑 금테 바 420×14, 깎인 자리 흰색으로 남았다 줄어듦.
 *  2페이즈 = 서버가 보라색으로 바꿈 → 바·테두리 보라. 레이드 남은 시간(눈금 없는 바)은 마크 기본 그대로 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class BossBars {
    private BossBars() {}

    private static final Pattern NAME = Pattern.compile("(.*?) Lv\\.(\\d+)(?: · (.*))?");
    private static final Map<UUID, float[]> TRAIL = new HashMap<>();   // [흰 자리, 마지막 시각]
    /** 지금 보스바가 떠 있는 보스 이름 (머리 위 바 안 그리기) */
    public static final Set<String> ACTIVE = new HashSet<>();
    private static long activeAt;

    public static final int HALF = 210, BH = 14;
    private static final net.minecraft.resources.ResourceLocation PL = RpgCore.id("textures/gui/ui/plaque_l.png"),
            PM = RpgCore.id("textures/gui/ui/plaque_m.png"), PR = RpgCore.id("textures/gui/ui/plaque_r.png");

    @SubscribeEvent
    public static void bar(CustomizeGuiOverlayEvent.BossEventProgress e) {
        BossEvent be = e.getBossEvent();
        if (be.getOverlay() != BossEvent.BossBarOverlay.NOTCHED_10) return;
        e.setCanceled(true);
        GuiGraphics g = e.getGuiGraphics();
        Font f = Minecraft.getInstance().font;
        int w = e.getWindow().getGuiScaledWidth();
        int top = e.getY() - 10;
        e.setIncrement(50);
        String raw = be.getName().getString(), name = raw, lv = null, sub = null;
        Matcher m = NAME.matcher(raw);
        if (m.matches()) { name = m.group(1); lv = m.group(2); sub = m.group(3); }
        long now = System.currentTimeMillis();
        if (now - activeAt > 1000) { ACTIVE.clear(); activeAt = now; }
        ACTIVE.add(name);
        boolean purple = be.getColor() == BossEvent.BossBarColor.PURPLE;
        // 이름 현판 1.5배
        String title = lv != null ? "Lv." + lv + "  " + name : name;
        // 현판 (높이 26, 세 조각 정수 위치에 딱 붙여서 → 틈 없음), 글자 1.5배는 안쪽 남색 칸 가운데
        int ph = 26, ew = 14, mw = Math.round(f.width(title) * 1.5f) + 21, px0 = w / 2 - mw / 2 - ew;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(PL, px0, top, ew, ph, 0, 0, 18, 28, 18, 28);
        g.blit(PM, px0 + ew, top, mw, ph, 0, 0, 4, 28, 4, 28);
        g.blit(PR, px0 + ew + mw, top, ew, ph, 0, 0, 18, 28, 18, 28);
        g.pose().pushPose();
        g.pose().translate(w / 2f, top + ph * 15.5f / 28f - 5.5f, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(f, title, -f.width(title) / 2, 0, Ui.VAL, true);
        g.pose().popPose();
        // 바 (현판 바로 밑, 안 겹침)
        int half = Math.min(HALF, w / 2 - 20);
        int x0 = w / 2 - half, x1 = w / 2 + half, by = top + ph + 3;
        int edge = purple ? 0xFFBE78FF : Ui.G4;
        g.fill(x0 - 3, by - 3, x1 + 3, by + BH + 3, Ui.OUT);
        g.fill(x0 - 2, by - 2, x1 + 2, by + BH + 2, purple ? 0xFF6A3A9A : Ui.G2);
        g.fill(x0 - 2, by - 2, x1 + 2, by - 1, edge);
        g.fill(x0 - 1, by - 1, x1 + 1, by + BH + 1, Ui.IN1);
        float r = Math.max(0, Math.min(1, be.getProgress()));
        float[] t = TRAIL.computeIfAbsent(be.getId(), k -> new float[]{r, now});
        if (r > t[0]) t[0] = r;   // 회복·새 페이즈
        float dt = Math.min(0.1f, (now - (long) t[1]) / 1000f);
        t[1] = now;
        t[0] = Math.max(r, t[0] - dt * 0.35f);   // 흰 자리가 천천히 줄어듦
        int w0 = x1 - x0, xr = x0 + Math.round(w0 * r), xt = x0 + Math.round(w0 * t[0]);
        int c = purple ? 0xFF9650DC : 0xFFC82828, hi = purple ? 0xFFC896FF : 0xFFFF6E6E, lo = purple ? 0xFF642E9A : 0xFF8C1C1C;
        g.fill(x0, by, x1, by + BH, 0xFF280E12);
        if (xt > xr) g.fill(xr, by, xt, by + BH, 0xFFFAF0D7);
        if (xr > x0) {
            g.fill(x0, by, xr, by + BH, c);
            g.fill(x0, by, xr, by + 1, hi);
            g.fill(x0, by + BH - 1, xr, by + BH, lo);
        }
        for (int i = 1; i < 10; i++) { int xx = x0 + w0 * i / 10; g.fill(xx, by + BH * 3 / 5, xx + 1, by + BH, 0xFF000000); }
        String pct = Math.round(r * 100) + "%";
        g.drawString(f, pct, w / 2 - f.width(pct) / 2, by + (BH - 8) / 2, 0xFFFFFFFF, true);
    }
}
