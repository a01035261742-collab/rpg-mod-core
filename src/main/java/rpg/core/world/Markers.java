package rpg.core.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** /rpg 보기: 사냥터·보스방·NPC·퀘스트 지점·마을·스폰을 OP 화면에 표시 (2초마다 다시 보냄) */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Markers {
    private Markers() {}

    /** type 0 사냥터(상자) · 1 보스방(원) · 2 NPC · 3 퀘스트 지점 · 4 전직 시련 · 5 마을 · 6 RPG 스폰 · 7 레이드 입구 · 8 레이드 방 */
    public record M(int type, int x1, int y1, int z1, int x2, int y2, int z2, float r, String label) {}

    private static final Set<UUID> ON = new HashSet<>();

    public static boolean toggle(ServerPlayer p) {
        boolean on = !ON.remove(p.getUUID());
        if (on) ON.add(p.getUUID());
        rpg.core.net.Net.markers(p, on ? collect(p) : List.of());
        return on;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ON.isEmpty() || e.getServer().getTickCount() % 40 != 0) return;
        for (UUID id : List.copyOf(ON)) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(id);
            if (p == null) { ON.remove(id); continue; }
            rpg.core.net.Net.markers(p, collect(p));
        }
    }

    static List<M> collect(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        String dim = l.dimension().location().toString();
        List<M> out = new ArrayList<>();
        for (var z : rpg.core.zone.ZoneData.get(l).zones.values())
            out.add(new M(0, z.min.getX(), z.min.getY(), z.min.getZ(), z.max2.getX() + 1, z.max2.getY() + 1, z.max2.getZ() + 1, 0,
                    z.name + " · " + z.mob + " Lv." + z.level + (z.levelMax > z.level ? "~" + z.levelMax : "")));
        for (var b : rpg.core.combat.BossPoints.get(l).points)
            out.add(at(1, b.pos, (float) b.radius, "보스: " + b.key));
        var qp = rpg.core.quest.QuestPlaces.get(p.getServer());
        qp.npcs.forEach((id, list) -> { for (var pl : list) if (pl.dim().equals(dim)) out.add(at(2, pl.pos(), 0, "NPC: " + rpg.core.quest.Quests.npcName(id))); });
        qp.points.forEach((id, pl) -> { if (pl.dim().equals(dim)) out.add(at(3, pl.pos(), 0, "퀘스트: " + rpg.core.quest.Quests.pointName(id))); });
        for (var pl : qp.trials) if (pl.dim().equals(dim)) out.add(at(4, pl.pos(), 0, "전직 시련"));
        rpg.core.town.Towns.get(l).towns.forEach((name, pos) -> out.add(at(5, pos, 0, "마을: " + name)));
        if (RpgWorld.is(l)) {
            out.add(at(6, RpgWorld.data(p.getServer()).rpgSpawn, 0, "RPG 스폰"));
            for (var d : Raids.data(p.getServer()).raids.values()) {
                if (d.gate != null) out.add(at(7, d.gate, 0, "레이드 입구: " + d.name + " (Lv." + d.minLevel + ")"));
                for (int i = 0; i < d.rooms.size(); i++) out.add(at(8, d.rooms.get(i), 0, "레이드 방: " + d.name + " " + (i + 1) + "번"));
            }
        }
        return out;
    }

    private static M at(int type, BlockPos b, float r, String label) {
        return new M(type, b.getX(), b.getY(), b.getZ(), b.getX(), b.getY(), b.getZ(), r, label);
    }
}
