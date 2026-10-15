package rpg.core.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import rpg.core.entity.GeoRpgMonster;

/**
 * RPG 몹 근접 공격: 몸 크기와 상관없이 "몸 바깥에서 REACH 칸" 안에 들어와야 때림
 * (Epic Fight 단검 판정 약 1.2칸에 맞춤 — 큰 몹이 멀리서 때리지 않게)
 * GeckoLib 몹은 공격 애니메이션을 먼저 시작하고, 무기가 닿는 순간에 피해 (그 사이 피하면 안 맞음)
 */
public class RpgMeleeGoal extends MeleeAttackGoal {
    public static final double REACH = 1.2;
    /** 타격 순간 판정 여유 (애니 도중 조금 물러나도 맞는 거리 배율) */
    private static final double IMPACT_SLACK = 1.3;

    private int pendingHit;          // 0 = 없음, 남은 틱
    private LivingEntity pendingTarget;

    public RpgMeleeGoal(PathfinderMob mob, double speed) {
        super(mob, speed, true);
    }

    @Override
    protected double getAttackReachSqr(LivingEntity target) {
        double r = mob.getBbWidth() / 2 + target.getBbWidth() / 2 + REACH;
        return r * r;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distSqr) {
        if (!(mob instanceof GeoRpgMonster geo)) { super.checkAndPerformAttack(target, distSqr); return; }
        if (pendingHit > 0) return;   // 휘두르는 중
        if (distSqr <= getAttackReachSqr(target) && isTimeToAttack()) {
            resetAttackCooldown();
            mob.swing(InteractionHand.MAIN_HAND);      // 애니메이션 시작
            pendingTarget = target;
            pendingHit = Math.max(1, geo.attackImpactTicks());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingHit > 0 && --pendingHit == 0) {
            LivingEntity t = pendingTarget;
            pendingTarget = null;
            if (t != null && t.isAlive() && mob.isAlive()
                    && mob.distanceToSqr(t) <= getAttackReachSqr(t) * IMPACT_SLACK * IMPACT_SLACK)
                mob.doHurtTarget(t);
        }
    }

    @Override
    public void stop() {
        super.stop();
        pendingHit = 0;
        pendingTarget = null;
    }
}
