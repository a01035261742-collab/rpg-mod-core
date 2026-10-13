package rpg.core.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * GeckoLib 애니메이션 몹. Blockbench 애니메이션 이름 규칙: walk · idle · attack
 * (파일: assets/rpgcore/geo/entity/&lt;id&gt;.geo.json, animations/entity/&lt;id&gt;.animation.json)
 */
public class GeoRpgMonster extends RpgMonster implements GeoEntity {
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GeoRpgMonster(EntityType<? extends Monster> type, Level level, int voice) {
        super(type, level, voice);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "move", 4, s ->
                s.setAndContinue(s.isMoving() ? WALK : IDLE)));
        c.add(new AnimationController<>(this, "attack", 2, s -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK));
    }

    /** 근접 공격 순간(공격 goal 이 swing 호출) → 공격 애니메이션 (서버에서 모든 클라로 동기화) */
    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        if (!level().isClientSide) triggerAnim("attack", "attack");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
