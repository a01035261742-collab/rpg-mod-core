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
    public static final RegistryObject<EntityType<GeoRpgMonster>> MUSH_BEAST = geoMonster("mush_beast", 1.9f, 2.2f, 2);
    public static final RegistryObject<EntityType<FlyingRpgMonster>> MUSH_FAIRY = TYPES.register("mush_fairy",
            () -> EntityType.Builder.<FlyingRpgMonster>of(FlyingRpgMonster::new, MobCategory.MONSTER)
                    .sized(0.5f, 0.6f).clientTrackingRange(10).build(RpgCore.MODID + ":mush_fairy"));
    /** 보스 기믹: 공허에 침식된 독버섯 기둥 (거대버섯 수호자 2페이즈) */
    public static final RegistryObject<EntityType<GeoRpgMonster>> VOID_PILLAR = geoMonster("void_pillar", 1.6f, 3.6f, 1);
    /** 레이드 기믹: 실 기둥 (어둠거미여왕 천장 숨기). 키 = 아래 실 뭉치 높이 → 체력바가 뭉치 위에 뜸 */
    public static final RegistryObject<EntityType<GeoRpgMonster>> WEB_PILLAR = geoMonster("web_pillar", 1.0f, 3.5f, 0);
    /** 곰: Blockbench 모델 (북극곰 비율 + 갈색 털). 보스 숲의 주인은 아직 북극곰 몸 (BrownBear 클래스) */
    public static final RegistryObject<EntityType<GeoRpgMonster>> BROWN_BEAR = geoMonster("brown_bear", 1.4f, 1.4f, 4);
    /** 보스 "거대버섯 수호자": 침식된 늙은 버섯 거인 (GeckoLib) */
    public static final RegistryObject<EntityType<MushGuardian>> MUSH_GUARDIAN = TYPES.register("mush_guardian",
            () -> EntityType.Builder.<MushGuardian>of(MushGuardian::new, MobCategory.MONSTER)
                    .sized(3.8f, 4.4f).clientTrackingRange(16).updateInterval(1).build(RpgCore.MODID + ":mush_guardian"));
    /** 보스 "숲의 주인": 갈색 곰 몸 2.5배 */
    public static final RegistryObject<EntityType<ForestLord>> FOREST_LORD = TYPES.register("forest_lord",
            () -> EntityType.Builder.<ForestLord>of(ForestLord::new, MobCategory.MONSTER)
                    .sized(3.5f, 3.5f).clientTrackingRange(16).updateInterval(1).build(RpgCore.MODID + ":forest_lord"));

    /** 보스 패턴 소품 (판정 없음, 연출): 던지는 바위 · 바위 기둥 · 공허 결정 */
    private static RegistryObject<EntityType<BossProp>> prop(String id, float w, float h) {
        return TYPES.register(id, () -> EntityType.Builder.<BossProp>of(BossProp::new, MobCategory.MISC)
                .sized(w, h).clientTrackingRange(10).updateInterval(1).noSave().build(RpgCore.MODID + ":" + id));
    }
    public static final RegistryObject<EntityType<BossProp>> BOSS_ROCK = prop("boss_rock", 1.0f, 1.0f);
    public static final RegistryObject<EntityType<BossProp>> ROCK_PILLAR = prop("rock_pillar", 2.0f, 3.0f);
    public static final RegistryObject<EntityType<BossProp>> VOID_CRYSTAL = prop("void_crystal", 1.5f, 2.6f);
    public static final RegistryObject<EntityType<BossProp>> MAGIC_CIRCLE = prop("magic_circle", 1.0f, 0.2f);
    // 수호자 패턴 이펙트
    public static final RegistryObject<EntityType<BossProp>> VOID_BEAM = prop("void_beam", 0.5f, 0.5f);
    public static final RegistryObject<EntityType<BossProp>> SPORE_BEAM = prop("spore_beam", 0.5f, 0.5f);
    public static final RegistryObject<EntityType<BossProp>> SPORE_BALL = prop("spore_ball", 0.6f, 0.6f);
    public static final RegistryObject<EntityType<BossProp>> POISON_POOL = prop("poison_pool", 1.0f, 0.1f);
    public static final RegistryObject<EntityType<BossProp>> VOID_CRACK = prop("void_crack", 1.0f, 0.1f);
    public static final RegistryObject<EntityType<BossProp>> RESONANCE_RING = prop("resonance_ring", 1.0f, 1.0f);
    public static final RegistryObject<EntityType<BossProp>> SHOCKWAVE = prop("shockwave", 1.0f, 0.1f);
    /** 단단한 버섯 (보이는 모양만, 부딪힘은 투명 블록) */
    public static final RegistryObject<EntityType<BossProp>> HARD_MUSHROOM = prop("hard_mushroom", 1.0f, 2.8f);

    /** 레이드 보스 "어둠거미여왕": 거미 몸 3배 (임시) */
    public static final RegistryObject<EntityType<SpiderQueen>> SPIDER_QUEEN = TYPES.register("spider_queen",
            () -> EntityType.Builder.<SpiderQueen>of(SpiderQueen::new, MobCategory.MONSTER)
                    .sized(4.2f, 2.7f).clientTrackingRange(16).updateInterval(1).build(RpgCore.MODID + ":spider_queen"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent e) {
        e.put(SPIDER_QUEEN.get(), net.minecraft.world.entity.monster.Spider.createAttributes().build());
        e.put(RAT.get(), RpgMonster.attrs(0.32).build());
        e.put(MUSH_KID.get(), RpgMonster.attrs(0.26).build());
        e.put(MUSH_SPIRIT.get(), RpgMonster.attrs(0.25).build());
        e.put(VOID_PILLAR.get(), RpgMonster.attrs(0.0).add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1.0).build());
        e.put(WEB_PILLAR.get(), RpgMonster.attrs(0.0).add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1.0).build());
        e.put(MUSH_SOLDIER.get(), RpgMonster.attrs(0.24).build());
        e.put(MUSH_BEAST.get(), RpgMonster.attrs(0.27).build());
        e.put(MUSH_FAIRY.get(), FlyingRpgMonster.flyAttrs().build());
        e.put(BROWN_BEAR.get(), RpgMonster.attrs(0.25).build());
        e.put(FOREST_LORD.get(), RpgMonster.attrs(0.25).add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1.0).build());
        e.put(MUSH_GUARDIAN.get(), RpgMonster.attrs(0.25).add(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, 1.0).build());
    }
}
