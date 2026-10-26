package rpg.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import rpg.core.RpgCore;

/** 스나이핑 조준선: 조준하는 동안 손 → 시선 방향 14칸 얇은 빨간 선 (쏘기 직전 0.2초는 하얗게) */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class AimLaser {
    private AimLaser() {}

    private record Aim(long start, long end) {}
    private static final Map<Integer, Aim> AIMS = new HashMap<>();

    public static void start(int entityId, int ticks) {
        long now = System.currentTimeMillis();
        AIMS.put(entityId, new Aim(now, now + ticks * 50L));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || AIMS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { AIMS.clear(); return; }
        long now = System.currentTimeMillis();
        AIMS.values().removeIf(a -> now > a.end());
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        float pt = e.getPartialTick();
        for (var en : AIMS.entrySet()) {
            Entity p = mc.level.getEntity(en.getKey());
            if (p == null) continue;
            boolean me = p == mc.player, first = mc.options.getCameraType().isFirstPerson();
            Vec3 from, to;
            if (me) {   // 내 것: 화면 가운데(조준점) 방향 = 실제로 화살이 나가는 방향. Epic Fight 가 몸을 이동 방향으로 돌려도 안 틀어짐
                Vec3 camPos = e.getCamera().getPosition(), camLook = new Vec3(e.getCamera().getLookVector());
                Vec3 eye = p.getEyePosition(pt);
                double reach = 14 + camPos.distanceTo(eye);
                var hit = mc.level.clip(new ClipContext(camPos, camPos.add(camLook.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                to = hit.getType() == HitResult.Type.MISS ? camPos.add(camLook.scale(reach)) : hit.getLocation();
                if (first) from = camPos.add(camLook.scale(0.8)).subtract(new Vec3(e.getCamera().getUpVector()).scale(0.22));
                else {
                    Vec3 dir = to.subtract(eye).normalize();
                    Vec3 right = dir.cross(new Vec3(0, 1, 0));
                    right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                    from = eye.add(right.scale(0.25)).add(0, -0.25, 0).add(dir.scale(0.4));
                }
            } else {
                Vec3 eye = p.getEyePosition(pt), look = p.getViewVector(pt);
                Vec3 right = look.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                from = eye.add(right.scale(0.25)).add(0, -0.25, 0).add(look.scale(0.4));   // 손 근처에서 시작
                Vec3 far = eye.add(look.scale(14));
                var hit = mc.level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                to = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
            }
            boolean ready = en.getValue().end() - now <= 200;
            float r = 1f, g = ready ? 1f : 0.15f, b = ready ? 1f : 0.15f, a = 0.85f;
            beam(vc, ps.last().pose(), from.subtract(cam), to.subtract(cam), 0.018f, r, g, b, a);
        }
        buf.endBatch(RenderType.lightning());
    }

    /** 두 장 십자 모양 얇은 판 (어느 방향에서 봐도 선으로 보이게) */
    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float w, float r, float g, float bl, float al) {
        Vec3 dir = b.subtract(a).normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(up).normalize().scale(w), s2 = dir.cross(s1).normalize().scale(w);
        for (Vec3 s : new Vec3[]{s1, s2}) {
            quad(vc, m, a.add(s), a.subtract(s), b.subtract(s), b.add(s), r, g, bl, al);
            quad(vc, m, b.add(s), b.subtract(s), a.subtract(s), a.add(s), r, g, bl, al);   // 뒷면
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, float r, float g, float b, float a) {
        for (Vec3 p : new Vec3[]{p1, p2, p3, p4}) vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }
}
