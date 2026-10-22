package rpg.core.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import rpg.core.RpgCore;

/** 새로 만든 몹 7종 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgCore.MODID);

    private static RegistryObject<EntityType<RpgMonster>> monster(String id, float w, float h, int voice) {
        return TYPES.register(id, () -> EntityType.Builder.<RpgMonster>of((t, l) -> new RpgMonster(t, l, voice), MobCategory.MONSTER)
                .sized(w, h).clientTrackingRange(10).build(RpgCore.MODID + ":" + id));
    }

    /** GeckoLib 애니메이션 몹 (Blockbench 모델·애니 그대로) */
    private static RegistryObject<EntityType<GeoRpgMonster>> geoMonster(String id, float w, float h, int voice) {
        return TYPES.register(id, () -> EntityType.Builder.<GeoRpgMonster>of((t, l) -> new GeoRpgMonster(t, l, voice), MobCategory.MONSTER)
                .sized(w, h).clientTrackingRange(10).build(RpgCore.MODID + ":" + id));
    }

    public static final RegistryObject<EntityType<GeoRpgMonster>> RAT = geoMonster("rat", 0.6f, 0.45f, 0);
    public static final RegistryObject<EntityType<GeoRpgMonster>> MUSH_KID = geoMonster("mush_kid", 0.6f, 0.9f, 1);
    public static final RegistryObject<EntityType<GeoRpgMonster>> MUSH_SPIRIT = geoMonster("mush_spirit", 0.6f, 1.75f, 1);
    public static final RegistryObject<EntityType<GeoRpgMonster>> MUSH_SOLDIER = geoMonster("mush_soldier", 0.7f, 2.15f, 1);
    public static final RegistryObject<EntityType<RpgMonster>> MUSH_BEAST = monster("mush_beast", 1.9f, 2.2f, 2);
    public static final RegistryObject<EntityType<FlyingRpgMonster>> MUSH_FAIRY = TYPES.register("mush_fairy",
            () -> EntityType.Builder.<FlyingRpgMonster>of(FlyingRpgMonster::new, MobCategory.MONSTER)
                    .sized(0.5f, 0.6f).clientTrackingRange(10).build(RpgCore.MODID + ":mush_fairy"));
    public static final RegistryObject<EntityType<BrownBear>> BROWN_BEAR = TYPES.register("brown_bear",
            () -> EntityType.Builder.<BrownBear>of(BrownBear::new, MobCategory.CREATURE)
                    .sized(1.4f, 1.4f).clientTrackingRange(10).build(RpgCore.MODID + ":brown_bear"));
    /** 보스 "숲의 주인": 갈색 곰 몸 2.5배 */
    public static final RegistryObject<EntityType<ForestLord>> FOREST_LORD = TYPES.register("forest_lord",
            () -> EntityType.Builder.<ForestLord>of(ForestLord::new, MobCategory.CREATURE)
                    .sized(3.5f, 3.5f).clientTrackingRange(16).updateInterval(1).build(RpgCore.MODID + ":forest_lord"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(RAT.get(), RpgMonster.attrs(0.32).build());
        e.put(MUSH_KID.get(), RpgMonster.attrs(0.26).build());
        e.put(MUSH_SPIRIT.get(), RpgMonster.attrs(0.25).build());
        e.put(MUSH_SOLDIER.get(), RpgMonster.attrs(0.24).build());
        e.put(MUSH_BEAST.get(), RpgMonster.attrs(0.27).build());
        e.put(MUSH_FAIRY.get(), FlyingRpgMonster.flyAttrs().build());
        e.put(BROWN_BEAR.get(), PolarBear.createAttributes().build());
        e.put(FOREST_LORD.get(), PolarBear.createAttributes().build());
    }
}
