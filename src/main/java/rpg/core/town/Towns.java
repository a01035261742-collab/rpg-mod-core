package rpg.core.town;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** 마을 위치 (차원별 저장). 귀환 주문서는 같은 차원에서 가장 가까운 마을로. /rpg town add <이름> */
public class Towns extends SavedData {
    public final Map<String, BlockPos> towns = new LinkedHashMap<>();

    public static Towns get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Towns::load, Towns::new, "rpgcore_towns");
    }

    public static Towns load(CompoundTag tag) {
        Towns t = new Towns();
        CompoundTag m = tag.getCompound("towns");
        for (String k : m.getAllKeys()) {
            CompoundTag p = m.getCompound(k);
            t.towns.put(k, new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z")));
        }
        return t;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag m = new CompoundTag();
        towns.forEach((k, p) -> {
            CompoundTag c = new CompoundTag();
            c.putInt("x", p.getX()); c.putInt("y", p.getY()); c.putInt("z", p.getZ());
            m.put(k, c);
        });
        tag.put("towns", m);
        return tag;
    }

    /** 가장 가까운 마을 (없으면 null) */
    public Map.Entry<String, BlockPos> nearest(BlockPos from) {
        Map.Entry<String, BlockPos> best = null;
        double bd = Double.MAX_VALUE;
        for (var e : towns.entrySet()) {
            double d = e.getValue().distSqr(from);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }
}
