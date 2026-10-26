package rpg.core.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;

/** 레이드 보스 어둠거미여왕 (임시 몸: 거미 3배, 전용 모델은 2단계) */
public class SpiderQueen extends Spider {
    public SpiderQueen(EntityType<? extends Spider> type, Level level) {
        super(type, level);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 1.5f;
    }

    @Override
    public boolean onClimbable() { return false; }   // 벽 타기 없음
}
