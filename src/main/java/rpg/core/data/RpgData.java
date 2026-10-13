package rpg.core.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import rpg.core.net.Net;

/** 플레이어 데이터 꺼내기 + 동기화 */
public final class RpgData {
    private RpgData() {}

    /** 서버 쪽 플레이어 데이터 (없으면 빈 데이터) */
    public static PlayerData get(Player p) {
        return p.getCapability(PlayerDataProvider.CAP).orElseGet(PlayerData::new);
    }

    public static void sync(ServerPlayer p) {
        Net.syncTo(p, get(p));
    }
}
