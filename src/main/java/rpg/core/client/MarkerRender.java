package rpg.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import rpg.core.RpgCore;
import rpg.core.world.Markers;

/** /rpg 보기 표시: 사냥터 상자 · 보스방 원 · 지점 기둥 + 이름 (32칸 안) */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class MarkerRender {
    private MarkerRender() {}

    public static final List<Markers.M> LIST = new ArrayList<>();
    private static final int[] COL = {0x40E040, 0xFF4040, 0xFFD24A, 0xFFD24A, 0xFF9040, 0x50A0FF, 0xC060FF, 0xE040A0, 0xFF60D0};

    public static void set(List<Markers.M> l) { LIST.clear(); LIST.addAll(l); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 c = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        Matrix4f m = ps.last().pose(); Matrix3f n = ps.last().normal();
        List<Object[]> labels = new ArrayList<>();
        for (Markers.M k : LIST) {
            double cx = (k.x1() + k.x2()) / 2.0 + (k.type() == 0 ? 0 : 0.5), cz = (k.z1() + k.z2()) / 2.0 + (k.type() == 0 ? 0 : 0.5);
            double near = k.type() == 0 ? Math.max(Math.abs(k.x2() - k.x1()), Math.abs(k.z2() - k.z1())) / 2.0 + 32 : 48 + k.r();
            if ((cx - c.x) * (cx - c.x) + (cz - c.z) * (cz - c.z) > near * near) continue;
            int col = COL[Math.max(0, Math.min(COL.length - 1, k.type()))];
            float r = (col >> 16 & 255) / 255f, g = (col >> 8 & 255) / 255f, b = (col & 255) / 255f;
            double ox = -c.x, oy = -c.y, oz = -c.z;
            switch (k.type()) {
                case 0 -> {   // 상자
                    box(vc, m, n, k.x1() + ox, k.y1() + oy, k.z1() + oz, k.x2() + ox, k.y2() + oy, k.z2() + oz, r, g, b);
                    labels.add(new Object[]{new Vec3(cx, k.y2() + 0.5, cz), k.label(), col});
                }
                case 1, 8 -> {
                    if (k.r() <= 0) pillar(vc, m, n, k, cx, cz, ox, oy, oz, r, g, b, labels, col);
                    else ring(vc, m, n, k, cx, cz, ox, oy, oz, r, g, b, labels, col);
                }
                default -> pillar(vc, m, n, k, cx, cz, ox, oy, oz, r, g, b, labels, col);   // 기둥 3칸
            }
        }
        buf.endBatch(RenderType.lines());
        Font font = mc.font;
        var cam = e.getCamera();
        for (Object[] lb : labels) {
            Vec3 p = (Vec3) lb[0];
            String s = (String) lb[1];
            ps.pushPose();
            ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
            ps.mulPose(cam.rotation());
            ps.scale(-0.025f, -0.025f, 0.025f);
            font.drawInBatch(s, -font.width(s) / 2f, 0, 0xFF000000 | (int) lb[2], true, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0x60000000, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Matrix3f n, Markers.M k, double cx, double cz, double ox, double oy, double oz,
                             float r, float g, float b, List<Object[]> labels, int col) {
        // 경기장 (보스·레이드): 울타리처럼 바닥 원 + 높이 1·3칸 원 + 기둥 (땅이 높아도 보이게) + 가운데 기둥
        double y = k.y1() + 0.05 + oy;
        int seg = Math.max(48, (int) (k.r() * 4));
        for (double h : new double[]{0, 1, 3}) for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
            line(vc, m, n, cx + Math.cos(a1) * k.r() + ox, y + h, cz + Math.sin(a1) * k.r() + oz, cx + Math.cos(a2) * k.r() + ox, y + h, cz + Math.sin(a2) * k.r() + oz, r, g, b);
        }
        for (int i = 0; i < 24; i++) {   // 15도마다 세로 기둥
            double a = Math.PI * 2 * i / 24;
            line(vc, m, n, cx + Math.cos(a) * k.r() + ox, y, cz + Math.sin(a) * k.r() + oz, cx + Math.cos(a) * k.r() + ox, y + 3, cz + Math.sin(a) * k.r() + oz, r, g, b);
        }
        line(vc, m, n, cx + ox, y, cz + oz, cx + ox, y + 4, cz + oz, r, g, b);
        line(vc, m, n, cx - 1 + ox, y, cz + oz, cx + 1 + ox, y, cz + oz, r, g, b);
        line(vc, m, n, cx + ox, y, cz - 1 + oz, cx + ox, y, cz + 1 + oz, r, g, b);
        labels.add(new Object[]{new Vec3(cx, k.y1() + 4.5, cz), k.label(), col});
    }

    private static void pillar(VertexConsumer vc, Matrix4f m, Matrix3f n, Markers.M k, double cx, double cz, double ox, double oy, double oz,
                               float r, float g, float b, List<Object[]> labels, int col) {
        double y = k.y1() + oy;
        line(vc, m, n, cx + ox, y, cz + oz, cx + ox, y + 3, cz + oz, r, g, b);
        box(vc, m, n, cx - 0.3 + ox, y, cz - 0.3 + oz, cx + 0.3 + ox, y + 0.1, cz + 0.3 + oz, r, g, b);
        labels.add(new Object[]{new Vec3(cx, k.y1() + 3.4, cz), k.label(), col});
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b) {
        float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4) return;
        dx /= len; dy /= len; dz /= len;
        vc.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, 1f).normal(n, dx, dy, dz).endVertex();
        vc.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, 1f).normal(n, dx, dy, dz).endVertex();
    }

    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, double x1, double y1, double z1, double x2, double y2, double z2, float r, float g, float b) {
        double[][] p = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}, {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
        int[][] ed = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] q : ed) line(vc, m, n, p[q[0]][0], p[q[0]][1], p[q[0]][2], p[q[1]][0], p[q[1]][1], p[q[1]][2], r, g, b);
    }
}
