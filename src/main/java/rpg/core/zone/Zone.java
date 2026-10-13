package rpg.core.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

/** 사냥터 하나: 상자 구역 + 나올 몹·레벨·최대 마리 수·리젠 간격(초) */
public class Zone {
    public String name;
    /** 나올 몹 (여러 종이면 + 로 이음: pig+cow) */
    public String mob;
    public int level, levelMax;
    public int max;
    public int interval;
    public BlockPos min, max2;
    public transient int timer;

    public Zone(String name, String mob, int level, int max, int interval, BlockPos a, BlockPos b) {
        this.name = name; this.mob = mob; this.level = level; this.levelMax = level; this.max = max; this.interval = interval;
        this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.max2 = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    public AABB box() {
        return new AABB(min.getX(), min.getY(), min.getZ(), max2.getX() + 1, max2.getY() + 1, max2.getZ() + 1);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name); t.putString("mob", mob); t.putInt("level", level); t.putInt("max", max);
        t.putInt("interval", interval); t.putInt("levelMax", levelMax);
        t.putLong("min", min.asLong()); t.putLong("max2", max2.asLong());
        return t;
    }

    public static Zone load(CompoundTag t) {
        Zone z = new Zone(t.getString("name"), t.getString("mob"), t.getInt("level"), t.getInt("max"), t.getInt("interval"),
                BlockPos.of(t.getLong("min")), BlockPos.of(t.getLong("max2")));
        z.levelMax = Math.max(z.level, t.getInt("levelMax"));
        return z;
    }

    public String[] mobs() { return mob.split("\\+"); }
}
