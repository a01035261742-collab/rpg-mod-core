package rpg.core.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 테스트 무기 (장비 시스템 전 임시). atk = 무기 공격력 */
public class RpgWeapon extends Item {
    public final int atk;
    private final Multimap<Attribute, AttributeModifier> mods;

    public RpgWeapon(Properties p, int atk, double attackSpeed) {
        super(p);
        this.atk = atk;
        this.mods = ImmutableMultimap.of(Attributes.ATTACK_SPEED,
                new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", attackSpeed - 4.0, AttributeModifier.Operation.ADDITION));
    }

    @Override
    @SuppressWarnings("deprecation")
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? mods : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("무기 공격력 " + atk).withStyle(ChatFormatting.GOLD));
        tip.add(Component.literal("테스트용 (장비 시스템 전 임시)").withStyle(ChatFormatting.DARK_GRAY));
    }
}
