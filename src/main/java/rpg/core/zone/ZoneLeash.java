package rpg.core.zone;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.combat.Combat;
import rpg.core.combat.MobData;
import rpg.core.combat.Mobs;

/**
 * 사냥터 몹 묶어두기.
 * - 돌아다닐 때: 구역 경계가 벽 (밖으로 한 칸도 안 나감)
 * - 쫓을 때: 구역 밖 CHASE 칸까지만, 더 멀어지면 포기하고 구역 안으로 돌아감 (돌아가는 동안 무적 · 안 노림, 도착하면 체력 가득)
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class ZoneLeash {
    private ZoneLeash() {}

    public static final double CHASE = 6;

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel l) || !mob.isAlive() || mob.tickCount % 5 != 0) return;
        MobData m = Combat.mob(mob);
        if (m == null || m.zone.isEmpty() || rpg.core.combat.Boss.isBoss(m)) return;
        Zone z = ZoneData.get(l).zones.get(m.zone);
        if (z == null) return;
        AABB b = z.box();
        double x = mob.getX(), zz = mob.getZ();
        double dx = Math.max(Math.max(b.minX - x, 0), x - b.maxX), dz = Math.max(Math.max(b.minZ - zz, 0), zz - b.maxZ);
        double out = Math.sqrt(dx * dx + dz * dz);
        // 돌아갈 자리: 경계에서 2칸 안쪽
        double tx = clamp(x, b.minX + 2, b.maxX - 2), tz = clamp(zz, b.minZ + 2, b.maxZ - 2);
        long now = l.getGameTime();

        if (m.returning) {
            if (mob.getTarget() != null) mob.setTarget(null);
            if (out <= 0 && Math.abs(x - tx) < 2.5 && Math.abs(zz - tz) < 2.5 || now - m.returnSince > 200) {
                if (out > 0) mob.teleportTo(tx, mob.getY(), tz);   // 10초 안에 못 돌아오면 (길 막힘) 순간이동
                m.returning = false;
                m.hp = m.maxHp;
                Mobs.sync(mob);
                l.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 6, 0.4, 0.3, 0.4, 0);
            } else if (mob.tickCount % 10 == 0) mob.getNavigation().moveTo(tx, mob.getY(), tz, 1.4);
            return;
        }
        boolean chasing = mob.getTarget() != null;
        if (chasing) {
            if (out > CHASE) {   // 너무 멀리 끌려 나옴 → 포기하고 돌아감
                m.returning = true;
                m.returnSince = now;
                mob.setTarget(null);
                mob.getNavigation().moveTo(tx, mob.getY(), tz, 1.4);
            }
            return;
        }
        if (out > 0) {   // 돌아다니다 경계 밖 → 벽처럼 경계 안으로 되돌림
            double cx = clamp(x, b.minX + 0.4, b.maxX - 0.4), cz = clamp(zz, b.minZ + 0.4, b.maxZ - 0.4);
            if (out > 3) { m.returning = true; m.returnSince = now; mob.getNavigation().moveTo(tx, mob.getY(), tz, 1.2); return; }   // 멀리 밀려남
            mob.setPos(cx, mob.getY(), cz);
            mob.getNavigation().stop();
        } else if (mob.tickCount % 20 == 0 && !mob.getNavigation().isDone()) {   // 바깥으로 걸어가는 길이면 멈춤
            var path = mob.getNavigation().getPath();
            var end = path == null ? null : path.getEndNode();
            if (end != null && (end.x < b.minX || end.x >= b.maxX || end.z < b.minZ || end.z >= b.maxZ)) mob.getNavigation().stop();
        }
    }

    /** 돌아가는 중에는 새로 노리지 않음 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void noTarget(LivingChangeTargetEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || mob.level().isClientSide || e.getNewTarget() == null) return;
        MobData m = Combat.mob(mob);
        if (m != null && m.returning) e.setCanceled(true);
    }

    /** 돌아가는 중에는 안 맞음 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void immune(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || mob.level().isClientSide) return;
        MobData m = Combat.mob(mob);
        if (m != null && m.returning) e.setCanceled(true);
    }

    private static double clamp(double v, double a, double b) { return a > b ? (a + b) / 2 : Math.max(a, Math.min(b, v)); }
}
