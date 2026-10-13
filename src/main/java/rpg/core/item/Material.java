package rpg.core.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** 몹 재료·강화 재료 (설명만 있는 아이템) */
public class Material extends Item {
    private final String desc;
    /** 몹 재료 (상점 "재료 전부 팔기" 대상) */
    public final boolean mob;

    public Material(Properties p, String desc, boolean mob) {
        super(p.stacksTo(100));
        this.desc = desc;
        this.mob = mob;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal(desc).withStyle(ChatFormatting.GRAY));
    }
}
