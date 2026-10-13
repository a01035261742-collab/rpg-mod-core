package rpg.core.craft;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 골드 수수료 레시피로 만들면 골드 차감 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class CraftEvents {
    private CraftEvents() {}

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getInventory() instanceof CraftingContainer inv)) return;
        var r = sp.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, inv, sp.level());
        if (r.isEmpty() || !(r.get() instanceof GoldShapedRecipe g) || g.gold <= 0) return;
        PlayerData d = RpgData.get(sp);
        d.gold = Math.max(0, d.gold - g.gold);
        RpgData.sync(sp);
        sp.displayClientMessage(Component.literal(e.getCrafting().getHoverName().getString() + " 제작  -"
                + String.format("%,d", g.gold) + " G").withStyle(ChatFormatting.YELLOW), true);
    }
}
