package rpg.core.client.entity;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

/** 새 몹 모델 공통 움직임: 머리 돌리기, 네발/두발 걷기, 팔 휘두르기, 날개, 꼬리 */
public class RpgModel<T extends Mob> extends HierarchicalModel<T> {
    private final ModelPart root, head, body, legL, legR, armL, armR, wingL, wingR, tail;
    private final ModelPart[] legs = new ModelPart[4];

    public RpgModel(ModelPart root) {
        this.root = root;
        head = get(root, "head"); body = get(root, "body");
        legL = get(root, "leg_l"); legR = get(root, "leg_r");
        armL = get(root, "arm_l"); armR = get(root, "arm_r");
        wingL = get(root, "wing_l"); wingR = get(root, "wing_r");
        tail = get(root, "tail");
        for (int i = 0; i < 4; i++) legs[i] = get(root, "leg" + i);
    }

    private static ModelPart get(ModelPart root, String name) {
        try { return root.getChild(name); } catch (Exception e) { return null; }
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void setupAnim(T e, float limbSwing, float amount, float age, float headYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        if (head != null) {
            head.yRot += headYaw * Mth.DEG_TO_RAD;
            head.xRot += headPitch * Mth.DEG_TO_RAD;
        }
        float sw = Mth.cos(limbSwing * 0.6662f) * 1.4f * amount;
        if (legs[0] != null) {
            legs[0].xRot += sw; legs[3].xRot += sw;
            legs[1].xRot -= sw; legs[2].xRot -= sw;
        }
        if (legL != null) legL.xRot += sw;
        if (legR != null) legR.xRot -= sw;
        if (armL != null) armL.xRot -= sw * 0.6f;
        if (armR != null) armR.xRot += sw * 0.6f;
        if (attackTime > 0 && armR != null) {   // 공격: 오른팔(두 팔) 내려치기
            float a = Mth.sin(attackTime * Mth.PI);
            armR.xRot -= a * 2.0f;
            if (armL != null) armL.xRot -= a * 1.6f;
        } else if (attackTime > 0 && head != null && legs[0] != null) {
            head.xRot += Mth.sin(attackTime * Mth.PI) * 0.6f;   // 네발: 머리 들이받기
        }
        if (e.isAggressive() && armR != null && attackTime <= 0) {   // 싸울 땐 팔을 앞으로
            armR.xRot -= 0.4f;
            if (armL != null) armL.xRot -= 0.4f;
        }
        if (wingL != null) {
            float f = Mth.sin(age * 1.6f) * 0.6f + 0.3f;
            wingL.yRot -= f; wingR.yRot += f;
        }
        if (tail != null) tail.yRot += Mth.sin(age * 0.25f) * 0.35f;
        if (wingL != null && body != null) {   // 떠다니는 몹: 위아래로 살짝
            float bob = Mth.sin(age * 0.15f) * 0.8f;
            body.y += bob; head.y += bob; wingL.y += bob; wingR.y += bob;
        }
    }
}
