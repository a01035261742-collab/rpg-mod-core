package rpg.core.item;

import java.util.List;
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
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 물약: 우클릭 즉시 회복 (빨간 = HP, 파란 = MP). 회복량·쿨타임은 player.json */
public class Potion extends Item {
    private final boolean hp;

    public Potion(Properties p, boolean hp) {
        super(p.stacksTo(100));
        this.hp = hp;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        PlayerData d = RpgData.get(sp);
        if (d.isDown()) return InteractionResultHolder.fail(stack);
        if (hp ? d.hp >= d.maxHp : d.mp >= d.maxMp) {
            sp.displayClientMessage(Component.literal(hp ? "HP가 가득 차 있어요" : "MP가 가득 차 있어요").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (hp) d.hp = Math.min(d.maxHp, d.hp + Balance.redPotionHp);
        else d.mp = Math.min(d.maxMp, d.mp + Balance.bluePotionMp);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        sp.getCooldowns().addCooldown(this, (int) Math.round(Balance.potionCooldown * 20));
        level.playSound(null, sp.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6f, 1.2f);
        RpgData.sync(sp);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(hp ? "우클릭: HP " + (long) Balance.redPotionHp + " 회복" : "우클릭: MP " + (long) Balance.bluePotionMp + " 회복")
                .withStyle(ChatFormatting.GRAY));
    }
}
