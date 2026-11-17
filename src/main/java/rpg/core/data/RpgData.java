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

    /**
     * 클라이언트로 플레이어 정보 보내기 예약. 바로 보내지 않고 "보낼 게 있다"고 표시만 하고,
     * 같은 틱에 여러 번 불러도 서버 틱 끝에 한 번만 보냄 (PlayerEvents.flushSync).
     */
    public static void sync(ServerPlayer p) {
        get(p).syncDirty = true;
    }

    /** 바로 보내야 할 때 (접속·리스폰·차원 이동·데이터팩 새로고침처럼 클라가 곧바로 최신 정보가 필요한 곳) */
    public static void syncNow(ServerPlayer p) {
        PlayerData d = get(p);
        d.syncDirty = false;
        Net.syncTo(p, d);
    }
}
