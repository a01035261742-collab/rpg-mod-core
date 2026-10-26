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
        // 레이드 단말기: OP 는 서바이벌이어도 설치 가능
        if (e.getEntity() instanceof Player op && op.hasPermissions(2) && e.getPlacedBlock().is(ModBlocks.RAID_GATE.get())) return;
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
        if (RpgWorld.is(e.getLevel())) e.getAffectedBlocks().clear();   // 사람·몹 피해는 그대로, 블록만 안 부서짐
    }

    @SubscribeEvent
    public static void mobGrief(LivingDestroyBlockEvent e) {
        if (RpgWorld.in(e.getEntity())) e.setCanceled(true);
    }
}
