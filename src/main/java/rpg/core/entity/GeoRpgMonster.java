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
 * GeckoLib 애니메이션 몹. Blockbench 애니메이션 이름 규칙: walk · idle · attack (· attack2 = 반대손)
 * (파일: assets/rpgcore/geo/entity/&lt;id&gt;.geo.json, animations/entity/&lt;id&gt;.animation.json)
 */
public class GeoRpgMonster extends RpgMonster implements GeoEntity {
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation ATTACK2 = RawAnimation.begin().thenPlay("attack2");

    /** 양손 번갈아 공격 애니메이션(attack ↔ attack2)이 있는 몹 */
    private static final java.util.Set<String> ALT_ATTACK = java.util.Set.of("mush_spirit", "mush_soldier");

    /** 패턴 애니메이션 (Blockbench 이름 = 패턴 종류). 없는 몹은 안 부름 */
    private static final String[] PATTERNS = {"slam", "leap", "dash", "shot", "zone", "cone"};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean otherHand;

    public GeoRpgMonster(EntityType<? extends Monster> type, Level level, int voice) {
        super(type, level, voice);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "move", 4, s ->
                s.setAndContinue(s.isMoving() ? WALK : IDLE)));
        c.add(new AnimationController<>(this, "attack", 2, s -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK).triggerableAnim("attack2", ATTACK2));
        AnimationController<GeoRpgMonster> pat = new AnimationController<>(this, "pattern", 2, s -> PlayState.STOP);
        for (String n : PATTERNS) pat.triggerableAnim(n, RawAnimation.begin().thenPlay(n));
        c.add(pat);
    }

    /** 패턴 애니메이션 재생 (서버) */
    public void playPattern(String type) {
        triggerAnim("pattern", type);
    }

    /** 예고가 끝나기 몇 틱 전에 애니메이션을 시작해야 타격 순간이 맞는지 (애니 안의 착지 시점) */
    public int patternLead(String type) {
        return switch (type) {
            case "slam" -> 11;   // 0.54초에 쿵
            case "zone" -> 18;   // 0.9초에 내리찍기 (버섯 정령)
            default -> 0;
        };
    }

    /** 근접 공격 순간(공격 goal 이 swing 호출) → 공격 애니메이션 (서버에서 모든 클라로 동기화) */
    @Override
    public void swing(InteractionHand hand, boolean updateSelf) {
        super.swing(hand, updateSelf);
        if (level().isClientSide) return;
        boolean alt = ALT_ATTACK.contains(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(getType()).getPath());
        triggerAnim("attack", alt && otherHand ? "attack2" : "attack");
        if (alt) otherHand = !otherHand;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
