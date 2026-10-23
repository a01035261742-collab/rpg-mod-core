package rpg.core.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import rpg.core.RpgCore;
import rpg.core.data.RpgData;

/** 귀환 주문서: 3초 시전 (움직이거나 맞으면 취소) → 같은 차원의 가장 가까운 마을. 보스 경기장에서는 못 씀 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public class ReturnScroll extends Item {
    public static final int CAST_TICKS = 60;

    public ReturnScroll(Properties p) {
        super(p);
    }

    private record Cast(Vec3 from, long end, double hp, InteractionHand hand) {}
    private static final Map<UUID, Cast> CASTS = new HashMap<>();

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (rpg.core.combat.Boss.inArena(sp)) { msg(sp, "보스 경기장에서는 귀환할 수 없어요", ChatFormatting.RED); return InteractionResultHolder.fail(stack); }
        if (rpg.core.town.Towns.get(sp.serverLevel()).towns.isEmpty()) { msg(sp, "이 세계에 등록된 마을이 없어요", ChatFormatting.RED); return InteractionResultHolder.fail(stack); }
        if (CASTS.containsKey(sp.getUUID())) return InteractionResultHolder.fail(stack);
        CASTS.put(sp.getUUID(), new Cast(sp.position(), sp.level().getGameTime() + CAST_TICKS, RpgData.get(sp).hp, hand));
        msg(sp, "귀환 중… (3초, 움직이면 취소)", ChatFormatting.AQUA);
        level.playSound(null, sp.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.6f);
        return InteractionResultHolder.consume(stack);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CASTS.isEmpty()) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        CASTS.entrySet().removeIf(en -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            Cast c = en.getValue();
            if (sp == null || !sp.isAlive()) return true;
            if (sp.position().distanceToSqr(c.from()) > 0.6 * 0.6) { msg(sp, "움직여서 귀환이 취소됐어요", ChatFormatting.GRAY); return true; }
            if (RpgData.get(sp).hp < c.hp() - 0.01) { msg(sp, "공격받아서 귀환이 취소됐어요", ChatFormatting.GRAY); return true; }
            long now = sp.level().getGameTime();
            sp.serverLevel().sendParticles(ParticleTypes.PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 6, 0.4, 0.6, 0.4, 0.3);
            if (now < c.end()) return false;
            ItemStack st = sp.getItemInHand(c.hand());
            if (!(st.getItem() instanceof ReturnScroll)) {   // 손에서 바꿨으면 인벤에서 하나
                st = ItemStack.EMPTY;
                for (ItemStack s : sp.getInventory().items) if (s.getItem() instanceof ReturnScroll) { st = s; break; }
                if (st.isEmpty()) { msg(sp, "귀환 주문서가 없어요", ChatFormatting.RED); return true; }
            }
            var town = rpg.core.town.Towns.get(sp.serverLevel()).nearest(sp.blockPosition());
            if (town == null) return true;
            if (!sp.getAbilities().instabuild) st.shrink(1);
            var p = town.getValue();
            sp.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
            msg(sp, town.getKey() + "(으)로 돌아왔어요", ChatFormatting.GREEN);
            return true;
        });
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("우클릭: 3초 뒤 가장 가까운 마을로").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("움직이거나 맞으면 취소 · 보스 경기장에서는 못 씀").withStyle(ChatFormatting.DARK_GRAY));
    }
}
