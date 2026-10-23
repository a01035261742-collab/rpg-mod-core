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
    /** 바닐라 인벤토리 2×2 제작 */
    public final net.minecraft.world.inventory.CraftingContainer craft = new net.minecraft.world.inventory.TransientCraftingContainer(this, 2, 2);
    public final net.minecraft.world.inventory.ResultContainer result = new net.minecraft.world.inventory.ResultContainer();
    public static final int INV_END = 52, RESULT = 52;

    public EquipMenu(int id, Inventory inv) {
        super(TYPE.get(), id);
        this.player = inv.player;
        PlayerEquip eq = PlayerEquip.get(player);
        // 장비 11칸: 2열 (무기·장갑·망토·벨트·귀걸이·펜던트 / 반지 4·훈장)
        // 0(숨김) · 장갑 망토 벨트 귀걸이 / 반지 4 / 펜던트 / 훈장 — 2열 × 5줄
        int[][] pos = {{0, 0}, {0, 0}, {0, 1}, {0, 2}, {0, 3}, {1, 0}, {1, 1}, {1, 2}, {1, 3}, {0, 4}, {1, 4}};
        for (int i = 0; i < PlayerEquip.SLOT_TYPES.length; i++) {
            final int slot = i;
            int sx = i == 0 ? -10000 : EQ_X + pos[i][0] * 20;   // 0번(예전 무기 칸)은 숨김
            addSlot(new SlotItemHandler(eq.items, i, sx, EQ_Y + pos[i][1] * 20) {
                @Override
                public boolean mayPlace(ItemStack s) {
                    return super.mayPlace(s) && Gear.canEquip(player, s);
                }
            });
        }
        // 갑옷 4칸 (바닐라 위치)
        for (int i = 0; i < 4; i++) {
            final EquipmentSlot es = ARMOR[i];
            final ResourceLocation icon = ARMOR_ICONS[i];
            addSlot(new Slot(inv, 39 - i, 8, 8 + i * 18) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPlace(ItemStack s) {
                    return s.canEquip(es, player) && (Gear.def(s) == null || Gear.canEquip(player, s));
                }
                @Override public @Nullable Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
                }
            });
        }
        // 왼손
        addSlot(new Slot(inv, 40, 77, 62) {
            @Override public @Nullable Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });
        // 가방 27 + 핫바 9
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 142));
        // 2×2 제작 (바닐라 위치)
        addSlot(new net.minecraft.world.inventory.ResultSlot(player, craft, result, 0, 154, 28));
        for (int r = 0; r < 2; r++) for (int c = 0; c < 2; c++) addSlot(new Slot(craft, c + r * 2, 98 + c * 18, 18 + r * 18));
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
            for (int i = 0; i < armorEnd && !moved; i++) {
                Slot t = slots.get(i);
                if (!t.hasItem() && t.mayPlace(stack)) moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (!moved) {
                int hotbarStart = invEnd - 9;
                if (index < hotbarStart) { if (!moveItemStackTo(stack, hotbarStart, invEnd, false)) return ItemStack.EMPTY; }
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
