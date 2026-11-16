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
        this.shadowRadius = id.equals("magic_circle") ? 0 : 0.4f * scale;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(BossProp e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        if (!e.visible()) return;
        ps.pushPose();
        float k = scale * e.size();
        ps.scale(k, k, k);
        super.render(e, yaw, pt, ps, buf, light);
        ps.popPose();
    }

    @Override
    public boolean shouldRender(BossProp e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) {
        return e.size() > 1.5f || super.shouldRender(e, f, x, y, z);   // 큰 마법진: 가운데가 화면 밖이어도 그림
    }

    @Override
    public RenderType getRenderType(BossProp e, ResourceLocation texture, MultiBufferSource buf, float pt) {
        return RenderType.entityCutout(texture);
    }
}
