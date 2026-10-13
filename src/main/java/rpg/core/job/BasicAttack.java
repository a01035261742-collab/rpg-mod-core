package rpg.core.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 원거리 평타: 궁수 = 화살(화살 소모 없음), 마법사 = 마법탄(즉발). 전사·도적은 바닐라 근접 */
public final class BasicAttack {
    private BasicAttack() {}

    private static final Map<UUID, Long> NEXT = new HashMap<>();

    public static void fire(ServerPlayer p) {
        PlayerData d = RpgData.get(p);
        if (d.isDown() || !p.isAlive() || (d.job != 2 && d.job != 3)) return;
        long now = p.level().getGameTime();
        if (now < NEXT.getOrDefault(p.getUUID(), 0L)) return;
        double speed = Math.max(0.2, p.getAttributeValue(Attributes.ATTACK_SPEED));
        NEXT.put(p.getUUID(), now + Math.max(4, Math.round(20 / speed)));
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        if (d.job == 2) arrow(p);
        else bolt(p);
    }

    private static void arrow(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Arrow a = new Arrow(lv, p);
        a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 3.0f, 0.5f);
        a.setBaseDamage(0.5);   // 실제 피해는 RPG 공식 (CombatEvents)
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        a.getPersistentData().putBoolean("rpgArrow", true);
        lv.addFreshEntity(a);
        lv.playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /** 바라보는 방향 첫 번째 몹에 즉시 명중 */
    private static void bolt(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double range = Balance.rangedRange;
        Vec3 end = eye.add(look.scale(range));
        var blockHit = lv.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = blockHit.getLocation();
        AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(lv, p, eye, end, box,
                e -> e instanceof Mob && e.isAlive() && !e.isSpectator());
        Vec3 to = hit != null ? hit.getLocation() : end;
        // 궤적
        Vec3 step = to.subtract(eye);
        int n = (int) Math.max(2, step.length() * 2);
        for (int i = 1; i <= n; i++) {
            Vec3 pos = eye.add(step.scale(i / (double) n)).add(0, -0.15, 0);
            lv.sendParticles(ParticleTypes.ENCHANTED_HIT, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
        lv.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.5f, 1.6f);
        if (hit != null && hit.getEntity() instanceof LivingEntity t) {
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().indirectMagic(p, p), 1.0f);
            lv.sendParticles(ParticleTypes.WITCH, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 6, 0.2, 0.2, 0.2, 0);
        }
    }
}
