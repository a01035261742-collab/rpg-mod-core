package rpg.core.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/**
 * 게임 중에 static 맵에 쌓인 상태 정리.
 * - 플레이어가 나가면: 그 사람 UUID 항목 지움 (서버를 오래 켜 둬도 안 쌓이게)
 * - 서버가 꺼지면: 게임 중 상태를 전부 비움 (싱글플레이에서 월드를 바꿔도 이전 월드 것이 안 남게)
 * 데이터팩/설정에서 읽어 온 정의(Balance, Boss.DEFS, GearDefs 등)는 비우지 않는다.
 * 새 static 상태 맵을 만들면 해당 클래스에 clearState() 를 만들고 아래 onStopped 에 한 줄 추가할 것.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class StateCleanup {
    private StateCleanup() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        java.util.UUID id = p.getUUID();
        rpg.core.combat.MobAI.forget(id);
        rpg.core.net.Net.forget(id);
        rpg.core.world.RpgWorld.forget(id);
        rpg.core.zone.ZoneWand.forget(id);
        rpg.core.compat.EpicFightCompat.forget(id);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        rpg.core.combat.Boss.clearState();
        rpg.core.combat.MobAI.clearState();
        rpg.core.combat.CombatEvents.clearState();
        rpg.core.combat.Status.clearState();
        rpg.core.loot.Loot.clearState();
        rpg.core.net.Net.clearState();
        rpg.core.job.BasicAttack.clearState();
        rpg.core.zone.ZoneWand.clearState();
        rpg.core.world.RpgWorld.clearState();
        rpg.core.world.Raids.clearState();
        rpg.core.world.Markers.clearState();
        rpg.core.world.Relight.clearState();
        rpg.core.town.Travel.clearState();
        rpg.core.item.ReturnScroll.clearState();
        rpg.core.compat.EpicFightCompat.clearState();
        rpg.core.data.PlayerEvents.clearState();
        rpg.core.cmd.BuildPanel.clearState();
        rpg.core.party.Parties.clearState();
        rpg.core.shop.Trade.clearState();
        rpg.core.skill.JobSkills.clearState();
        // 시련장 Trials.RUNS 는 Trials.stopped 가 이미 비움
    }
}
