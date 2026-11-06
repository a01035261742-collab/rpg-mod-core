package rpg.core.equip;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;
import rpg.core.RpgCore;

/**
 * 장비 창 = 바닐라 인벤토리(갑옷 4 + 왼손 + 36칸) + 오른쪽에 장비 11칸
 * 슬롯 순서: 0~10 장비 · 11~14 갑옷 · 15 왼손 · 16~42 가방 · 43~51 핫바 · 52 제작 결과 · 53~56 2×2 제작칸
 */
public class EquipMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RpgCore.MODID);
    public static final RegistryObject<MenuType<EquipMenu>> TYPE = MENUS.register("equip",
            () -> IForgeMenuType.create((id, inv, buf) -> new EquipMenu(id, inv)));

    // 화면 좌표 (EquipScreen 과 맞춤)
    public static final int EQ_X = 182, EQ_Y = 8;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] ARMOR_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    public final Player player;
    /** 가방 팝업 위치 · 화면이 열렸는지 (클라만) */
    public static final int BAG_X = 84, BAG_Y = 52;
    public static boolean bagOpen;

    private static class BagSlot extends Slot {
        BagSlot(net.minecraft.world.Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean isActive() { return bagOpen; }
    }
    /** 바닐라 인벤토리 2×2 제작 */
    public final net.minecraft.world.inventory.CraftingContainer craft = new net.minecraft.world.inventory.TransientCraftingContainer(this, 2, 2);
    public final net.minecraft.world.inventory.ResultContainer result = new net.minecraft.world.inventory.ResultContainer();
    public static final int INV_END = 52, RESULT = 52;

    public EquipMenu(int id, Inventory inv) {
        super(TYPE.get(), id);
        this.player = inv.player;
        PlayerEquip eq = PlayerEquip.get(player);
        // 장비 칸 (시안 ui_d2 장비 탭): 0(숨김) · 장갑 망토 벨트 / 귀걸이 / 반지 4 / 펜던트 / 훈장
        int[][] pos = {{-10000, 0}, {147, 31}, {147, 53}, {147, 75}, {77, 146}, {77, 124}, {99, 124}, {121, 124}, {143, 124}, {99, 146}, {121, 146}};
        for (int i = 0; i < PlayerEquip.SLOT_TYPES.length; i++) {
            final int slot = i;
            addSlot(new SlotItemHandler(eq.items, i, pos[i][0], pos[i][1]) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return super.mayPlace(s) && Gear.canEquip(player, s);
                }
                @Override public boolean isActive() { return !bagOpen; }
            });
        }
        // 갑옷 4칸 (바닐라 위치)
        for (int i = 0; i < 4; i++) {
            final EquipmentSlot es = ARMOR[i];
            final ResourceLocation icon = ARMOR_ICONS[i];
            addSlot(new Slot(inv, 39 - i, 77, 31 + i * 22) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean isActive() { return !bagOpen; }
                @Override public boolean mayPlace(ItemStack s) {
                    return s.canEquip(es, player) && (Gear.def(s) == null || Gear.canEquip(player, s));
                }
                @Override public @Nullable Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
                }
            });
        }
        // 왼손
        addSlot(new Slot(inv, 40, -10000, 0) {
            @Override public boolean isActive() { return false; }
            @Override public @Nullable Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });
        // 가방 27 + 핫바 9
        // 가방 (화면에서 [가방] 눌렀을 때만 보이고 눌림)
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new BagSlot(inv, 9 + r * 9 + c, BAG_X + c * 18, BAG_Y + r * 18));
        // 핫바: 0번 = 무기 칸 (장비 탭 오른쪽 열 맨 아래), 1~8 = 가방 마지막 줄
        addSlot(new Slot(inv, 0, 147, 97) {
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean mayPlace(ItemStack s) { return "weapon".equals(Gear.slotOf(s)) && Gear.canEquip(player, s); }
            @Override public boolean isActive() { return !bagOpen; }
        });
        for (int c = 1; c < 9; c++) addSlot(new BagSlot(inv, c, BAG_X + (c - 1) * 18, BAG_Y + 54));
        // 2×2 제작 (바닐라 위치)
        addSlot(new net.minecraft.world.inventory.ResultSlot(player, craft, result, 0, -10000, 0) { @Override public boolean isActive() { return false; } });
        for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) addSlot(new Slot(craft, c + r * 2, -10000, 0) { @Override public boolean isActive() { return false; } });
    }

    /** 제작칸이 바뀌면 결과 다시 계산 (서버) */
    @Override
    public void slotsChanged(net.minecraft.world.Container c) {
        if (c != craft || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        ItemStack out = ItemStack.EMPTY;
        var opt = sp.server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, craft, sp.level());
        if (opt.isPresent()) {
            var r = opt.get();
            if (result.setRecipeUsed(sp.level(), sp, r)) out = r.assemble(craft, sp.level().registryAccess());
        }
        result.setItem(0, out);
        setRemoteSlot(RESULT, out);
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, out));
    }

    /** 창 닫으면 제작칸 물건 돌려주기 */
    @Override
    public void removed(Player p) {
        super.removed(p);
        result.clearContent();
        if (!p.level().isClientSide) clearContainer(p, craft);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack s, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(s, slot);
    }

    @Override
    public boolean stillValid(Player p) {
        return true;
    }

    /** Shift+클릭: 가방 → 맞는 장비/갑옷 칸, 장비 → 가방 */
    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int eqEnd = PlayerEquip.SLOT_TYPES.length;   // 11
        int armorEnd = eqEnd + 4;                    // 15
        int invStart = armorEnd + 1;                 // 16
        int invEnd = INV_END;
        if (index == RESULT) {   // 제작 결과 Shift+클릭: 가방으로 (여러 번 만들기)
            stack.getItem().onCraftedBy(stack, p.level(), p);
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, copy);
        } else if (index > RESULT) {   // 제작칸 → 가방
            if (!moveItemStackTo(stack, invStart, invEnd, false)) return ItemStack.EMPTY;
        } else if (index < invStart) {
            if (!moveItemStackTo(stack, invStart, invEnd, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            int wp = invEnd - 9;   // 무기 칸 (핫바 0번)
            if (index != wp && !slots.get(wp).hasItem() && slots.get(wp).mayPlace(stack)) moved = moveItemStackTo(stack, wp, wp + 1, false);
            for (int i = 0; i < armorEnd && !moved; i++) {
                Slot t = slots.get(i);
                if (!t.hasItem() && t.mayPlace(stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) {
                int hotbarStart = invEnd - 9;
                if (index == hotbarStart) { if (!moveItemStackTo(stack, invStart, hotbarStart, false) && !moveItemStackTo(stack, hotbarStart + 1, invEnd, false)) return ItemStack.EMPTY; }
                else if (index < hotbarStart) { if (!moveItemStackTo(stack, hotbarStart + 1, invEnd, false)) return ItemStack.EMPTY; }
                else if (!moveItemStackTo(stack, invStart, hotbarStart, false)) return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack);
        return copy;
    }
}
