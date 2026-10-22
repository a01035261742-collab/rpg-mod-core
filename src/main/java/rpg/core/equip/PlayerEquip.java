package rpg.core.equip;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 장비 칸 (모자·상의·하의·신발은 바닐라 갑옷 칸을 그대로 씀)
 * 0 (예전 무기 칸, 안 씀 — 무기는 손에 듦) · 1 장갑 · 2 망토 · 3 벨트 · 4 귀걸이 · 5~8 반지 · 9 펜던트 · 10 훈장
 */
public class PlayerEquip {
    public static final String[] SLOT_TYPES = {"_unused", "gloves", "cape", "belt", "earring", "ring", "ring", "ring", "ring", "pendant", "medal"};
    public final ItemStackHandler items = new ItemStackHandler(SLOT_TYPES.length) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return SLOT_TYPES[slot].equals(Gear.slotOf(stack));
        }

        @Override
        protected void onContentsChanged(int slot) {
            dirty = true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    public boolean dirty = true;

    public static PlayerEquip get(Player p) {
        return p.getCapability(Provider.CAP).orElseGet(PlayerEquip::new);
    }

    /** 무기 = 주로 쓰는 손에 든 무기 (Epic Fight 모션도 손 기준) */
    public static ItemStack weapon(Player p) {
        ItemStack s = p.getMainHandItem();
        return "weapon".equals(Gear.slotOf(s)) ? s : ItemStack.EMPTY;
    }

    /** 착용 중인 장비의 스탯 합계 (착용 조건을 못 맞춘 장비는 빼고) */
    public Map<String, Double> sum(Player p) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 1; i < items.getSlots(); i++) add(p, items.getStackInSlot(i), m);
        add(p, weapon(p), m);
        for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            add(p, p.getItemBySlot(es), m);
        }
        return m;
    }

    private static void add(Player p, ItemStack s, Map<String, Double> m) {
        GearDefs.GearDef d = Gear.def(s);
        if (d == null || !Gear.canEquip(p, s)) return;
        d.stats().forEach((k, v) -> m.merge(k, v, Double::sum));
        CompoundTag o = Gear.opts(s);
        for (String k : o.getAllKeys()) m.merge(k, (double) o.getInt(k), Double::sum);
        Enhance.addBonus(s, m);   // 주문서 + 스타포스
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        public static final Capability<PlayerEquip> CAP = CapabilityManager.get(new CapabilityToken<>() {});
        private final PlayerEquip data = new PlayerEquip();
        private final LazyOptional<PlayerEquip> opt = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? opt.cast() : LazyOptional.empty();
        }

        @Override public CompoundTag serializeNBT() { return data.items.serializeNBT(); }
        @Override public void deserializeNBT(CompoundTag nbt) { data.items.deserializeNBT(nbt); }
    }
}
