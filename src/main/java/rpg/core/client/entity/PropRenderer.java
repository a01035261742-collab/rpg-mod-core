package rpg.core.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import rpg.core.RpgCore;
import rpg.core.entity.BossProp;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** 보스 패턴 소품 렌더러 (바위·기둥·결정): 크기 배율 + 결정 발광 */
public class PropRenderer extends GeoEntityRenderer<BossProp> {
    private final float scale;

    public PropRenderer(EntityRendererProvider.Context ctx, String id, float scale) {
        super(ctx, new DefaultedEntityGeoModel<>(RpgCore.id(id), false));
        this.scale = scale;
        this.shadowRadius = 0.4f * scale;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(BossProp e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (!e.visible()) return;
        ps.pushPose();
        ps.scale(scale, scale, scale);
        super.render(e, yaw, pt, ps, buf, light);
        ps.popPose();
    }

    @Override
    public RenderType getRenderType(BossProp e, ResourceLocation texture, MultiBufferSource buf, float pt) {
        return RenderType.entityCutout(texture);
    }
}
