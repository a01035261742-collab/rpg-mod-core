package rpg.core.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** 보스 거대버섯 수호자: 침식된 늙은 버섯 거인 (GeckoLib). 눈 높이 낮춤 (Epic Fight 락온이 위로 안 꺾이게) */
public class MushGuardian extends GeoRpgMonster {
    /** 2페이즈: 공허 결정 · 보라 눈 (1페이즈 = 결정 없음 · 초록 눈) */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> PHASE2 =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MushGuardian.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PHASE2, false);
    }

    public boolean phase2() { return entityData.get(PHASE2); }
    public void setPhase2(boolean on) { entityData.set(PHASE2, on); }

    public MushGuardian(EntityType<? extends Monster> type, Level level) {
        super(type, level, 2);
        swingAnim = false;
        hurtAnim = false;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 1.8f;
    }
}
