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

    // 테스트 무기 (장비 시스템 전 임시) — 공격 속도: 검 1.6
    public static final RegistryObject<Item> TEST_SWORD_10 = ITEMS.register("test_sword_10",
            () -> new RpgWeapon(new Item.Properties().stacksTo(1), 10, 1.6));
    public static final RegistryObject<Item> TEST_SWORD_30 = ITEMS.register("test_sword_30",
            () -> new RpgWeapon(new Item.Properties().stacksTo(1), 30, 1.6));
    public static final RegistryObject<Item> TEST_SWORD_100 = ITEMS.register("test_sword_100",
            () -> new RpgWeapon(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), 100, 1.6));

    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand",
            () -> new rpg.core.zone.ZoneWand(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgcore"))
            .icon(() -> STAT_RESET_SCROLL.get().getDefaultInstance())
            .displayItems((params, out) -> {
                out.accept(STAT_RESET_SCROLL.get());
                out.accept(TEST_SWORD_10.get());
                out.accept(TEST_SWORD_30.get());
                out.accept(TEST_SWORD_100.get());
                out.accept(ZONE_WAND.get());
            })
            .build());
}
