package rpg.core.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Slime;
import rpg.core.balance.Balance;
import rpg.core.net.Net;

/** RPG 몹 만들기 */
public final class Mobs {
    private Mobs() {}

    /** mobs.json 의 key 몹을 level 로 pos 에 소환. 실패하면 null */
    public static Mob spawn(ServerLevel level, String key, int lv, BlockPos pos, String zone) {
        Balance.MobDef def = Balance.MOBS.get(key);
        if (def == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(def.entity());
        if (id == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null, null);
        if (mob instanceof Slime slime) slime.setSize(2, true);
        mob.setPersistenceRequired();
        apply(mob, def, lv, zone);
        level.addFreshEntity(mob);
        return mob;
    }

    public static void apply(Mob mob, Balance.MobDef def, int lv, String zone) {
        MobData m = Combat.mob(mob);
        if (m == null) return;
        Balance.MobStats s = def.at(lv);
        m.key = def.key(); m.name = def.name(); m.zone = zone == null ? "" : zone;
        m.level = lv; m.maxHp = Math.max(1, s.hp()); m.hp = m.maxHp; m.atk = s.atk(); m.exp = s.exp(); m.gold = s.gold();
        mob.setHealth(mob.getMaxHealth());
    }

    /** 근처 플레이어들에게 이 몹의 레벨·체력 알리기 */
    public static void sync(Mob mob) {
        MobData m = Combat.mob(mob);
        if (m != null && m.isRpg()) Net.mobInfo(mob, m);
    }
}
