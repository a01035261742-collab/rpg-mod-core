package rpg.core.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** 레이드 보스 어둠거미여왕 (GeckoLib 모델). 2페이즈 = 배에 공허석 */
public class SpiderQueen extends GeoRpgMonster {
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> PHASE2 =
            net.minecraft.network.syncher.SynchedEntityData.defineId(SpiderQueen.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    public SpiderQueen(EntityType<? extends Monster> type, Level level) {
        super(type, level, 3);
        swingAnim = false;
        hurtAnim = false;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE2, false);
    }

    public boolean phase2() { return entityData.get(PHASE2); }
    public void setPhase2(boolean on) { entityData.set(PHASE2, on); }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 1.5f;
    }
}
