package rpg.core.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
            return;
        }
        // RPG 대륙: 손(1번 칸)이 비어 있으면 주운 게 손으로 안 들어가게 (먹어지거나 바닥이 클릭되던 것)
        var inv = sp.getInventory();
        if (rpg.core.world.RpgWorld.in(sp) && !sp.isCreative() && inv.items.get(inv.selected).isEmpty()) {
            e.setCanceled(true);
            if (ie.hasPickUpDelay() || !ie.isAlive()) return;
            net.minecraft.world.item.ItemStack st = ie.getItem();
            int before = st.getCount();
            for (int pass = 0; pass < 2 && !st.isEmpty(); pass++)
                for (int i = 0; i < inv.items.size() && !st.isEmpty(); i++) {
                    if (i == inv.selected) continue;
                    var cur = inv.items.get(i);
                    if (pass == 0 && !cur.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameTags(cur, st) && cur.getCount() < cur.getMaxStackSize()) {
                        int n = Math.min(st.getCount(), cur.getMaxStackSize() - cur.getCount());
                        cur.grow(n); st.shrink(n);
                    } else if (pass == 1 && cur.isEmpty()) {
                        inv.items.set(i, st.copy()); st.setCount(0);
                    }
                }
            int got = before - st.getCount();
            if (got <= 0) return;
            var shown = ie.getItem().copyWithCount(got);
            sp.take(ie, got);
            if (st.isEmpty()) ie.discard(); else ie.setItem(st);
            sp.inventoryMenu.broadcastChanges();
            sp.level().playSound(null, sp.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, (sp.getRandom().nextFloat() - sp.getRandom().nextFloat()) * 1.4f + 2f);
            Net.itemNote(sp, shown.getHoverName().getString() + (got > 1 ? " ×" + got : "") + " 획득");
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

    /** 서버가 꺼질 때 호출: 게임 중에 쌓인 상태만 비움 (데이터팩에서 읽은 정의는 그대로) */
    public static void clearState() { CLAIMED.clear(); }
}
