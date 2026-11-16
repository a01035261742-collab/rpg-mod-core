package rpg.core.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** 보스 숲의 주인: 공허독 스친 거대 짐승 (GeckoLib 모델, 패턴마다 애니메이션). 눈 높이 1.4칸 → Epic Fight 락온이 위로 안 꺾이게 */
public class ForestLord extends GeoRpgMonster {
    public ForestLord(EntityType<? extends Monster> type, Level level) {
        super(type, level, 4);
        swingAnim = false;   // 근접은 패턴 애니(melee)로
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 1.4f;
    }
}
