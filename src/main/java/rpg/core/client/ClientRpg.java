package rpg.core.client;

import net.minecraft.client.Minecraft;

/** 클라: 지금 RPG 대륙에 있나 */
public final class ClientRpg {
    private ClientRpg() {}

    public static boolean inRpg() {
        var mc = Minecraft.getInstance();
        return mc.level != null && rpg.core.world.RpgWorld.is(mc.level);
    }
}
