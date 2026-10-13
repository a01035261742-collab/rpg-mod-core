package rpg.core.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * RPG 몹 근접 공격: 몸 크기와 상관없이 "몸 바깥에서 REACH 칸" 안에 들어와야 때림
 * (Epic Fight 단검 판정 약 1.2칸에 맞춤 — 큰 몹이 멀리서 때리지 않게)
 */
public class RpgMeleeGoal extends MeleeAttackGoal {
    public static final double REACH = 1.2;

    public RpgMeleeGoal(PathfinderMob mob, double speed) {
        super(mob, speed, true);
    }

    @Override
    protected double getAttackReachSqr(LivingEntity target) {
        double r = mob.getBbWidth() / 2 + target.getBbWidth() / 2 + REACH;
        return r * r;
    }
}
