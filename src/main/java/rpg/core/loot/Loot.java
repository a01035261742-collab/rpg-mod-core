package rpg.core.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/** 드롭 주인: 정해진 시간 동안 주인만 주움 (모두에게 보임). 이후 누구나. 골드 주머니는 주우면 바로 골드 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Loot {
    private Loot() {}

    private static final String OWNER = "rpgOwner", UNTIL = "rpgOwnerUntil";
    private record Claimed(ItemEntity e, long until) {}
    private static final List<Claimed> CLAIMED = new ArrayList<>();

    public static void claim(ItemEntity ie, ServerPlayer owner, double seconds) {
        if (seconds <= 0) return;
        long until = owner.level().getGameTime() + Math.round(seconds * 20);
        ie.getPersistentData().putUUID(OWNER, owner.getUUID());
        ie.getPersistentData().putLong(UNTIL, until);
        ie.setCustomName(Drops.ownerTag(owner));
        ie.setCustomNameVisible(true);
        CLAIMED.add(new Claimed(ie, until));
    }

    private static boolean locked(ItemEntity ie, UUID who) {
        var t = ie.getPersistentData();
        if (!t.hasUUID(OWNER)) return false;
        if (ie.level().getGameTime() >= t.getLong(UNTIL)) return false;
        return !t.getUUID(OWNER).equals(who);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void pickup(EntityItemPickupEvent e) {
        ItemEntity ie = e.getItem();
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (locked(ie, sp.getUUID())) {
            e.setCanceled(true);
            return;
        }
        if (ie.getItem().getItem() instanceof GoldPouch) {
            e.setCanceled(true);
            if (ie.hasPickUpDelay() || !ie.isAlive()) return;
            long gold = GoldPouch.amount(ie.getItem()) * ie.getItem().getCount();
            PlayerData d = RpgData.get(sp);
            d.gold += gold;
            RpgData.sync(sp);
            Net.reward(sp, 0, gold);
            sp.take(ie, 1);
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
            ie.discard();
        }
    }

    /** 주운 아이템 알림 (오른쪽 아래) */
    @SubscribeEvent
    public static void picked(net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        var s = e.getStack();
        if (s.isEmpty() || s.getItem() instanceof GoldPouch) return;
        Net.itemNote(sp, s.getHoverName().getString() + (s.getCount() > 1 ? " ×" + s.getCount() : "") + " 획득");
    }

    /** 주인 시간 끝나면 이름표 떼기 */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CLAIMED.isEmpty()) return;
        CLAIMED.removeIf(c -> {
            if (!c.e().isAlive()) return true;
            if (c.e().level().getGameTime() < c.until()) return false;
            c.e().setCustomName(null);
            c.e().setCustomNameVisible(false);
            return true;
        });
    }


}
