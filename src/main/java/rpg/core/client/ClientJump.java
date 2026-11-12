package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** 도약의 물약 (클라): 내 버프 목록에 있으면 점프 +10% */
public final class ClientJump {
    private ClientJump() {}

    public static double pct(Player p) {
        if (p != Minecraft.getInstance().player) return 0;
        for (ClientData.Buff b : ClientData.BUFFS) if (b.name().equals("도약의 물약")) return 10;
        return 0;
    }
}
