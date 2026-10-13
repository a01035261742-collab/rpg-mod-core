package rpg.core.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

import java.util.List;

/** 스탯 초기화 주문서: 우클릭하면 찍은 스탯을 전부 포인트로 돌려받음 */
public class StatResetScroll extends Item {
    public StatResetScroll(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        PlayerData d = RpgData.get(sp);
        int refund = d.resetStats();
        if (refund <= 0) {
            sp.sendSystemMessage(Component.literal("돌려받을 스탯이 없어요").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(stack);
        }
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        RpgData.sync(sp);
        level.playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.2f);
        sp.sendSystemMessage(Component.literal("스탯을 초기화했어요. 포인트 " + refund + " 돌려받음 (K키로 다시 분배)")
                .withStyle(ChatFormatting.AQUA));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("우클릭: 찍은 스탯을 전부 포인트로 돌려받음").withStyle(ChatFormatting.GRAY));
    }
}
