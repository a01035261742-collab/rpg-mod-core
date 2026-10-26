package rpg.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import rpg.core.RpgCore;
import rpg.core.item.ModItems;

/** 차원 문 (들어가면 반대 월드) · 공유 창고 블록 */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RpgCore.MODID);

    public static final RegistryObject<Block> WORLD_PORTAL = BLOCKS.register("world_portal", () -> new PortalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1f, 3600000f).noCollission().noOcclusion().lightLevel(s -> 11).noLootTable()));
    public static final RegistryObject<Block> SHARED_STASH = BLOCKS.register("shared_stash", () -> new StashBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(-1f, 3600000f).noLootTable()));

    public static final RegistryObject<Block> RAID_GATE = BLOCKS.register("raid_gate", () -> new RaidGateBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(-1f, 3600000f).lightLevel(s -> 9).noLootTable()));
    public static final RegistryObject<Item> RAID_GATE_ITEM = ModItems.ITEMS.register("raid_gate", () -> new BlockItem(RAID_GATE.get(), new Item.Properties()));

    public static final RegistryObject<Item> WORLD_PORTAL_ITEM = ModItems.ITEMS.register("world_portal", () -> new BlockItem(WORLD_PORTAL.get(), new Item.Properties()));
    public static final RegistryObject<Item> SHARED_STASH_ITEM = ModItems.ITEMS.register("shared_stash", () -> new BlockItem(SHARED_STASH.get(), new Item.Properties()));

    public static void init() {}

    /** 차원 문: 안에 들어가면 반대 월드로 (OP 만 부술 수 있음) */
    public static class PortalBlock extends Block {
        public PortalBlock(Properties p) { super(p); }
        @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos pos, CollisionContext c) { return Shapes.box(0, 0, 0.375, 1, 1, 0.625); }
        @Override public void entityInside(BlockState s, Level l, BlockPos pos, Entity e) {
            // 여기서 바로 보내면 안 됨: 이동 처리 도중이라 이동이 끝나며 위치가 이전 월드 좌표로 덮어써짐 (서바이벌).
            // 플레이어 틱(RpgWorld.tick)에서 문 안에 있는지 보고 보냄
        }
    }

    /** 공유 창고: 우클릭하면 열림 */
    public static class StashBlock extends Block {
        public StashBlock(Properties p) { super(p); }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide && p instanceof ServerPlayer sp) SharedStash.open(sp);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }

    /** 레이드 단말기: 아이템의 보스 키로 놓으면 그 보스 레이드 입구, 우클릭하면 입장 창 */
    public static class RaidGateBlock extends Block {
        public RaidGateBlock(Properties p) { super(p); }
        @Override public void setPlacedBy(Level l, BlockPos pos, BlockState s, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
            super.setPlacedBy(l, pos, s, placer, stack);
            if (l instanceof net.minecraft.server.level.ServerLevel sl) Raids.placed(sl, pos, stack, placer);
        }
        @Override public void onRemove(BlockState s, Level l, BlockPos pos, BlockState ns, boolean moving) {
            if (!s.is(ns.getBlock()) && l instanceof net.minecraft.server.level.ServerLevel sl) Raids.removed(sl, pos);
            super.onRemove(s, l, pos, ns, moving);
        }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
            if (!l.isClientSide && p instanceof ServerPlayer sp) Raids.openGate(sp, pos);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
}
