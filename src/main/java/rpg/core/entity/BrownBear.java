package rpg.core.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.level.Level;

/** 산곰: 북극곰 몸에 갈색 털 */
public class BrownBear extends PolarBear {
    public BrownBear(EntityType<? extends PolarBear> type, Level level) {
        super(type, level);
    }
}
