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
        java.util.List<String> all = new java.util.ArrayList<>(java.util.List.of(BASIC));
        for (String b : new String[]{"warrior_sword", "archer_bow", "mage_staff", "thief_dagger", "gloves", "cape", "belt", "earring", "ring", "pendant", "medal"})
            for (int lv : new int[]{20, 30, 40}) all.add(b + "_" + lv);   // 테스트 장비 Lv20·30·40
        for (String id : all) GEAR.add(ITEMS.register(id, () -> id.startsWith("archer_bow")
                ? new rpg.core.equip.GearItems.Bow(new Item.Properties(), id)
                : new rpg.core.equip.GearItems.Basic(new Item.Properties(), id)));
        String[][] armor = {{"hat", "HELMET"}, {"top", "CHESTPLATE"}, {"bottom", "LEGGINGS"}, {"shoes", "BOOTS"}};
        for (String[] a : armor) {
            var type = net.minecraft.world.item.ArmorItem.Type.valueOf(a[1]);
            GEAR.add(ITEMS.register(a[0] + "_1", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.CHAIN, type, new Item.Properties(), a[0] + "_1")));
            GEAR.add(ITEMS.register(a[0] + "_10", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.IRON, type, new Item.Properties(), a[0] + "_10")));
            GEAR.add(ITEMS.register(a[0] + "_20", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.GOLD, type, new Item.Properties(), a[0] + "_20")));
            GEAR.add(ITEMS.register(a[0] + "_30", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.DIAMOND, type, new Item.Properties(), a[0] + "_30")));
            GEAR.add(ITEMS.register(a[0] + "_40", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.NETHERITE, type, new Item.Properties(), a[0] + "_40")));
        }
    }

    // 드롭: 골드 주머니 · 몹 재료 (일반/희귀) · 강화 재료 (강화 단계에서 쓸 자리)
    public static final RegistryObject<Item> GOLD_POUCH = ITEMS.register("gold_pouch", () -> new rpg.core.loot.GoldPouch(new Item.Properties()));
    public static final java.util.List<RegistryObject<Item>> MATERIALS = new java.util.ArrayList<>();
    private static final String[][] MAT = {
            {"pig_hide", "c"}, {"golden_hoof", "r"}, {"cow_hide", "c"}, {"cow_horn", "r"}, {"slime_goo", "c"}, {"slime_core", "r"},
            {"rat_tail", "c"}, {"rat_tooth", "r"}, {"dog_fur", "c"}, {"dog_fang", "r"}, {"bee_sting", "c"}, {"royal_jelly", "r"},
            {"boar_bristle", "c"}, {"boar_tusk", "r"}, {"bear_pelt", "c"}, {"bear_paw", "r"}, {"spore_dust", "c"}, {"spirit_cap", "r"},
            {"tiny_cap", "c"}, {"glow_spore", "r"}, {"toxic_cap", "c"}, {"soldier_badge", "r"}, {"fairy_wing", "c"}, {"fairy_dust", "r"},
            {"spore_silk", "c"}, {"spore_gland", "r"}, {"witch_herb", "c"}, {"odd_potion", "r"}, {"dark_silk", "c"}, {"dark_eye", "r"},
            {"beast_shell", "c"}, {"beast_core", "r"}};
    static {
        for (String[] m : MAT) {
            boolean rare = m[1].equals("r");
            MATERIALS.add(ITEMS.register(m[0], () -> new Material(new Item.Properties().rarity(rare ? Rarity.UNCOMMON : Rarity.COMMON),
                    rare ? "희귀 몹 재료 · 제작/판매" : "몹 재료 · 제작/판매", true)));
        }
        MATERIALS.add(ITEMS.register("star_fragment", () -> new Material(new Item.Properties().rarity(Rarity.RARE), "강화 재료 (강화 시스템에서 사용 예정)", false)));
        MATERIALS.add(ITEMS.register("scroll_fragment", () -> new Material(new Item.Properties().rarity(Rarity.RARE), "주문서 재료 (강화 시스템에서 사용 예정)", false)));
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
                for (var m : MATERIALS) out.accept(m.get());
                out.accept(ZONE_WAND.get());
            })
            .build());
}
