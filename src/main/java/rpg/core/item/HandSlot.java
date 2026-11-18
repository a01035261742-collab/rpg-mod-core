package rpg.core.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** RPG 대륙: 손(핫바 0번 = 무기 칸)에는 무기만. 맨손일 때 주운 물건이 손으로 들어오면 가방 빈칸으로 옮김 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class HandSlot {
    private HandSlot() {}

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        if (!rpg.core.world.RpgWorld.in(p)) return;
        var inv = p.getInventory();
        ItemStack hand = inv.items.get(0);
        if (hand.isEmpty() || !p.containerMenu.getCarried().isEmpty()) return;   // 창에서 옮기는 중이면 기다림
        var d = rpg.core.equip.Gear.def(hand);
        if (d != null && "weapon".equals(d.slot())) return;
        if (hand.getItem() instanceof net.minecraft.world.item.BowItem || hand.getItem() instanceof net.minecraft.world.item.SwordItem) return;
        // 같은 물건 합치기 → 빈칸 (가방 9~35 먼저, 그다음 1~8)
        for (int i = 1; i < inv.items.size() && !hand.isEmpty(); i++) {
            ItemStack s = inv.items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, hand) && s.getCount() < s.getMaxStackSize()) {
                int n = Math.min(hand.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(n); hand.shrink(n);
            }
        }
        if (!hand.isEmpty()) {
            int free = -1;
            for (int i = 9; i < inv.items.size() && free < 0; i++) if (inv.items.get(i).isEmpty()) free = i;
            for (int i = 1; i < 9 && free < 0; i++) if (inv.items.get(i).isEmpty()) free = i;
            if (free < 0) return;   // 가방이 꽉 참: 그대로 둠
            inv.items.set(free, hand.copy());
        }
        inv.items.set(0, ItemStack.EMPTY);
        inv.setChanged();
    }
}
