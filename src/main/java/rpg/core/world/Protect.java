package rpg.core.world;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** RPG 대륙 블록 보호: 크리에이티브만 부수기·설치. 폭발·몹·양동이도 막음 (문·상자 사용은 됨) */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Protect {
    private Protect() {}

    private static boolean blocked(Player p) {
        return p != null && RpgWorld.in(p) && !p.isCreative();
    }

    @SubscribeEvent
    public static void brk(BlockEvent.BreakEvent e) { if (blocked(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent
    public static void speed(PlayerEvent.BreakSpeed e) { if (blocked(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public static void place(BlockEvent.EntityPlaceEvent e) {
        // 레이드 단말기·이동의 돌: OP 는 서바이벌이어도 설치 가능
        if (e.getEntity() instanceof Player op && op.hasPermissions(2) && (e.getPlacedBlock().is(ModBlocks.RAID_GATE.get()) || e.getPlacedBlock().is(ModBlocks.WAYSTONE.get()))) return;
        if (e.getEntity() instanceof Player p ? blocked(p) : e.getLevel() instanceof net.minecraft.world.level.Level l && RpgWorld.is(l))
            e.setCanceled(true);
    }

    @SubscribeEvent
    public static void bucket(FillBucketEvent e) { if (blocked(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public static void trample(BlockEvent.FarmlandTrampleEvent e) {
        if (e.getLevel() instanceof net.minecraft.world.level.Level l && RpgWorld.is(l)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void boom(ExplosionEvent.Detonate e) {
        if (RpgWorld.is(e.getLevel())) {
            e.getAffectedBlocks().clear();   // 사람·몹 피해는 그대로, 블록만 안 부서짐
            e.getAffectedEntities().removeIf(en -> en instanceof net.minecraft.world.entity.decoration.HangingEntity);   // 액자·그림도
        }
    }

    // ───────── 장식 못 건드리게: 독서대·다락문·조각된 책장·액자 (문·상자는 됨) ─────────
    private static boolean deco(net.minecraft.world.level.block.state.BlockState b) {
        var k = b.getBlock();
        return k instanceof net.minecraft.world.level.block.LecternBlock || k instanceof net.minecraft.world.level.block.TrapDoorBlock
                || k instanceof net.minecraft.world.level.block.ChiseledBookShelfBlock;
    }

    private static boolean frame(net.minecraft.world.entity.Entity en) {
        return en instanceof net.minecraft.world.entity.decoration.HangingEntity;   // 아이템 액자(발광 포함)·그림
    }

    @SubscribeEvent
    public static void useDeco(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (blocked(e.getEntity()) && !e.getEntity().hasPermissions(2) && deco(e.getLevel().getBlockState(e.getPos()))) {
            e.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
            e.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void useFrame(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        if (blocked(e.getEntity()) && !e.getEntity().hasPermissions(2) && frame(e.getTarget())) e.setCanceled(true);   // 액자 안 물건 돌리기·넣기
    }

    @SubscribeEvent
    public static void useFrameAt(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific e) {
        if (blocked(e.getEntity()) && !e.getEntity().hasPermissions(2) && frame(e.getTarget())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void hitFrame(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
        if (blocked(e.getEntity()) && frame(e.getTarget())) e.setCanceled(true);   // 때려서 물건 빼기·부수기
    }

    @SubscribeEvent
    public static void shootFrame(net.minecraftforge.event.entity.ProjectileImpactEvent e) {
        if (RpgWorld.in(e.getProjectile()) && e.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult h && frame(h.getEntity()))
            e.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);   // 화살은 통과
    }

    @SubscribeEvent
    public static void mobGrief(LivingDestroyBlockEvent e) {
        if (RpgWorld.in(e.getEntity())) e.setCanceled(true);
    }
}
