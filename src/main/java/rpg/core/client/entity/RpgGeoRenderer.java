package rpg.core.client.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
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
        this.cull = !id.equals("rat");   // 쥐는 얇은 판(귀·수염)이 있어서 양면 그대로
        if (glow) addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** 뒷면 그리기 끔 → 맞붙은 면끼리 깜빡이며 겹쳐 보이는 현상 없앰 */
    private final boolean cull;

    @Override
    public RenderType getRenderType(GeoRpgMonster e, ResourceLocation texture, MultiBufferSource buf, float pt) {
        return cull ? RenderType.entityCutout(texture) : super.getRenderType(e, texture, buf, pt);
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
        /** 갓이 큰 몹은 고개를 덜 돌림 (갓이 휘청거려 보이지 않게): 좌우·상하 배율, 최대 각도 */
        private final float yawMul, pitchMul, yawMax, pitchMax;
        Model(String id) {
            super(RpgCore.id(id), false);
            boolean bigCap = id.equals("mush_soldier");
            yawMul = bigCap ? 0.5f : 1f; pitchMul = bigCap ? 0.35f : 1f;
            yawMax = bigCap ? 30f : 75f; pitchMax = bigCap ? 12f : 45f;
        }

        @Override
        public void setCustomAnimations(GeoRpgMonster e, long instanceId, AnimationState<GeoRpgMonster> st) {
            super.setCustomAnimations(e, instanceId, st);
            CoreGeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData d = st.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head != null && d != null) {
                float pitch = Mth.clamp(d.headPitch() * pitchMul, -pitchMax, pitchMax);
                float yaw = Mth.clamp(d.netHeadYaw() * yawMul, -yawMax, yawMax);
                head.setRotX(head.getRotX() + pitch * Mth.DEG_TO_RAD);
                head.setRotY(head.getRotY() + yaw * Mth.DEG_TO_RAD);
            }
        }
    }
}
