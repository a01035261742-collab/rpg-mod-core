package rpg.core.equip;

import java.util.List;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 장비 아이템 클래스 2가지: 일반(무기·장신구), 방어구(마크 갑옷 칸) */
public final class GearItems {
    private GearItems() {}

    /** 무기·장갑·망토·벨트·장신구 */
    public static class Basic extends Item implements Gear.GearItem {
        private final String id;

        public Basic(Properties p, String id) {
            super(p.stacksTo(1));
            this.id = id;
        }

        @Override public String gearId() { return id; }

        @Override
        public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean selected) {
            if (!level.isClientSide) Gear.roll(s);
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            Gear.tooltip(s, tip);
        }
    }

    /** 근접 무기 (검·지팡이·단검): 마크 검 종류라 Epic Fight가 전투 무기로 인식 (블록 조준 안 함).
     *  내구도·바닐라 공격력 없음 → 피해는 RPG 공식만 */
    public static final net.minecraft.world.item.Tier NO_TIER = new net.minecraft.world.item.Tier() {
        @Override public int getUses() { return 0; }
        @Override public float getSpeed() { return 1f; }
        @Override public float getAttackDamageBonus() { return 0f; }
        @Override public int getLevel() { return 0; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    public static class Weapon extends net.minecraft.world.item.SwordItem implements Gear.GearItem {
        private final String id;

        public Weapon(Properties p, String id) {
            super(NO_TIER, 0, 0f, p.stacksTo(1));
            this.id = id;
        }

        @Override public String gearId() { return id; }

        @Override
        public com.google.common.collect.Multimap<net.minecraft.world.entity.ai.attributes.Attribute, net.minecraft.world.entity.ai.attributes.AttributeModifier>
                getDefaultAttributeModifiers(net.minecraft.world.entity.EquipmentSlot slot) {
            return com.google.common.collect.ImmutableMultimap.of();
        }

        @Override public boolean isCorrectToolForDrops(net.minecraft.world.level.block.state.BlockState b) { return false; }
        @Override public boolean isEnchantable(ItemStack s) { return false; }
        @Override public boolean canPerformAction(ItemStack s, net.minecraftforge.common.ToolAction a) { return false; }   // 바닐라 휩쓸기 없음
        @Override public boolean hurtEnemy(ItemStack s, net.minecraft.world.entity.LivingEntity t, net.minecraft.world.entity.LivingEntity a) { return true; }
        @Override public boolean mineBlock(ItemStack s, Level l, net.minecraft.world.level.block.state.BlockState b, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.LivingEntity e) { return true; }

        @Override
        public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean selected) {
            if (!level.isClientSide) Gear.roll(s);
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            Gear.tooltip(s, tip);
        }
    }

    /** 궁수 활: 당겨서 쏘기 (Epic Fight 활 모션), 화살 소모 없음. 피해는 RPG 공식 × 당긴 정도 */
    public static class Bow extends net.minecraft.world.item.BowItem implements Gear.GearItem {
        private final String id;

        public Bow(Properties p, String id) {
            super(p.stacksTo(1));
            this.id = id;
        }

        @Override public String gearId() { return id; }

        @Override
        public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player p,
                                                                          net.minecraft.world.InteractionHand hand) {
            p.startUsingItem(hand);
            return net.minecraft.world.InteractionResultHolder.consume(p.getItemInHand(hand));
        }

        @Override
        public void releaseUsing(ItemStack s, Level level, net.minecraft.world.entity.LivingEntity e, int timeLeft) {
            if (!(e instanceof net.minecraft.world.entity.player.Player p)) return;
            float power = getPowerForTime(getUseDuration(s) - timeLeft);
            if (power < 0.1f || level.isClientSide) return;
            var a = new rpg.core.item.RpgArrow(level, p);
            a.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 1.5f + power * 1.5f, 0.5f);   // 중력 없음: 덜 당겨도 똑바로
            if (power >= 1.0f) a.setCritArrow(true);
            a.getPersistentData().putFloat("rpgPower", power);
            level.addFreshEntity(a);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARROW_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0f, 1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        }

        @Override
        public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean selected) {
            if (!level.isClientSide) Gear.roll(s);
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            Gear.tooltip(s, tip);
        }

        @Override public boolean isEnchantable(ItemStack s) { return false; }
    }

    /** 모자·상의·하의·신발 (바닐라 갑옷 칸, 바닐라 방어력 0 — RPG 방어만 씀) */
    public static class Armor extends ArmorItem implements Gear.GearItem {
        private final String id;

        public Armor(ArmorMaterial mat, Type type, Properties p, String id) {
            super(mat, type, p.stacksTo(1));
            this.id = id;
        }

        @Override public String gearId() { return id; }

        @Override
        public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean selected) {
            if (!level.isClientSide) Gear.roll(s);
        }

        @Override
        public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
            Gear.tooltip(s, tip);
        }
    }

    /** 바닐라 텍스처(사슬·철)를 쓰는 방어력 0 재질 */
    public record Mat(String name, SoundEvent sound) implements ArmorMaterial {
        @Override public int getDurabilityForType(ArmorItem.Type t) { return 0; }
        @Override public int getDefenseForType(ArmorItem.Type t) { return 0; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return sound; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return name; }
        @Override public float getToughness() { return 0; }
        @Override public float getKnockbackResistance() { return 0; }
    }

    public static final Mat CHAIN = new Mat("chainmail", SoundEvents.ARMOR_EQUIP_CHAIN);
    public static final Mat IRON = new Mat("iron", SoundEvents.ARMOR_EQUIP_IRON);
    public static final Mat GOLD = new Mat("gold", SoundEvents.ARMOR_EQUIP_GOLD);
    public static final Mat DIAMOND = new Mat("diamond", SoundEvents.ARMOR_EQUIP_DIAMOND);
    public static final Mat NETHERITE = new Mat("netherite", SoundEvents.ARMOR_EQUIP_NETHERITE);
}
