package rpg.core.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** RPG 몹끼리 안 밀치기: 같은 팀(충돌 규칙 = 다른 팀만 밀기)에 넣음. 플레이어와는 그대로 밀고 밀림 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class MobTeam {
    private MobTeam() {}

    private static final String NAME = "rpg_mobs";

    private static PlayerTeam team(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        PlayerTeam t = sb.getPlayerTeam(NAME);
        if (t == null) t = sb.addPlayerTeam(NAME);
        if (t.getCollisionRule() != Team.CollisionRule.PUSH_OTHER_TEAMS) t.setCollisionRule(Team.CollisionRule.PUSH_OTHER_TEAMS);
        return t;
    }

    public static void add(Mob m) {
        if (m.level().isClientSide || m.getServer() == null) return;
        PlayerTeam t = team(m.getServer());
        if (m.getTeam() != t) m.getServer().getScoreboard().addPlayerToTeam(m.getStringUUID(), t);
    }

    private static void remove(Entity e) {
        if (e.level().isClientSide || e.getServer() == null) return;
        var sb = e.getServer().getScoreboard();
        PlayerTeam t = sb.getPlayerTeam(NAME);
        if (t != null && t.getPlayers().contains(e.getStringUUID())) sb.removePlayerFromTeam(e.getStringUUID(), t);
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof Mob m && !e.getLevel().isClientSide && Combat.isRpgMob(m)) add(m);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof Mob m && Combat.isRpgMob(m)) remove(m);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent e) {
        if (e.getEntity() instanceof Mob m && m.isRemoved() && m.getRemovalReason() != null && m.getRemovalReason().shouldDestroy()
                && Combat.isRpgMob(m)) remove(m);
    }
}
