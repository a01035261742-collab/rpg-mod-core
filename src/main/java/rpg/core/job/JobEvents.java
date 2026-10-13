package rpg.core.job;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/** 교관 NPC: 우클릭 → 전직 창, 무적 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class JobEvents {
    private JobEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void talk(PlayerInteractEvent.EntityInteract e) {
        int fam = Jobs.npcFamily(e.getTarget());
        if (fam == 0) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp)) return;
        var d = RpgData.get(sp);
        int state = d.job == fam ? 2 : d.job != 0 ? 3 : d.level < rpg.core.balance.Balance.jobLevel1 ? 1 : 0;
        Net.openJob(sp, fam, state);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void invulnerable(LivingAttackEvent e) {
        if (e.getEntity() instanceof Villager v && Jobs.npcFamily(v) != 0) e.setCanceled(true);
    }

    public static void spawn(ServerPlayer p, int family) {
        Villager v = new Villager(net.minecraft.world.entity.EntityType.VILLAGER, p.level());
        v.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, 0);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistenceRequired();
        v.getPersistentData().putInt(Jobs.NPC_TAG, family);
        v.setCustomName(Component.literal(Jobs.name(family) + " 교관").withStyle(ChatFormatting.GOLD));
        v.setCustomNameVisible(true);
        p.level().addFreshEntity(v);
    }
}
