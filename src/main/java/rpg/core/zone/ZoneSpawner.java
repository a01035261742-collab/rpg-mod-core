package rpg.core.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.combat.Combat;
import rpg.core.combat.MobData;
import rpg.core.combat.Mobs;

/** 사냥터 몹 리젠 + 사냥 맵 자연 스폰 막기 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class ZoneSpawner {
    private ZoneSpawner() {}

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % 20 != 0) return;   // 1초마다
        ZoneData data = ZoneData.get(level);
        for (Zone z : data.zones.values()) {
            if (++z.timer < Math.max(1, z.interval)) continue;
            z.timer = 0;
            var box = z.box();
            // 근처(48칸)에 플레이어가 없으면 쉼
            if (level.getNearestPlayer(box.getCenter().x, box.getCenter().y, box.getCenter().z, 48 + box.getSize(), false) == null) continue;
            int alive = level.getEntitiesOfClass(Mob.class, box.inflate(24), m -> {
                MobData d = Combat.mob(m);
                return d != null && z.name.equals(d.zone);
            }).size();
            int need = z.max - alive;
            // 한 번에 최대 3마리씩 채움
            for (int i = 0; i < Math.min(need, 3); i++) {
                BlockPos pos = findSpot(level, z, level.random);
                if (pos == null) continue;
                String[] ms = z.mobs();
                String key = ms[level.random.nextInt(ms.length)];
                int lv = z.level + (z.levelMax > z.level ? level.random.nextInt(z.levelMax - z.level + 1) : 0);
                Mobs.spawn(level, key, lv, pos, z.name);
            }
            // 구역에서 멀리 벗어난 몹은 정리 (다시 리젠됨)
            level.getEntitiesOfClass(Mob.class, box.inflate(64), m -> {
                MobData d = Combat.mob(m);
                return d != null && z.name.equals(d.zone) && !box.inflate(16).contains(m.position());
            }).forEach(Mob::discard);
        }
    }

    /** 구역 안 무작위 x,z 에서 발 디딜 곳 (위에서 아래로) */
    private static BlockPos findSpot(ServerLevel level, Zone z, RandomSource r) {
        for (int tries = 0; tries < 8; tries++) {
            int x = z.min.getX() + r.nextInt(z.max2.getX() - z.min.getX() + 1);
            int zz = z.min.getZ() + r.nextInt(z.max2.getZ() - z.min.getZ() + 1);
            // 땅 블록을 찍어서 높이가 0인 구역도 되게: 위로 4칸, 아래로 2칸 더 찾기
            for (int y = z.max2.getY() + 4; y >= z.min.getY() - 2; y--) {
                BlockPos p = new BlockPos(x, y, zz);
                BlockState below = level.getBlockState(p.below());
                if (below.isSolid() && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) return p;
            }
        }
        return null;
    }

    /** 사냥 맵(설정한 차원)에서는 자연 스폰 막기 */
    @SubscribeEvent
    public static void blockNatural(MobSpawnEvent.FinalizeSpawn e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!Balance.huntingDimensions.contains(level.dimension().location().toString())) return;
        switch (e.getSpawnType()) {
            case NATURAL, CHUNK_GENERATION, PATROL, REINFORCEMENT, JOCKEY -> e.setSpawnCancelled(true);
            default -> {}
        }
    }
}
