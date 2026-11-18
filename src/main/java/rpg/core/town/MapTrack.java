package rpg.core.town;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.net.Net;
import rpg.core.zone.Zone;
import rpg.core.zone.ZoneData;

/** 약도 '지금 위치': 1초마다 서 있는 사냥터(몹 목록)·근처 마을을 보고 바뀌면 클라에 알림 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class MapTrack {
    private MapTrack() {}

    private static final Map<UUID, String> LAST = new HashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 11) return;
        String mobs = "", town = "";
        if (rpg.core.world.RpgWorld.in(p)) {
            for (Zone z : ZoneData.get(p.serverLevel()).zones.values())
                if (z.box().inflate(2, 6, 2).contains(p.position())) { mobs = z.mob == null ? "" : z.mob; break; }
            if (mobs.isEmpty()) {
                double best = Balance.townDiscover * Balance.townDiscover * 2.25;
                for (var en : Towns.get(p.serverLevel()).towns.entrySet()) {
                    double d = en.getValue().distSqr(p.blockPosition());
                    if (d < best) { best = d; town = en.getKey(); }
                }
            }
        }
        String key = mobs + "|" + town;
        if (key.equals(LAST.get(p.getUUID()))) return;
        LAST.put(p.getUUID(), key);
        Net.CH.send(PacketDistributor.PLAYER.with(() -> p), new Net.MapHere(mobs, town));
    }

    @SubscribeEvent
    public static void out(PlayerEvent.PlayerLoggedOutEvent e) { LAST.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent e) { LAST.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void dim(PlayerEvent.PlayerChangedDimensionEvent e) { LAST.remove(e.getEntity().getUUID()); }
}
