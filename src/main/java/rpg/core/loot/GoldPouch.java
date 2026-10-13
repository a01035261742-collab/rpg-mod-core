package rpg.core.loot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.item.ModItems;
import rpg.core.net.Net;

/** 골드 주머니: 바닥에서 주우면 바로 골드. 인벤에 있으면 우클릭으로 사용 */
public class GoldPouch extends Item {
    public GoldPouch(Properties p) { super(p.stacksTo(1)); }

    public static ItemStack of(long gold) {
        ItemStack s = new ItemStack(ModItems.GOLD_POUCH.get());
        s.getOrCreateTag().putLong("rpgGold", gold);
        return s;
    }

    public static long amount(ItemStack s) {
        return s.hasTag() ? s.getTag().getLong("rpgGold") : 0;
    }

    @Override
    public Component getName(ItemStack s) {
        return Component.literal("골드 주머니 (" + String.format("%,d", amount(s)) + " G)").withStyle(ChatFormatting.GOLD);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            long g = amount(s) * s.getCount();
            PlayerData d = RpgData.get(sp);
            d.gold += g;
            RpgData.sync(sp);
            Net.reward(sp, 0, g);
            s.setCount(0);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
