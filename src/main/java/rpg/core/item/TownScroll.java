package rpg.core.item;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** 마을 이동 주문서: 우클릭 → 가 본 마을 고르기 → 3초 시전 (움직이거나 맞으면 취소) → 그 마을. rpg.core.town.Travel */
public class TownScroll extends Item {
    public TownScroll(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) rpg.core.town.Travel.openScroll(sp);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("우클릭: 가 본 마을 중 하나로 (3초 시전)").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("움직이거나 맞으면 취소 · 레이드·보스 경기장·시련 중에는 못 씀").withStyle(ChatFormatting.DARK_GRAY));
    }
}
