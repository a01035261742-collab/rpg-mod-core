package rpg.core.entity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.level.Level;

/** 보스 숲의 주인: 큰 곰. 눈 높이를 몸통 가운데쯤(1.4칸)으로 낮춤 → Epic Fight 락온이 위로 꺾이지 않게 */
public class ForestLord extends BrownBear {
    public ForestLord(EntityType<? extends PolarBear> type, Level level) {
        super(type, level);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dims) {
        return 1.4f;
    }
}
