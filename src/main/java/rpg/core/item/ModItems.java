package rpg.core.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import rpg.core.RpgCore;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpgCore.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RpgCore.MODID);

    public static final RegistryObject<Item> STAT_RESET_SCROLL = ITEMS.register("stat_reset_scroll",
            () -> new StatResetScroll(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> RED_POTION = ITEMS.register("red_potion", () -> new Potion(new Item.Properties(), true));
    public static final RegistryObject<Item> BLUE_POTION = ITEMS.register("blue_potion", () -> new Potion(new Item.Properties(), false));

    // 장비 (수치는 equipment.json). 장비마다 따로 등록
    public static final java.util.List<RegistryObject<Item>> GEAR = new java.util.ArrayList<>();
    private static final String[] BASIC = {
            "warrior_sword_1", "warrior_sword_10", "archer_bow_1", "archer_bow_10",
            "mage_staff_1", "mage_staff_10", "thief_dagger_1", "thief_dagger_10",
            "gloves_1", "gloves_10", "cape_1", "cape_10", "belt_1", "belt_10",
            "earring_1", "earring_10", "ring_1", "ring_10", "pendant_1", "pendant_10", "medal_1", "medal_10"};

    static {
        for (String id : BASIC) GEAR.add(ITEMS.register(id, () -> new rpg.core.equip.GearItems.Basic(new Item.Properties(), id)));
        String[][] armor = {{"hat", "HELMET"}, {"top", "CHESTPLATE"}, {"bottom", "LEGGINGS"}, {"shoes", "BOOTS"}};
        for (String[] a : armor) {
            var type = net.minecraft.world.item.ArmorItem.Type.valueOf(a[1]);
            GEAR.add(ITEMS.register(a[0] + "_1", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.CHAIN, type, new Item.Properties(), a[0] + "_1")));
            GEAR.add(ITEMS.register(a[0] + "_10", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.IRON, type, new Item.Properties(), a[0] + "_10")));
        }
    }

    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand",
            () -> new rpg.core.zone.ZoneWand(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgcore"))
            .icon(() -> STAT_RESET_SCROLL.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(STAT_RESET_SCROLL.get());
                out.accept(RED_POTION.get());
                out.accept(BLUE_POTION.get());
                for (var g : GEAR) out.accept(g.get());
                out.accept(ZONE_WAND.get());
            })
            .build());
}
