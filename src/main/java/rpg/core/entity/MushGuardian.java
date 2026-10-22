package rpg.core.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** 보스 거대버섯 수호자: 버섯 짐승 2배. 눈 높이 낮춤 (Epic Fight 락온이 위로 안 꺾이게) */
public class MushGuardian extends RpgMonster {
    public MushGuardian(EntityType<? extends Monster> type, Level level) {
        super(type, level, 2);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 1.8f;
    }
}
