package rpg.core.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import rpg.core.RpgCore;
import rpg.core.client.WorldText;
import rpg.core.entity.GeoRpgMonster;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** GeckoLib 몹 공통 렌더러: 모델·애니·텍스처는 id 로 자동 연결, 머리는 시선 방향 추가 회전, 엘리트 1.3배, 선택적 발광 마스크 */
public class RpgGeoRenderer extends GeoEntityRenderer<GeoRpgMonster> {
    public RpgGeoRenderer(EntityRendererProvider.Context ctx, String id, float shadow) {
        this(ctx, id, shadow, false);
    }

    /** glow = textures/entity/<id>_glowmask.png 부분을 어두운 곳에서도 밝게 (눈 등) */
    public RpgGeoRenderer(EntityRendererProvider.Context ctx, String id, float shadow, boolean glow) {
        super(ctx, new Model(id));
        this.shadowRadius = shadow;
        if (glow) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(GeoRpgMonster e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        boolean elite = WorldText.isElite(e);
        if (elite) { ps.pushPose(); ps.scale(1.3f, 1.3f, 1.3f); }
        super.render(e, yaw, pt, ps, buf, light);
        if (elite) ps.popPose();
    }

    /** 애니메이션 위에 머리 시선(좌우·상하)을 더함 — 기본 모델은 덮어써서 물기 동작이 사라짐 */
    static class Model extends DefaultedEntityGeoModel<GeoRpgMonster> {
        Model(String id) { super(RpgCore.id(id), false); }

        @Override
        public void setCustomAnimations(GeoRpgMonster e, long instanceId, AnimationState<GeoRpgMonster> st) {
            super.setCustomAnimations(e, instanceId, st);
            CoreGeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData d = st.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && d != null) {
                head.setRotX(head.getRotX() + d.headPitch() * Mth.DEG_TO_RAD);
                head.setRotY(head.getRotY() + d.netHeadYaw() * Mth.DEG_TO_RAD);
            }
        }
    }
}
