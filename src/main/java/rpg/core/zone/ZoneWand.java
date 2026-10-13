package rpg.core.zone;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import rpg.core.RpgCore;

/** 사냥터 지팡이: 좌클릭 = 첫 번째 점, 우클릭 = 두 번째 점 → /rpg zone create ... */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public class ZoneWand extends Item {
    public static final Map<UUID, BlockPos[]> POS = new HashMap<>();

    public ZoneWand(Properties p) {
        super(p);
    }

    public static BlockPos[] get(ServerPlayer p) {
        return POS.computeIfAbsent(p.getUUID(), k -> new BlockPos[2]);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) {
            get(sp)[1] = ctx.getClickedPos();
            msg(sp, "두 번째 점", ctx.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    @SubscribeEvent
    public static void leftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getItemStack().getItem() instanceof ZoneWand)) return;
        e.setCanceled(true);
        if (e.getEntity() instanceof ServerPlayer sp && e.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            get(sp)[0] = e.getPos();
            msg(sp, "첫 번째 점", e.getPos());
        }
    }

    private static void msg(ServerPlayer sp, String which, BlockPos p) {
        BlockPos[] ps = get(sp);
        String size = "";
        if (ps[0] != null && ps[1] != null) {
            size = String.format("  (크기 %d×%d×%d)", Math.abs(ps[0].getX() - ps[1].getX()) + 1,
                    Math.abs(ps[0].getY() - ps[1].getY()) + 1, Math.abs(ps[0].getZ() - ps[1].getZ()) + 1);
        }
        sp.sendSystemMessage(Component.literal("[사냥터] " + which + ": " + p.getX() + ", " + p.getY() + ", " + p.getZ() + size)
                .withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("좌클릭: 첫 번째 점 · 우클릭: 두 번째 점").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("그다음 /rpg zone create <이름> <몹> <레벨> <최대 마리>").withStyle(ChatFormatting.GRAY));
    }
}
