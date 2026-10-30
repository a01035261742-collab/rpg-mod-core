package rpg.core.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/**
 * 보스 경기장 (차원별 저장): 중앙 · 반경 · 다음 등장 시각 · 지금 나와 있는 보스.
 * 보스가 없고 시간이 됐고 청크가 불러와져 있고 근처(64칸)에 플레이어가 있으면 중앙에 소환.
 * /rpg boss point add <보스> [반경]
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public class BossPoints extends SavedData {
    public static final class Point {
        public String id, key;
        public BlockPos pos;
        public double radius;
        public long nextSpawn;
        public UUID boss;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("id", id); t.putString("key", key);
            t.putInt("x", pos.getX()); t.putInt("y", pos.getY()); t.putInt("z", pos.getZ());
            t.putDouble("r", radius); t.putLong("next", nextSpawn);
            if (boss != null) t.putUUID("boss", boss);
            return t;
        }

        static Point load(CompoundTag t) {
            Point p = new Point();
            p.id = t.getString("id"); p.key = t.getString("key");
            p.pos = new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
            p.radius = t.getDouble("r"); p.nextSpawn = t.getLong("next");
            if (t.hasUUID("boss")) p.boss = t.getUUID("boss");
            return p;
        }
    }

    public final List<Point> points = new ArrayList<>();

    public static BossPoints get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BossPoints::load, BossPoints::new, "rpgcore_bosses");
    }

    public static BossPoints load(CompoundTag tag) {
        BossPoints d = new BossPoints();
        ListTag list = tag.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) d.points.add(Point.load(list.getCompound(i)));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Point p : points) list.add(p.save());
        tag.put("points", list);
        return tag;
    }

    public Point add(String key, BlockPos pos, double r) {
        Point p = new Point();
        int n = 1;
        while (find(key + "_" + n) != null) n++;
        p.id = key + "_" + n; p.key = key; p.pos = pos; p.radius = r; p.nextSpawn = 0;
        points.add(p);
        setDirty();
        return p;
    }

    public Point find(String id) {
        for (Point p : points) if (p.id.equals(id)) return p;
        return null;
    }

    /** 보스 소환 (경기장 정보를 몹에 적어둠) */
    public static Mob spawnAt(ServerLevel l, String key, BlockPos pos, double radius, String pointId) {
        Mob mob = Mobs.spawn(l, key, Boss.level(key), pos, "boss", false);
        if (mob == null) return null;
        var pd = mob.getPersistentData();
        pd.putDouble("rpgBossCX", pos.getX() + 0.5); pd.putDouble("rpgBossCY", pos.getY()); pd.putDouble("rpgBossCZ", pos.getZ() + 0.5);
        pd.putDouble("rpgBossR", radius);
        pd.putString("rpgBossPoint", pointId == null ? "" : pointId);
        return mob;
    }

    /** 보스가 죽으면 다음 등장 시각 */
    public static void died(ServerLevel l, Mob mob, Boss.Def def) {
        String id = mob.getPersistentData().getString("rpgBossPoint");
        if (id.isEmpty()) return;
        BossPoints d = get(l);
        Point p = d.find(id);
        if (p == null) return;
        p.boss = null;
        p.nextSpawn = l.getGameTime() + def.respawnSec() * 20L;
        d.setDirty();
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel l) || l.getGameTime() % 20 != 0) return;
        BossPoints d = get(l);
        if (d.points.isEmpty()) return;
        long now = l.getGameTime();
        for (Point p : d.points) {
            if (!l.isLoaded(p.pos)) continue;
            if (p.boss != null) {
                Entity ent = l.getEntity(p.boss);
                if (ent != null && ent.isAlive()) continue;
                if (ent == null && !l.isPositionEntityTicking(p.pos)) continue;   // 아직 불러오는 중
                p.boss = null;   // 사라짐 (명령어로 지움 등)
                d.setDirty();
            }
            if (now < p.nextSpawn || !Boss.DEFS.containsKey(p.key)) continue;
            if (l.getNearestPlayer(p.pos.getX() + 0.5, p.pos.getY(), p.pos.getZ() + 0.5, 64, false) == null) continue;
            Mob mob = spawnAt(l, p.key, p.pos, Boss.DEFS.get(p.key).arena(), p.id);   // 크기는 mobs.json 기준
            if (mob != null) { p.boss = mob.getUUID(); d.setDirty(); }
        }
    }
}
