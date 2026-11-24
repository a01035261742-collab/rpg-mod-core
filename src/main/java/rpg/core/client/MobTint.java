package rpg.core.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** 변종 바닐라 몹 (벌·거미·박쥐·슬라임·골렘): 몸 전체에 색을 덧씌워 그림 (텍스처 파일 없이) */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class MobTint {
    private MobTint() {}

    /** 다시 그리는 중인 몹 (같은 몹의 Pre 가 또 오면 그냥 통과) */
    private static final java.util.Set<LivingEntity> DRAWING = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pre(RenderLivingEvent.Pre e) {
        LivingEntity le = e.getEntity();
        if (le instanceof rpg.core.entity.GeoRpgMonster || DRAWING.contains(le)) return;   // GeckoLib 몹은 전용 텍스처
        ClientMobs.Info i = ClientMobs.MAP.get(le.getId());
        if (i == null || i.tint() < 0) return;
        int c = i.tint();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        MultiBufferSource src = e.getMultiBufferSource();
        MultiBufferSource tinted = type -> {
            VertexConsumer v = src.getBuffer(type);
            return type.toString().contains("text") ? v : new Tinted(v, r, g, b);   // 이름표 글씨는 그대로
        };
        e.setCanceled(true);
        DRAWING.add(le);
        try {
            e.getRenderer().render(le, le.getYRot(), e.getPartialTick(), e.getPoseStack(), tinted, e.getPackedLight());
        } finally {
            DRAWING.remove(le);
        }
    }

    /** 정점 색에 몸 색을 곱함 */
    private record Tinted(VertexConsumer in, float r, float g, float b) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { in.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int cr, int cg, int cb, int ca) { in.color((int) (cr * r), (int) (cg * g), (int) (cb * b), ca); return this; }
        @Override public VertexConsumer uv(float u, float v) { in.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { in.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { in.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { in.normal(x, y, z); return this; }
        @Override public void endVertex() { in.endVertex(); }
        @Override public void defaultColor(int cr, int cg, int cb, int ca) { in.defaultColor((int) (cr * r), (int) (cg * g), (int) (cb * b), ca); }
        @Override public void unsetDefaultColor() { in.unsetDefaultColor(); }

        @Override
        public void vertex(float x, float y, float z, float cr, float cg, float cb, float ca, float u, float v, int ov, int lt, float nx, float ny, float nz) {
            in.vertex(x, y, z, cr * r, cg * g, cb * b, ca, u, v, ov, lt, nx, ny, nz);
        }
    }
}
