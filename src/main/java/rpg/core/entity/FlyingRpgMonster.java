package rpg.core.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 떠다니는 몹 (버섯 요정): 중력 없음, 날아서 쫓아옴 */
public class FlyingRpgMonster extends RpgMonster {
    public FlyingRpgMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level, 3);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder flyAttrs() {
        return attrs(0.3).add(Attributes.FLYING_SPEED, 0.45);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public void tick() {
        super.tick();
        // 둥실둥실: 땅에서 1.5칸쯤 떠 있게
        if (!level().isClientSide && getTarget() == null) {
            Vec3 v = getDeltaMovement();
            boolean low = !level().getBlockState(blockPosition().below(2)).isAir();
            setDeltaMovement(v.x, low ? 0.04 : -0.02, v.z);
        }
    }

    @Override public boolean causeFallDamage(float d, float m, net.minecraft.world.damagesource.DamageSource s) { return false; }
    @Override protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState s, net.minecraft.core.BlockPos p) {}
}
