package rpg.core.combat;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import rpg.core.balance.Balance;
import rpg.core.entity.GeoRpgMonster;

/**
 * RPG 몹 근접 공격.
 * - mobs.json 에 attacks 가 있는 GeckoLib 몹: Epic Fight 방식 판정
 *   준비(대상 쪽으로 돎, 판정 0.25초 전부터 방향 고정) → 판정 구간(방향 고정, 매 틱 범위 안 플레이어를 한 번씩 때림) → 마무리(잠깐 멈춤)
 *   모션은 우리 GeckoLib 애니메이션, 범위는 몸 끝 기준 (바닐라 근접 정도)
 * - 그 외: 바닐라 방식 (몸 바깥 REACH 칸 안이면 바로 때림)
 */
public class RpgMeleeGoal extends MeleeAttackGoal {
    public static final double REACH = 1.2;
    /** 판정 시작 몇 틱 전부터 방향 고정 (0.25초) */
    private static final int LOCK_BEFORE = 5;

    private Balance.MobAttack cur;      // 진행 중인 공격 (null = 없음)
    private int t;                      // 공격 시작 후 틱
    private float lockedYaw;
    private int next;                   // 다음에 쓸 공격 번호 (번갈아)
    private final Set<Integer> hit = new HashSet<>();

    public RpgMeleeGoal(PathfinderMob mob, double speed) {
        super(mob, speed, true);
    }

    @Override
    protected double getAttackReachSqr(LivingEntity target) {
        double r = mob.getBbWidth() / 2 + target.getBbWidth() / 2 + REACH;
        return r * r;
    }

    /** 이 몹의 EF 방식 공격 목록 (없으면 빈 목록) */
    private List<Balance.MobAttack> attacks() {
        if (!(mob instanceof GeoRpgMonster)) return List.of();
        MobData m = Combat.mob(mob);
        if (m == null) return List.of();
        Balance.MobDef def = Balance.MOBS.get(m.key);
        return def == null ? List.of() : def.attacks();
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double distSqr) {
        List<Balance.MobAttack> list = attacks();
        if (list.isEmpty()) { super.checkAndPerformAttack(target, distSqr); return; }
        if (cur != null || !isTimeToAttack()) return;
        MobData sm = Combat.mob(mob);
        if (sm != null && mob.level().getGameTime() < sm.stunUntil) return;   // 경직 중
        MobData m = Combat.mob(mob);
        if (m != null && m.casting >= 0) return;            // 패턴 쓰는 중
        Balance.MobAttack a = list.get(next % list.size());
        double reach = mob.getBbWidth() / 2 + target.getBbWidth() / 2 + a.range() * 0.9;
        if (distSqr > reach * reach) return;
        next++;
        cur = a; t = 0; hit.clear();
        resetAttackCooldown();
        mob.getNavigation().stop();
        ((GeoRpgMonster) mob).playAttack(a.anim());
    }

    /** 경직 중이면 true (준비 중 공격은 끊음, 판정 구간이면 그대로 휘두름) */
    private boolean stunned() {
        MobData m = Combat.mob(mob);
        if (m == null) return false;
        long now = mob.level().getGameTime();
        if (m.stunCancel) {
            m.stunCancel = false;
            if (cur != null && t < cur.start()) {             // 준비 중이면 공격 취소 + 애니메이션 끊기
                cur = null;
                if (mob instanceof GeoRpgMonster g) g.stopAttack();
            }
        }
        return now < m.stunUntil;
    }

    @Override
    public void tick() {
        boolean stun = stunned();
        if (cur == null) {
            if (stun) { mob.getNavigation().stop(); return; }
            super.tick();
            return;
        }
        LivingEntity target = mob.getTarget();
        mob.getNavigation().stop();
        if (t < cur.start() - LOCK_BEFORE) {
            // 준비: 대상 쪽으로 몸을 돌림 (맞기 LOCK_BEFORE 틱 전부터는 고정 → 옆으로 비키면 피함)
            if (target != null) {
                mob.getLookControl().setLookAt(target, 30, 30);
                double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
                float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
                mob.setYRot(Mth.approachDegrees(mob.getYRot(), yaw, 30f));
            }
            lockedYaw = mob.getYRot();
        } else {
            // 판정 구간 + 마무리: 방향 고정
            mob.setYRot(lockedYaw);
            mob.yBodyRot = lockedYaw;
            mob.yHeadRot = lockedYaw;
            // 타격 순간 앞으로 한 발 내딛기 (EF 몹처럼) → 뒤로 걷기만 해서는 못 피함, 옆으로 비키거나 구르기
            if (t == cur.start() && cur.lunge() > 0) {
                float r = lockedYaw * Mth.DEG_TO_RAD;
                mob.setDeltaMovement(mob.getDeltaMovement().add(-Mth.sin(r) * cur.lunge(), 0, Mth.cos(r) * cur.lunge()));
            }
            if (t >= cur.start() && t <= cur.end()) swingHits();
        }
        if (++t >= cur.length()) cur = null;
    }

    /** 판정 범위 안 플레이어를 한 번씩 때림 */
    private void swingHits() {
        double half = mob.getBbWidth() / 2;
        double far = half + cur.range();
        AABB box = mob.getBoundingBox().inflate(far + 1, 0.5, far + 1);
        float rad = lockedYaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(rad), fz = Mth.cos(rad);        // 앞 방향
        for (Player p : mob.level().getEntitiesOfClass(Player.class, box, pl -> pl.isAlive() && !pl.isSpectator() && !pl.isCreative())) {
            if (hit.contains(p.getId())) continue;
            double dx = p.getX() - mob.getX(), dz = p.getZ() - mob.getZ();
            double ph = p.getBbWidth() / 2;
            double fwd = dx * fx + dz * fz;                    // 앞으로 거리
            double side = -dx * fz + dz * fx;                  // 옆 거리
            boolean in;
            if (cur.angle() > 0) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                double ang = Math.toDegrees(Math.atan2(Math.abs(side), Math.max(fwd, 1e-4)));
                double slack = Math.toDegrees(Math.atan2(ph, Math.max(dist, 0.1)));   // 몸 두께만큼 각도 여유
                in = dist - ph <= far && fwd > -ph && ang <= cur.angle() / 2 + slack;
            } else {
                in = fwd > 0 && fwd - ph <= far && Math.abs(side) - ph <= cur.width() / 2;
            }
            if (!in) continue;
            hit.add(p.getId());
            if (mob.doHurtTarget(p) && cur.knock() > 0) p.knockback(cur.knock(), -fx, -fz);
        }
    }

    @Override
    public void stop() {
        super.stop();
        cur = null;
        hit.clear();
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }
}
