package rpg.core.world;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** 빛 다시 계산: 주변 청크 빛(하늘·블록)을 0으로 비우고 빛 근원부터 다시 퍼뜨림 → 잠시 뒤 화면에도 다시 보냄 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Relight {
    private Relight() {}

    private record Job(ServerLevel level, List<ChunkPos> chunks, long sendAt) {}
    private static final List<Job> JOBS = new ArrayList<>();

    public static int run(ServerLevel l, ChunkPos c, int r) {
        var eng = l.getChunkSource().getLightEngine();
        int min = eng.getMinLightSection(), max = eng.getMaxLightSection();
        List<ChunkPos> list = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                var ch = l.getChunk(c.x + dx, c.z + dz);
                ch.getSkyLightSources().fillFrom(ch);
                for (int sy = min; sy < max; sy++) {
                    SectionPos sp = SectionPos.of(c.x + dx, sy, c.z + dz);
                    eng.queueSectionData(LightLayer.SKY, sp, new DataLayer());
                    eng.queueSectionData(LightLayer.BLOCK, sp, new DataLayer());
                }
                list.add(ch.getPos());
            }
        for (ChunkPos p : list) eng.propagateLightSources(p);
        JOBS.add(new Job(l, list, l.getGameTime() + 60));
        return list.size();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || JOBS.isEmpty()) return;
        for (var it = JOBS.iterator(); it.hasNext(); ) {
            Job j = it.next();
            if (j.level.getGameTime() < j.sendAt) continue;
            it.remove();
            var eng = j.level.getChunkSource().getLightEngine();
            BitSet all = new BitSet(); all.set(0, eng.getLightSectionCount());
            int view = j.level.getServer().getPlayerList().getViewDistance() + 1;
            for (ChunkPos p : j.chunks) {
                ClientboundLightUpdatePacket pkt = null;
                for (ServerPlayer pl : j.level.players()) {
                    ChunkPos pc = pl.chunkPosition();
                    if (Math.abs(pc.x - p.x) > view || Math.abs(pc.z - p.z) > view) continue;
                    if (pkt == null) pkt = new ClientboundLightUpdatePacket(p, eng, (BitSet) all.clone(), (BitSet) all.clone());
                    pl.connection.send(pkt);
                }
            }
        }
    }
}
