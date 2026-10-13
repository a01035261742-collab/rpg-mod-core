package rpg.core.zone;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** 차원(월드)별 사냥터 목록 저장 */
public class ZoneData extends SavedData {
    public final Map<String, Zone> zones = new LinkedHashMap<>();

    public static ZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ZoneData::load, ZoneData::new, "rpgcore_zones");
    }

    public static ZoneData load(CompoundTag tag) {
        ZoneData d = new ZoneData();
        ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Zone z = Zone.load(list.getCompound(i));
            d.zones.put(z.name, z);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone z : zones.values()) list.add(z.save());
        tag.put("zones", list);
        return tag;
    }
}
