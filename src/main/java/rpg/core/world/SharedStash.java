package rpg.core.world;

import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 공유 창고: 사람마다 27칸, 야생·RPG 어디서든 같은 창고 (플레이어 저장 데이터에 보관).
 * 넣을 수 있는 건 RPG 제작에 쓰는 바닐라 재료만 (철·구리 주괴, 유리병, 가죽, 파란 염료). 이미 들어 있는 다른 물건은 꺼내기만 됨
 */
public final class SharedStash {
    private SharedStash() {}

    private static final String KEY = "rpgSharedStash";
    public static final Set<Item> ALLOWED = Set.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.GLASS_BOTTLE, Items.LEATHER, Items.BLUE_DYE);

    public static boolean allowed(ItemStack s) { return ALLOWED.contains(s.getItem()); }

    /** 공유 창고 상자 (칸 제한용 표시) */
    static final class Box extends SimpleContainer {
        final ServerPlayer owner;
        Box(ServerPlayer owner) { super(27); this.owner = owner; }
        @Override public boolean canPlaceItem(int slot, ItemStack s) { return allowed(s); }
        @Override public void stopOpen(Player who) { super.stopOpen(who); save(owner, this); }
    }

    /** 명령어: 공유 창고 블록 6칸 안에서만 */
    public static void command(ServerPlayer p) {
        BlockPos c = p.blockPosition();
        var b = ModBlocks.SHARED_STASH.get();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-6, -3, -6), c.offset(6, 3, 6)))
            if (p.level().getBlockState(q).is(b)) { open(p); return; }
        p.sendSystemMessage(Component.literal("공유 창고 블록 가까이(6칸)에서만 열 수 있어요").withStyle(ChatFormatting.RED));
    }

    public static void open(ServerPlayer p) {
        // 상자는 창을 연 뒤에 읽음 (이전 창이 닫히며 저장된 내용을 읽어야 복사가 안 생김)
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> {
            Box box = new Box(p);
            load(p, box);
            return new ChestMenu(MenuType.GENERIC_9x3, id, inv, box, 3) {
                @Override protected Slot addSlot(Slot s) {
                    if (s.container instanceof Box) s = new Slot(s.container, s.getContainerSlot(), s.x, s.y) {
                        @Override public boolean mayPlace(ItemStack st) { return allowed(st); }
                    };
                    return super.addSlot(s);
                }
            };
        }, Component.literal("공유 창고 (제작 재료만: 철·구리·유리병·가죽·파란 염료)")));
    }

    private static CompoundTag keep(ServerPlayer p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void load(ServerPlayer p, SimpleContainer box) {
        ListTag l = keep(p).getList(KEY, 10);
        box.fromTag(l);
    }

    private static void save(ServerPlayer p, SimpleContainer box) {
        CompoundTag k = keep(p);
        k.put(KEY, box.createTag());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
    }
}
