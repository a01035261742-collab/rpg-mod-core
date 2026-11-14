package rpg.core.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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

    public static final RegistryObject<Item> PASSIVE_RESET_SCROLL = ITEMS.register("passive_reset_scroll",
            () -> new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)) {
                @Override public void appendHoverText(net.minecraft.world.item.ItemStack s, @org.jetbrains.annotations.Nullable net.minecraft.world.level.Level l,
                                                      java.util.List<Component> tip, net.minecraft.world.item.TooltipFlag f) {
                    tip.add(Component.literal("패시브 트리(N) → [초기화] 할 때 1장 씀").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });

    public static final RegistryObject<Item> RED_POTION = potion("red_potion", Rarity.COMMON);
    public static final RegistryObject<Item> ORANGE_POTION = potion("orange_potion", Rarity.COMMON);
    public static final RegistryObject<Item> WHITE_POTION = potion("white_potion", Rarity.COMMON);
    public static final RegistryObject<Item> BLUE_POTION = potion("blue_potion", Rarity.COMMON);
    public static final RegistryObject<Item> DEEP_BLUE_POTION = potion("deep_blue_potion", Rarity.COMMON);
    public static final RegistryObject<Item> MANA_ELIXIR = potion("mana_elixir", Rarity.COMMON);
    public static final RegistryObject<Item> ELIXIR = potion("elixir", Rarity.UNCOMMON);
    public static final RegistryObject<Item> POWER_ELIXIR = potion("power_elixir", Rarity.RARE);
    // 0.68: 중형·대형 물약 (HP 빨·주·흰, MP 파랑·보라)
    public static final RegistryObject<Item> RED_POTION_M = potion("red_potion_m", Rarity.COMMON);
    public static final RegistryObject<Item> ORANGE_POTION_M = potion("orange_potion_m", Rarity.COMMON);
    public static final RegistryObject<Item> WHITE_POTION_M = potion("white_potion_m", Rarity.COMMON);
    public static final RegistryObject<Item> RED_POTION_L = potion("red_potion_l", Rarity.UNCOMMON);
    public static final RegistryObject<Item> ORANGE_POTION_L = potion("orange_potion_l", Rarity.UNCOMMON);
    public static final RegistryObject<Item> WHITE_POTION_L = potion("white_potion_l", Rarity.UNCOMMON);
    public static final RegistryObject<Item> PURPLE_POTION_M = potion("purple_potion_m", Rarity.COMMON);
    public static final RegistryObject<Item> BLUE_POTION_L = potion("blue_potion_l", Rarity.UNCOMMON);
    public static final RegistryObject<Item> PURPLE_POTION_L = potion("purple_potion_l", Rarity.UNCOMMON);
    // 버프 아이템 (10분, 고정 수치)
    static { for (String b : BuffItem.DEFS.keySet()) ITEMS.register(b, () -> new BuffItem(new Item.Properties(), b)); }

    private static RegistryObject<Item> potion(String id, Rarity rarity) {
        return ITEMS.register(id, () -> new Potion(new Item.Properties().rarity(rarity), id));
    }

    // 장비 (수치는 equipment.json). 장비마다 따로 등록
    public static final java.util.List<RegistryObject<Item>> GEAR = new java.util.ArrayList<>();
    private static final String[] BASIC = {
            "warrior_sword_1", "warrior_sword_10", "archer_bow_1", "archer_bow_10",
            "mage_staff_1", "mage_staff_10", "thief_dagger_1", "thief_dagger_10",
            "gloves_1", "gloves_10", "cape_1", "cape_10", "belt_1", "belt_10",
            "earring_1", "earring_10", "ring_1", "ring_10", "pendant_1", "pendant_10", "medal_1", "medal_10"};

    /** 직업 가죽 방어구 색 [궁수·마법사·도적][Lv10·20·30·40] */
    public static final int[][] JOB_COLORS = {
            {0x6B8E3D, 0x4E7A2E, 0x7A6A3A, 0x2F3B2A},
            {0x4A5FC1, 0x6A3FA8, 0x8E4FB0, 0x2B1E4A},
            {0x5A5A5A, 0x3C3C46, 0x5A3C5A, 0x1E1E24}};

    /** 몹 전용 장신구 16종 (drops.json uniques) */
    public static final String[] UNIQUE = {"pig_snout_pendant", "cow_horn_earring", "slime_ring", "rat_tail_belt", "dog_fang_earring", "bee_cape", "tusk_gloves", "bear_belt", "spirit_pendant", "mushroom_ring", "soldier_gloves", "fairy_cape", "gland_earring", "witch_pendant", "dark_eye_ring", "beast_belt", "forest_lord_ring", "guardian_ring", "forest_lord_belt", "guardian_pendant", "guardian_cape", "forest_hunter_medal", "mush_hunter_medal", "hunter_medal", "slayer_medal", "enhancer_medal", "unlucky_medal", "boss_hunter_medal", "rich_medal",
            "spider_queen_earring", "spider_queen_gloves", "spider_queen_ring", "spider_queen_medal",
            "cave_fang_earring", "bat_cape", "venom_ring", "spike_gloves", "ore_pendant", "miner_belt", "void_earring"};   // 3지역 몹 전용   // + 보스 장신구

    static {
        java.util.List<String> all = new java.util.ArrayList<>(java.util.List.of(BASIC));
        for (String b : new String[]{"warrior_sword", "archer_bow", "mage_staff", "thief_dagger", "gloves", "cape", "belt", "earring", "ring", "pendant", "medal"})
            for (int lv : new int[]{20, 30, 40, 50, 60}) all.add(b + "_" + lv);   // Lv20~60 (제작·상점)
        all.addAll(java.util.List.of(UNIQUE));   // 몹 전용 장신구 (드롭 전용)
        all.addAll(java.util.List.of("warrior_sword_queen", "archer_bow_queen", "mage_staff_queen", "thief_dagger_queen"));   // 여왕의 독니 무기 (레이드)
        for (String id : all) GEAR.add(ITEMS.register(id, () -> id.startsWith("archer_bow")
                ? new rpg.core.equip.GearItems.Bow(new Item.Properties(), id)
                : (id.startsWith("warrior_sword") || id.startsWith("mage_staff") || id.startsWith("thief_dagger"))
                ? new rpg.core.equip.GearItems.Weapon(new Item.Properties(), id)
                : new rpg.core.equip.GearItems.Basic(new Item.Properties(), id)));
        String[][] armor = {{"hat", "HELMET"}, {"top", "CHESTPLATE"}, {"bottom", "LEGGINGS"}, {"shoes", "BOOTS"}};
        for (String[] a : armor) {
            var type = net.minecraft.world.item.ArmorItem.Type.valueOf(a[1]);
            GEAR.add(ITEMS.register(a[0] + "_1", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.CHAIN, type, new Item.Properties(), a[0] + "_1")));
            GEAR.add(ITEMS.register(a[0] + "_10", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.IRON, type, new Item.Properties(), a[0] + "_10")));
            GEAR.add(ITEMS.register(a[0] + "_20", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.GOLD, type, new Item.Properties(), a[0] + "_20")));
            GEAR.add(ITEMS.register(a[0] + "_30", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.DIAMOND, type, new Item.Properties(), a[0] + "_30")));
            GEAR.add(ITEMS.register(a[0] + "_40", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.NETHERITE, type, new Item.Properties(), a[0] + "_40")));
            GEAR.add(ITEMS.register(a[0] + "_50", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.IRON, type, new Item.Properties(), a[0] + "_50")));
            GEAR.add(ITEMS.register(a[0] + "_60", () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.NETHERITE, type, new Item.Properties(), a[0] + "_60")));
            // 직업별 방어구 (제작 전용): 전사 = 금속(철·금·다이아·네더라이트), 나머지 = 직업 색 가죽
            int[] tiers = {10, 20, 30, 40, 50, 60};
            var metal = new rpg.core.equip.GearItems.Mat[]{rpg.core.equip.GearItems.IRON, rpg.core.equip.GearItems.GOLD,
                    rpg.core.equip.GearItems.DIAMOND, rpg.core.equip.GearItems.NETHERITE, rpg.core.equip.GearItems.IRON, rpg.core.equip.GearItems.NETHERITE};
            for (int t = 0; t < tiers.length; t++) {
                final int ti = t;
                String wid = "warrior_" + a[0] + "_" + tiers[t];
                GEAR.add(ITEMS.register(wid, () -> new rpg.core.equip.GearItems.Armor(metal[ti], type, new Item.Properties(), wid)));
                for (int j = 0; j < 3; j++) {
                    String job = new String[]{"archer", "mage", "thief"}[j];
                    String id = job + "_" + a[0] + "_" + tiers[t];
                    GEAR.add(ITEMS.register(id, () -> new rpg.core.equip.GearItems.Armor(rpg.core.equip.GearItems.LEATHER, type, new Item.Properties(), id)));
                }
            }
        }
    }

    // 퀘스트 물건 (배달·가져오기)
    public static final java.util.List<RegistryObject<Item>> QUEST_ITEMS = new java.util.ArrayList<>();
    static {
        for (String id : new String[]{"quest_letter", "quest_parcel", "quest_medicine", "quest_notes", "quest_patrol_log", "quest_old_bow", "quest_spore_tome", "quest_battle_map", "quest_witch_scroll", "quest_patrol_journal", "quest_miner_pick", "quest_amethyst", "quest_miner_journal"})
            QUEST_ITEMS.add(ITEMS.register(id, QuestItem::new));
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
            {"beast_shell", "c"}, {"beast_core", "r"},
            {"cave_silk", "c"}, {"cave_fang", "r"}, {"bat_wing", "c"}, {"echo_crystal", "r"}, {"venom_sac", "c"}, {"venom_crystal", "r"}, {"stone_spike", "c"}, {"spike_heart", "r"},
            {"ore_goo", "c"}, {"ore_core", "r"}, {"miner_bone", "c"}, {"miner_badge", "r"}, {"golem_shard", "c"}, {"void_core", "r"}};   // 3지역
    public static final RegistryObject<Item> SCROLL_FRAGMENT, STAR_FRAGMENT;
    static {
        for (String[] m : MAT) {
            boolean rare = m[1].equals("r");
            MATERIALS.add(ITEMS.register(m[0], () -> new Material(new Item.Properties().rarity(rare ? Rarity.UNCOMMON : Rarity.COMMON),
                    rare ? "희귀 몹 재료 · 제작/판매" : "몹 재료 · 제작/판매", true)));
        }
        STAR_FRAGMENT = ITEMS.register("star_fragment", () -> new Material(new Item.Properties().rarity(Rarity.RARE), "장비 계승 재료 (스타포스 옮기기 · 강화 창 [계승])", false));
        MATERIALS.add(STAR_FRAGMENT);
        MATERIALS.add(ITEMS.register("forest_lord_claw", () -> new Material(new Item.Properties().rarity(Rarity.EPIC), "보스 \"숲의 주인\" 확정 드롭 · 판매용 (나중에 제작 재료)", false)));
        MATERIALS.add(ITEMS.register("forest_lord_treasure", () -> new Treasure(new Item.Properties().rarity(Rarity.EPIC), "보스 \"숲의 주인\" 보물 · 상점에 팔면 20,000 G")));
        MATERIALS.add(ITEMS.register("guardian_treasure", () -> new Treasure(new Item.Properties().rarity(Rarity.EPIC), "보스 \"거대버섯 수호자\" 보물 · 상점에 팔면 40,000 G")));
        MATERIALS.add(ITEMS.register("spider_queen_treasure", () -> new Treasure(new Item.Properties().rarity(Rarity.EPIC), "레이드 \"어둠거미여왕\" 보물 · 상점에 팔면 80,000 G")));
        MATERIALS.add(ITEMS.register("guardian_heart", () -> new Material(new Item.Properties().rarity(Rarity.EPIC), "보스 \"거대버섯 수호자\" 확정 드롭 · 판매용 (나중에 제작 재료)", false)));
        SCROLL_FRAGMENT = ITEMS.register("scroll_fragment", () -> new Material(new Item.Properties().rarity(Rarity.RARE), "10개 → 메뉴(M) · 강화 · 조각 합성에서 무작위 주문서", false));
        MATERIALS.add(SCROLL_FRAGMENT);
    }

    /** 귀환 주문서: 3초 뒤 가장 가까운 마을로 */
    public static final RegistryObject<Item> RETURN_SCROLL = ITEMS.register("return_scroll", () -> new ReturnScroll(new Item.Properties().stacksTo(64)));
    /** 마을 이동 주문서: 가 본 마을 골라서 3초 뒤 이동 (rpg.core.town.Travel) */
    public static final RegistryObject<Item> TOWN_SCROLL = ITEMS.register("town_scroll", () -> new TownScroll(new Item.Properties().stacksTo(64)));

    /** 잠재능력: 부여 주문서 (레어·에픽) · 수상한 큐브 (골드 대신 재설정) */
    public static final RegistryObject<Item> POTENTIAL_SCROLL = ITEMS.register("potential_scroll",
            () -> new Material(new Item.Properties().rarity(Rarity.RARE), "잠재능력이 없는 장비에 레어 잠재능력 부여 · 메뉴(M) → 강화 → 잠재능력", false));
    public static final RegistryObject<Item> EPIC_POTENTIAL_SCROLL = ITEMS.register("epic_potential_scroll",
            () -> new Material(new Item.Properties().rarity(Rarity.EPIC), "잠재능력이 없는 장비에 에픽 잠재능력 부여 · 메뉴(M) → 강화 → 잠재능력", false));
    public static final RegistryObject<Item> CUBE = ITEMS.register("cube",
            () -> new Material(new Item.Properties().rarity(Rarity.EPIC), "골드 대신 잠재능력 1번 재설정 · 메뉴(M) → 강화 → 잠재능력", false));

    /** 환생의 불꽃: 추가 옵션 재설정 */
    public static final RegistryObject<Item> POWERFUL_FLAME = ITEMS.register("powerful_flame",
            () -> new Material(new Item.Properties().rarity(Rarity.RARE), "추가 옵션 재설정 (3~6단계) · 메뉴(M) → 강화 → 추가옵션", false));
    public static final RegistryObject<Item> ETERNAL_FLAME = ITEMS.register("eternal_flame",
            () -> new Material(new Item.Properties().rarity(Rarity.EPIC), "추가 옵션 재설정 (4~7단계) · 메뉴(M) → 강화 → 추가옵션", false));
    public static final RegistryObject<Item> BLACK_FLAME = ITEMS.register("black_flame",
            () -> new Material(new Item.Properties().rarity(Rarity.EPIC), "추가 옵션 재설정 (4~7단계, 이전/새 옵션 선택) · 메뉴(M) → 강화 → 추가옵션", false));

    /** 주문서 (종류·등급은 NBT) */
    public static final RegistryObject<Item> SCROLL = ITEMS.register("scroll", () -> new Scroll(new Item.Properties()));

    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand",
            () -> new rpg.core.zone.ZoneWand(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // 크리에이티브 탭: 무기 · 방어구 · 장신구 · 기타 (탭 안은 직업·세트·단계 순)
    private static final java.util.List<String> JOBS = java.util.List.of("warrior", "archer", "mage", "thief");
    private static final java.util.List<String> SETS = java.util.List.of("common", "warrior", "archer", "mage", "thief");
    private static final java.util.List<String> PARTS = java.util.List.of("hat", "top", "bottom", "shoes");
    private static final java.util.List<String> ACC = java.util.List.of("gloves", "cape", "belt", "earring", "ring", "pendant", "medal");

    private static int tierOf(String id) {
        String t = id.substring(id.lastIndexOf('_') + 1);
        return t.chars().allMatch(Character::isDigit) ? Integer.parseInt(t) : 99;   // queen 등은 맨 뒤
    }

    private static java.util.List<Item> gear(java.util.function.Predicate<Item> f, java.util.Comparator<String> order) {
        return GEAR.stream().map(RegistryObject::get).filter(f)
                .sorted(java.util.Comparator.comparing(i -> ((rpg.core.equip.Gear.GearItem) i).gearId(), order)).toList();
    }

    private static boolean isWeapon(Item i) {
        return i instanceof rpg.core.equip.GearItems.Weapon || i instanceof rpg.core.equip.GearItems.Bow;
    }

    private static boolean isArmor(Item i) { return i instanceof rpg.core.equip.GearItems.Armor; }

    private static Item byId(String id) {
        return GEAR.stream().map(RegistryObject::get).filter(i -> ((rpg.core.equip.Gear.GearItem) i).gearId().equals(id)).findFirst().orElse(Items.BARRIER);
    }

    public static final RegistryObject<CreativeModeTab> TAB_WEAPON = TABS.register("weapon", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgcore.weapon"))
            .icon(() -> byId("warrior_sword_40").getDefaultInstance())
            .displayItems((params, out) -> gear(ModItems::isWeapon, java.util.Comparator
                    .<String>comparingInt(id -> JOBS.indexOf(id.substring(0, id.indexOf('_'))))
                    .thenComparingInt(ModItems::tierOf)).forEach(out::accept))
            .build());

    public static final RegistryObject<CreativeModeTab> TAB_ARMOR = TABS.register("armor", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgcore.armor"))
            .icon(() -> byId("warrior_top_40").getDefaultInstance())
            .withTabsBefore(new net.minecraft.resources.ResourceLocation(RpgCore.MODID, "weapon"))
            .displayItems((params, out) -> gear(ModItems::isArmor, java.util.Comparator
                    .<String>comparingInt(id -> id.indexOf('_') == id.lastIndexOf('_') ? 0 : SETS.indexOf(id.substring(0, id.indexOf('_'))))
                    .thenComparingInt(ModItems::tierOf)
                    .thenComparingInt(id -> PARTS.indexOf(id.indexOf('_') == id.lastIndexOf('_') ? id.substring(0, id.indexOf('_'))
                            : id.substring(id.indexOf('_') + 1, id.lastIndexOf('_'))))).forEach(out::accept))
            .build());

    public static final RegistryObject<CreativeModeTab> TAB_ACC = TABS.register("accessory", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgcore.accessory"))
            .icon(() -> byId("ring_40").getDefaultInstance())
            .withTabsBefore(new net.minecraft.resources.ResourceLocation(RpgCore.MODID, "armor"))
            .displayItems((params, out) -> gear(i -> !isWeapon(i) && !isArmor(i), java.util.Comparator
                    .<String>comparingInt(id -> { int k = ACC.indexOf(id.substring(0, id.lastIndexOf('_'))); return k < 0 ? 100 : k; })
                    .thenComparingInt(ModItems::tierOf)
                    .thenComparingInt(id -> java.util.Arrays.asList(UNIQUE).indexOf(id))).forEach(out::accept))
            .build());

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgcore"))
            .icon(() -> STAT_RESET_SCROLL.get().getDefaultInstance())
            .withTabsBefore(new net.minecraft.resources.ResourceLocation(RpgCore.MODID, "accessory"))
            .displayItems((params, out) -> {
                out.accept(STAT_RESET_SCROLL.get());
                out.accept(RETURN_SCROLL.get());
                out.accept(TOWN_SCROLL.get());
                out.accept(rpg.core.world.ModBlocks.WAYSTONE_ITEM.get());
                out.accept(POTENTIAL_SCROLL.get());
                out.accept(EPIC_POTENTIAL_SCROLL.get());
                out.accept(CUBE.get());
                out.accept(POWERFUL_FLAME.get());
                out.accept(ETERNAL_FLAME.get());
                out.accept(BLACK_FLAME.get());
                for (String k : Scroll.KINDS) for (int g = 0; g < 4; g++) out.accept(Scroll.make(k, g));
                out.accept(Scroll.make("white", 0));
                out.accept(Scroll.make("chaos", 0));
                for (var po : new RegistryObject[]{RED_POTION, ORANGE_POTION, WHITE_POTION, BLUE_POTION, DEEP_BLUE_POTION, MANA_ELIXIR, ELIXIR, POWER_ELIXIR})
                    out.accept((Item) po.get());
                for (var m : MATERIALS) out.accept(m.get());
                for (var q : QUEST_ITEMS) out.accept(q.get());
                out.accept(ZONE_WAND.get());
            })
            .build());
}
