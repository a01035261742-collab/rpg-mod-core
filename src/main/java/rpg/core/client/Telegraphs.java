package rpg.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import rpg.core.RpgCore;
import rpg.core.net.Net;

/** 바닥 예고: 연한 빨강 범위 + 가운데(또는 시작점)부터 진한 빨강이 차오름, 다 차면 공격 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class Telegraphs {
    private Telegraphs() {}

    private record T(Net.Telegraph t, long start, long end) {}
    private static final List<T> LIST = new ArrayList<>();

    public static void add(Net.Telegraph t) {
        long now = System.currentTimeMillis();
        LIST.add(new T(t, now, now + t.ticks() * 50L));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIST.clear(); return; }
        long now = System.currentTimeMillis();
        LIST.removeIf(x -> now > x.end() + 150);
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());
        for (T x : LIST) {
            float p = (float) Math.min(1, Math.max(0, (now - x.start()) / (double) Math.max(1, x.end() - x.start())));
            Net.Telegraph t = x.t();
            float y = (float) (groundY(mc, t) + 0.06);
            if (t.shape() >= 10) {   // 추적 중 (노랑, 차오름 없음)
                Net.Telegraph b = new Net.Telegraph(t.shape() - 10, t.x(), t.y(), t.z(), t.dx(), t.dz(), t.a(), t.b(), t.ticks());
                switch (b.shape()) {
                    case 1 -> line(vc, m, b, y, 1f, 0x66FFD020, 0);
                    case 2 -> cone(vc, m, b, y, 1f, 0x66FFD020);
                    default -> circle(vc, m, b, y, 1f, 0x66FFD020);
                }
                continue;
            }
            switch (t.shape()) {
                case 1 -> { line(vc, m, t, y, 1f, 0x55FF2020, 0); line(vc, m, t, y + 0.01f, p, 0x99FF1010, 0); }
                case 2 -> { cone(vc, m, t, y, 1f, 0x55FF2020); cone(vc, m, t, y + 0.01f, p, 0x99FF1010); }
                default -> { circle(vc, m, t, y, 1f, 0x55FF2020); circle(vc, m, t, y + 0.01f, p, 0x99FF1010); }
            }
        }
        buf.endBatch(RenderType.debugQuads());
        ps.popPose();
    }

    /** 예고 위치 바로 아래 땅 높이 */
    private static double groundY(Minecraft mc, Net.Telegraph t) {
        var pos = net.minecraft.core.BlockPos.containing(t.x(), t.y() + 0.5, t.z());
        for (int i = 0; i < 4; i++) {
            if (!mc.level.getBlockState(pos.below()).isAir()) return pos.getY();
            pos = pos.below();
        }
        return t.y();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x1, float z1, float x2, float z2, float x3, float z3,
                             float x4, float z4, float y, int argb) {
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        vc.vertex(m, x1, y, z1).color(r, g, b, a).endVertex();
        vc.vertex(m, x2, y, z2).color(r, g, b, a).endVertex();
        vc.vertex(m, x3, y, z3).color(r, g, b, a).endVertex();
        vc.vertex(m, x4, y, z4).color(r, g, b, a).endVertex();
    }

    /** 원: 반경 a × 진행도 */
    private static void circle(VertexConsumer vc, Matrix4f m, Net.Telegraph t, float y, float p, int col) {
        float r = t.a() * p, cx = (float) t.x(), cz = (float) t.z();
        int n = 32;
        for (int i = 0; i < n; i++) {
            double a1 = Math.PI * 2 * i / n, a2 = Math.PI * 2 * (i + 1) / n;
            float x1 = cx + (float) Math.cos(a1) * r, z1 = cz + (float) Math.sin(a1) * r;
            float x2 = cx + (float) Math.cos(a2) * r, z2 = cz + (float) Math.sin(a2) * r;
            quad(vc, m, cx, cz, x2, z2, x1, z1, cx, cz, y, col);   // 위에서 보이게 (반시계)
            quad(vc, m, cx, cz, x1, z1, x2, z2, cx, cz, y, col);   // 아래에서도
        }
    }

    /** 직선: 시작점에서 방향으로 길이 a, 폭 b. 진행도만큼 앞으로 차오름 */
    private static void line(VertexConsumer vc, Matrix4f m, Net.Telegraph t, float y, float p, int col, int unused) {
        float dx = (float) t.dx(), dz = (float) t.dz();
        float len = t.a() * p, hw = t.b() / 2;
        float sx = (float) t.x(), sz = (float) t.z();
        float px = -dz * hw, pz = dx * hw;
        float ex = sx + dx * len, ez = sz + dz * len;
        quad(vc, m, sx + px, sz + pz, ex + px, ez + pz, ex - px, ez - pz, sx - px, sz - pz, y, col);
        quad(vc, m, sx - px, sz - pz, ex - px, ez - pz, ex + px, ez + pz, sx + px, sz + pz, y, col);
    }

    /** 부채꼴: 반경 a, 각도 b */
    private static void cone(VertexConsumer vc, Matrix4f m, Net.Telegraph t, float y, float p, int col) {
        float r = t.a() * p, cx = (float) t.x(), cz = (float) t.z();
        double base = Math.atan2(t.dz(), t.dx()), half = Math.toRadians(t.b() / 2);
        int n = 16;
        for (int i = 0; i < n; i++) {
            double a1 = base - half + 2 * half * i / n, a2 = base - half + 2 * half * (i + 1) / n;
            float x1 = cx + (float) Math.cos(a1) * r, z1 = cz + (float) Math.sin(a1) * r;
            float x2 = cx + (float) Math.cos(a2) * r, z2 = cz + (float) Math.sin(a2) * r;
            quad(vc, m, cx, cz, x2, z2, x1, z1, cx, cz, y, col);
            quad(vc, m, cx, cz, x1, z1, x2, z2, cx, cz, y, col);
        }
    }
}
