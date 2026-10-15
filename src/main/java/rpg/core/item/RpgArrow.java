package rpg.core.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** 궁수 화살: 중력 없이 직선, 블록에 맞거나 MAX_DIST 칸 넘게 날아가면 사라짐 (주울 수 없음, 피해는 RPG 공식) */
public class RpgArrow extends Arrow {
    public static final double MAX_DIST = 40;
    private final Vec3 start;
    /** 사거리 (바람 읽기로 늘어남) */
    public double range = MAX_DIST;

    public RpgArrow(Level level, LivingEntity shooter) {
        super(level, shooter);
        this.start = shooter.getEyePosition();
        setNoGravity(true);
        setBaseDamage(0.5);   // 실제 피해는 RPG 공식 (CombatEvents)
        pickup = AbstractArrow.Pickup.DISALLOWED;
        getPersistentData().putBoolean("rpgArrow", true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (tickCount > 100 || position().distanceToSqr(start) > range * range)) discard();
    }

    @Override
    protected void onHitEntity(net.minecraft.world.phys.EntityHitResult hit) {
        if (getPersistentData().contains("rpgExplode")) {   // 폭발탄: 직접 피해 없이 그 자리에서 터짐
            if (!level().isClientSide) explode(hit.getLocation());
            discard();
            return;
        }
        super.onHitEntity(hit);
    }

    private void explode(Vec3 at) {
        if (getOwner() instanceof net.minecraft.server.level.ServerPlayer sp)
            rpg.core.skill.BranchSkills.explode(sp, at, getPersistentData().getDouble("rpgExplode"), getPersistentData().getDouble("rpgExplodeR"));
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        if (!level().isClientSide && getPersistentData().contains("rpgExplode")) explode(hit.getLocation());
        if (level() instanceof ServerLevel l) {
            Vec3 p = hit.getLocation();
            l.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 4, 0.05, 0.05, 0.05, 0.1);
        }
        playSound(getDefaultHitGroundSoundEvent(), 0.6f, 1.2f / (random.nextFloat() * 0.2f + 0.9f));
        discard();
    }
}
