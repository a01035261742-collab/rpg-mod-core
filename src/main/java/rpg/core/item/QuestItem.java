package rpg.core.item;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** 퀘스트 물건 (배달·가져오기). 퀘스트를 포기하면 회수됨 */
public class QuestItem extends Item {
    public QuestItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("퀘스트 물건").withStyle(ChatFormatting.YELLOW));
        tip.add(Component.literal("퀘스트를 포기하면 사라져요").withStyle(ChatFormatting.DARK_GRAY));
    }
}
