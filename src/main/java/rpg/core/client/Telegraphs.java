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

    /** 추적 예고: 클라가 매 프레임 대상 위치로 그림 */
    private record K(Net.Track t, long end) {}
    private static final List<K> TRACKS = new ArrayList<>();

    public static void track(Net.Track t) {
        if (t.follow() < 0 || t.ticks() <= 0) { TRACKS.removeIf(k -> k.t().owner() == t.owner()); return; }
        TRACKS.removeIf(k -> k.t().owner() == t.owner() && k.t().follow() == t.follow());
        TRACKS.add(new K(t, System.currentTimeMillis() + t.ticks() * 50L));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || (LIST.isEmpty() && TRACKS.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIST.clear(); TRACKS.clear(); return; }
        long now = System.currentTimeMillis();
        LIST.removeIf(x -> now > x.end() + 150);
        TRACKS.removeIf(k -> now > k.end());
        float pt = e.getPartialTick();
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
            if (t.shape() >= 50) {   // 독 늪 (탁한 황록, 위험): 차오름 없이 그대로
                Net.Telegraph b = new Net.Telegraph(t.shape() - 50, t.x(), t.y(), t.z(), t.dx(), t.dz(), t.a(), t.b(), t.ticks());
                float yy = y + 0.02f;
                switch (b.shape()) {
                    case 1 -> line(vc, m, b, yy, 1f, 0x80809018, 0);
                    case 2 -> cone(vc, m, b, yy, 1f, 0x80809018);
                    default -> circle(vc, m, b, yy, 1f, 0x80809018);
                }
                continue;
            }
            if (t.shape() >= 40) {   // 보라 예고 (나눠 맞기 등): 차오름
                Net.Telegraph b = new Net.Telegraph(t.shape() - 40, t.x(), t.y(), t.z(), t.dx(), t.dz(), t.a(), t.b(), t.ticks());
                switch (b.shape()) {
                    case 1 -> { line(vc, m, b, y, 1f, 0x55B050FF, 0); line(vc, m, b, y + 0.01f, p, 0x99A030F0, 0); }
                    case 2 -> { cone(vc, m, b, y, 1f, 0x55B050FF); cone(vc, m, b, y + 0.01f, p, 0x99A030F0); }
                    default -> { circle(vc, m, b, y, 1f, 0x55B050FF); circle(vc, m, b, y + 0.01f, p, 0x99A030F0); }
                }
                continue;
            }
            if (t.shape() >= 30) {   // 초록 (안전 구역): 차오름 없이 그대로
                Net.Telegraph b = new Net.Telegraph(t.shape() - 30, t.x(), t.y(), t.z(), t.dx(), t.dz(), t.a(), t.b(), t.ticks());
                float yy = y + 0.02f;
                switch (b.shape()) {
                    case 1 -> line(vc, m, b, yy, 1f, 0x7040E060, 0);
                    case 2 -> cone(vc, m, b, yy, 1f, 0x7040E060);
                    default -> circle(vc, m, b, yy, 1f, 0x7040E060);
                }
                continue;
            }
            if (t.shape() >= 10 && t.shape() < 20) {   // 추적 중 (노랑, 차오름 없음)
                Net.Telegraph b = new Net.Telegraph(t.shape() - 10, t.x(), t.y(), t.z(), t.dx(), t.dz(), t.a(), t.b(), t.ticks());
                switch (b.shape()) {
                    case 1 -> line(vc, m, b, y, 1f, 0x66FFD020, 0);
                    case 2 -> cone(vc, m, b, y, 1f, 0x66FFD020);
                    default -> circle(vc, m, b, y, 1f, 0x66FFD020);
                }
                continue;
            }
            if (t.shape() >= 20) {   // 파란 예고 (카운터 등): 차오름
                Net.Telegraph b = new Net.Telegraph(t.shape() - 20, t.x(), t.y(), t.z(), t.dx(), t.dz(), t.a(), t.b(), t.ticks());
                switch (b.shape()) {
                    case 1 -> { line(vc, m, b, y, 1f, 0x5530A0FF, 0); line(vc, m, b, y + 0.01f, p, 0x992080FF, 0); }
                    case 2 -> { cone(vc, m, b, y, 1f, 0x5530A0FF); cone(vc, m, b, y + 0.01f, p, 0x992080FF); }
                    default -> { circle(vc, m, b, y, 1f, 0x5530A0FF); circle(vc, m, b, y + 0.01f, p, 0x992080FF); }
                }
                continue;
            }
            switch (t.shape()) {
                case 1 -> { line(vc, m, t, y, 1f, 0x55FF2020, 0); line(vc, m, t, y + 0.01f, p, 0x99FF1010, 0); }
                case 2 -> { cone(vc, m, t, y, 1f, 0x55FF2020); cone(vc, m, t, y + 0.01f, p, 0x99FF1010); }
                default -> { circle(vc, m, t, y, 1f, 0x55FF2020); circle(vc, m, t, y + 0.01f, p, 0x99FF1010); }
            }
        }
        // 추적 예고 (노랑): 보스·대상의 지금 프레임 위치
        for (K k : TRACKS) {
            Net.Track tr = k.t();
            var owner = mc.level.getEntity(tr.owner());
            var fol = mc.level.getEntity(tr.follow());
            if (owner == null || fol == null) continue;
            Vec3 o = owner.getPosition(pt), f = fol.getPosition(pt);
            Net.Telegraph b;
            if (tr.shape() == 1) {
                Vec3 d = new Vec3(f.x - o.x, 0, f.z - o.z);
                d = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
                b = new Net.Telegraph(1, o.x, o.y, o.z, d.x, d.z, tr.a(), tr.b(), 1);
                float y = (float) (groundY(mc, b) + 0.05);
                line(vc, m, b, y, 1f, 0x66FFD020, 0);
            } else {
                b = new Net.Telegraph(0, f.x, f.y, f.z, 1, 0, tr.a(), 0, 1);
                float y = (float) (groundY(mc, b) + 0.05);
                circle(vc, m, b, y, 1f, tr.shape() == 3 ? 0x77B050FF : 0x66FFD020);   // 3 = 보라 (나눠 맞기)
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
