package rpg.core.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Set;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import rpg.core.RpgCore;
import rpg.core.entity.BossProp;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** 보스 패턴 소품·이펙트 렌더러: 크기 배율 (레이저는 굵기·길이 따로) + 발광 */
public class PropRenderer extends GeoEntityRenderer<BossProp> {
    /** 발광 마스크(_glowmask)가 있는 것 */
    private static final Set<String> GLOW = Set.of("boss_rock", "rock_pillar", "void_crystal", "magic_circle", "void_crack", "resonance_ring");
    /** 반투명 + 스스로 빛남 (레이저·독 웅덩이) */
    private static final Set<String> TRANSLUCENT = Set.of("void_beam", "spore_beam", "poison_pool");

    private final float scale;
    private final String id;

    public PropRenderer(EntityRendererProvider.Context ctx, String id, float scale) {
        super(ctx, new DefaultedEntityGeoModel<>(RpgCore.id(id), false));
        this.scale = scale;
        this.id = id;
        this.shadowRadius = GLOW.contains(id) && !id.equals("magic_circle") && !id.equals("void_crack") && !id.equals("resonance_ring") ? 0.4f * scale : 0;
        if (GLOW.contains(id)) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(BossProp e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (!e.visible()) return;
        ps.pushPose();
        if (!e.isBeam()) { float k = scale * e.size(); ps.scale(k, k, k); }
        super.render(e, yaw, pt, ps, buf, TRANSLUCENT.contains(id) ? 0xF000F0 : light);
        ps.popPose();
    }

    /** 레이저: 돌린 다음 굵기(x·y)·길이(z) 따로 늘림 */
    @Override
    protected void applyRotations(BossProp e, PoseStack ps, float age, float rotYaw, float pt) {
        super.applyRotations(e, ps, age, rotYaw, pt);
        if (e.isBeam()) ps.scale(e.size(), e.size(), e.len());
    }

    @Override
    public boolean shouldRender(BossProp e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.isBeam() || e.size() > 1.5f || super.shouldRender(e, f, x, y, z);   // 큰 것: 가운데가 화면 밖이어도 그림
    }

    @Override
    public RenderType getRenderType(BossProp e, ResourceLocation texture, MultiBufferSource buf, float pt) {
        return TRANSLUCENT.contains(id) ? RenderType.entityTranslucentEmissive(texture) : RenderType.entityCutout(texture);
    }
}
