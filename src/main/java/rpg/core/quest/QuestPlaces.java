package rpg.core.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** 맵 배치 기록 (오버월드 저장): 퀘스트 지점 위치 + 세운 NPC 위치 (/rpg quest check 용) */
public class QuestPlaces extends SavedData {
    public record Place(String dim, BlockPos pos) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dim);
            t.putLong("pos", pos.asLong());
            return t;
        }
        static Place load(CompoundTag t) {
            return new Place(t.getString("dim"), BlockPos.of(t.getLong("pos")));
        }
        public String text() {
            return pos.getX() + " " + pos.getY() + " " + pos.getZ() + (dim.equals("minecraft:overworld") ? "" : " (" + dim + ")");
        }
    }

    public final Map<String, Place> points = new LinkedHashMap<>();
    public final Map<String, List<Place>> npcs = new LinkedHashMap<>();
    /** 교관의 시련 시련장 (몹이 나오는 자리) */
    public final List<Place> trials = new ArrayList<>();

    public static QuestPlaces get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(QuestPlaces::load, QuestPlaces::new, "rpgcore_quest_places");
    }

    public static QuestPlaces load(CompoundTag tag) {
        QuestPlaces d = new QuestPlaces();
        CompoundTag pt = tag.getCompound("points");
        for (String k : pt.getAllKeys()) d.points.put(k, Place.load(pt.getCompound(k)));
        CompoundTag np = tag.getCompound("npcs");
        for (String k : np.getAllKeys()) {
            ListTag l = np.getList(k, Tag.TAG_COMPOUND);
            List<Place> ps = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) ps.add(Place.load(l.getCompound(i)));
            d.npcs.put(k, ps);
        }
        ListTag tr = tag.getList("trials", Tag.TAG_COMPOUND);
        for (int i = 0; i < tr.size(); i++) d.trials.add(Place.load(tr.getCompound(i)));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag pt = new CompoundTag();
        points.forEach((k, v) -> pt.put(k, v.save()));
        tag.put("points", pt);
        CompoundTag np = new CompoundTag();
        npcs.forEach((k, v) -> {
            ListTag l = new ListTag();
            for (Place p : v) l.add(p.save());
            np.put(k, l);
        });
        tag.put("npcs", np);
        ListTag tr = new ListTag();
        for (Place p : trials) tr.add(p.save());
        tag.put("trials", tr);
        return tag;
    }
}
