package rpg.core.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** 곰덫 (블록 모델, 1/16칸 단위). 펼쳐져 있다가 snap 초부터 양쪽 톱니 턱이 0.07초 만에 닫힘 */
final class Trap {
    private Trap() {}
    static final float U = 1 / 16f, R = 11 * U;

    static void draw(SkillFx.D d, Vec3 c, double scale, double snapT) {
        int light = d.light(c.add(0, 0.5, 0));
        VertexConsumer metal = d.buf.getBuffer(RenderType.entityCutoutNoCull(SkillFx.D.tex("trap_metal")));
        VertexConsumer tooth = d.buf.getBuffer(RenderType.entityCutoutNoCull(SkillFx.D.tex("trap_tooth")));
        PoseStack ps = new PoseStack();
        ps.last().pose().set(d.m);
        ps.last().normal().set(d.nm);
        ps.translate(c.x - d.cam.x, c.y - d.cam.y, c.z - d.cam.z);
        ps.scale((float) scale, (float) scale, (float) scale);
        for (int i = 0; i < 12; i++) {   // 바닥 테
            double a = (i + 0.5) / 12 * Math.PI * 2;
            part(ps, metal, light, Math.cos(a) * R, U / 2, Math.sin(a) * R, -(a + Math.PI / 2), 0, 6, 1, 2);
        }
        part(ps, metal, light, 0, 1.5 * U, 0, 0, 0, 6, 1, 6);   // 발판
        part(ps, metal, light, 0, 2.5 * U, 0, 0, 0, 2, 1, 2);
        for (int sx = -1; sx <= 1; sx += 2) part(ps, metal, light, sx * (R + 2 * U), U, 0, 0, 0, 3, 2, 8);   // 스프링
        double cl = snapT <= 0 ? 0 : Math.min(1, snapT / 0.07);
        double bounce = snapT > 0 ? 0.15 * Math.sin(Math.min(1, snapT / 0.2) * Math.PI) : 0;
        for (int k = 0; k < 2; k++) {   // 톱니 턱 두 개 (가운데 가로축으로 접힘)
            double rx = k == 0 ? (Math.PI / 2 - 0.04) * cl - bounce : -(Math.PI / 2 - 0.04) * cl + bounce;
            ps.pushPose();
            ps.translate(0, 1.5 * U, 0);
            ps.mulPose(Axis.XP.rotation((float) rx));
            for (int i = 0; i < 6; i++) {
                double a = (k == 1 ? 0 : Math.PI) + (i + 0.5) / 6 * Math.PI;
                part(ps, metal, light, Math.cos(a) * R, 0, Math.sin(a) * R, -(a + Math.PI / 2), 0, 6, 2, 1);
                double ta = (k == 1 ? 0 : Math.PI) + (i + (k == 1 ? 0.75 : 0.25)) / 6 * Math.PI;
                part(ps, tooth, light, Math.cos(ta) * R, 2 * U, Math.sin(ta) * R, -(ta + Math.PI / 2), 0, 2, 2, 1);
                part(ps, tooth, light, Math.cos(ta) * R, 3.6 * U, Math.sin(ta) * R, -(ta + Math.PI / 2), 0, 1, 1.5, 1);
            }
            ps.popPose();
        }
    }

    /** 상자 하나: 중심(블록) · Y 회전 · 크기(1/16칸). 면마다 크기만큼 텍스처 (1픽셀 = 1/16칸) */
    static void part(PoseStack ps, VertexConsumer vc, int light, double x, double y, double z, double ry, double rx, double w, double h, double dd) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotation((float) ry));
        float X = (float) (w * U / 2), Y = (float) (h * U / 2), Z = (float) (dd * U / 2);
        float uw = (float) (w / 16), uh = (float) (h / 16), ud = (float) (dd / 16);
        var pose = ps.last();
        face(pose, vc, light, -X, Y, -Z, X, Y, -Z, X, Y, Z, -X, Y, Z, 0, 1, 0, uw, ud);     // 위
        face(pose, vc, light, -X, -Y, Z, X, -Y, Z, X, -Y, -Z, -X, -Y, -Z, 0, -1, 0, uw, ud); // 아래
        face(pose, vc, light, -X, -Y, Z, -X, Y, Z, X, Y, Z, X, -Y, Z, 0, 0, 1, uw, uh);     // 앞
        face(pose, vc, light, X, -Y, -Z, X, Y, -Z, -X, Y, -Z, -X, -Y, -Z, 0, 0, -1, uw, uh); // 뒤
        face(pose, vc, light, X, -Y, Z, X, Y, Z, X, Y, -Z, X, -Y, -Z, 1, 0, 0, ud, uh);     // 오른
        face(pose, vc, light, -X, -Y, -Z, -X, Y, -Z, -X, Y, Z, -X, -Y, Z, -1, 0, 0, ud, uh); // 왼
        ps.popPose();
    }

    private static void face(PoseStack.Pose p, VertexConsumer vc, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, float uw, float vh) {
        vc.vertex(p.pose(), x0, y0, z0).color(1f, 1f, 1f, 1f).uv(0, vh).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
        vc.vertex(p.pose(), x1, y1, z1).color(1f, 1f, 1f, 1f).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
        vc.vertex(p.pose(), x2, y2, z2).color(1f, 1f, 1f, 1f).uv(uw, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
        vc.vertex(p.pose(), x3, y3, z3).color(1f, 1f, 1f, 1f).uv(uw, vh).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.normal(), nx, ny, nz).endVertex();
    }
}
