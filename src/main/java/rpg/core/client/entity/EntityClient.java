package rpg.core.client.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PolarBearRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.entity.ModEntities;

/** 새 몹 모델·렌더러 등록 (클라이언트) */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EntityClient {
    private EntityClient() {}

    private static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(RpgCore.id(id), "main");
    }

    private static final ModelLayerLocation FAIRY = layer("mush_fairy"), BEAST = layer("mush_beast");

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(BEAST, MobModels::mushBeast);
    }

    private static <T extends Mob> void reg(EntityRenderersEvent.RegisterRenderers e, EntityType<T> type,
                                            ModelLayerLocation loc, String tex, float shadow) {
        ResourceLocation t = RpgCore.id("textures/entity/" + tex + ".png");
        EntityRendererProvider<T> p = ctx -> new MobRenderer<T, RpgModel<T>>(ctx, new RpgModel<>(ctx.bakeLayer(loc)), shadow) {
            @Override
            public ResourceLocation getTextureLocation(T ent) { return t; }
        };
        e.registerEntityRenderer(type, p);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.RAT.get(), ctx -> new RpgGeoRenderer(ctx, "rat", 0.3f));
        e.registerEntityRenderer(ModEntities.MUSH_KID.get(), ctx -> new RpgGeoRenderer(ctx, "mush_kid", 0.35f));
        e.registerEntityRenderer(ModEntities.MUSH_SPIRIT.get(), ctx -> new RpgGeoRenderer(ctx, "mush_spirit", 0.5f, true));
        e.registerEntityRenderer(ModEntities.MUSH_SOLDIER.get(), ctx -> new RpgGeoRenderer(ctx, "mush_soldier", 0.5f, true));
        e.registerEntityRenderer(ModEntities.MUSH_FAIRY.get(), ctx -> new RpgGeoRenderer(ctx, "mush_fairy", 0.25f));
        e.registerEntityRenderer(ModEntities.MUSH_BEAST.get(), ctx -> new RpgGeoRenderer(ctx, "mush_beast", 1.2f));
        e.registerEntityRenderer(ModEntities.BROWN_BEAR.get(), ctx -> new RpgGeoRenderer(ctx, "brown_bear", 0.9f));
        // 보스 거대버섯 수호자: GeckoLib 모델 (결정·눈 발광)
        e.registerEntityRenderer(ModEntities.MUSH_GUARDIAN.get(), ctx -> new RpgGeoRenderer(ctx, "mush_guardian", 2.4f, true));
        // 레이드 보스 어둠거미여왕 (GeckoLib) + 알 · 고치
        e.registerEntityRenderer(ModEntities.SPIDER_QUEEN.get(), ctx -> new RpgGeoRenderer(ctx, "spider_queen", 2.2f));
        e.registerEntityRenderer(ModEntities.SPIDER_EGG.get(), ctx -> new RpgGeoRenderer(ctx, "spider_egg", 0.6f));
        e.registerEntityRenderer(ModEntities.WEB_COCOON.get(), ctx -> new RpgGeoRenderer(ctx, "web_cocoon", 0.5f));
        // 보스 숲의 주인: GeckoLib 모델 1.8배, 결정·균열·눈 발광
        e.registerEntityRenderer(ModEntities.FOREST_LORD.get(), ctx -> new RpgGeoRenderer(ctx, "forest_lord", 2.5f, true));
        // 보스 패턴 소품
        e.registerEntityRenderer(ModEntities.BOSS_ROCK.get(), ctx -> new PropRenderer(ctx, "boss_rock", 2.2f));
        e.registerEntityRenderer(ModEntities.ROCK_PILLAR.get(), ctx -> new PropRenderer(ctx, "rock_pillar", 2.2f));
        e.registerEntityRenderer(ModEntities.VOID_CRYSTAL.get(), ctx -> new PropRenderer(ctx, "void_crystal", 1.6f));
        e.registerEntityRenderer(ModEntities.MAGIC_CIRCLE.get(), ctx -> new PropRenderer(ctx, "magic_circle", 1.0f));
        e.registerEntityRenderer(ModEntities.VOID_BEAM.get(), ctx -> new PropRenderer(ctx, "void_beam", 1.0f));
        e.registerEntityRenderer(ModEntities.SPORE_BEAM.get(), ctx -> new PropRenderer(ctx, "spore_beam", 1.0f));
        e.registerEntityRenderer(ModEntities.SPORE_BALL.get(), ctx -> new PropRenderer(ctx, "spore_ball", 1.6f));
        e.registerEntityRenderer(ModEntities.POISON_POOL.get(), ctx -> new PropRenderer(ctx, "poison_pool", 1.0f));
        e.registerEntityRenderer(ModEntities.VOID_CRACK.get(), ctx -> new PropRenderer(ctx, "void_crack", 1.0f));
        e.registerEntityRenderer(ModEntities.RESONANCE_RING.get(), ctx -> new PropRenderer(ctx, "resonance_ring", 1.0f));
        e.registerEntityRenderer(ModEntities.SHOCKWAVE.get(), ctx -> new PropRenderer(ctx, "shockwave", 1.0f));
        e.registerEntityRenderer(ModEntities.HARD_MUSHROOM.get(), ctx -> new PropRenderer(ctx, "hard_mushroom", 1.0f));
        e.registerEntityRenderer(ModEntities.VOID_PILLAR.get(), ctx -> new RpgGeoRenderer(ctx, "void_pillar", 0.9f, true));
        e.registerEntityRenderer(ModEntities.WEB_PILLAR.get(), ctx -> new RpgGeoRenderer(ctx, "web_pillar", 0.6f));
    }

    /** 직업 가죽 방어구: 아이콘 색 (아래층만 칠함, 위 오버레이는 그대로) */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void itemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item e) {
        for (var ro : rpg.core.item.ModItems.GEAR) {
            if (ro.get() instanceof rpg.core.equip.GearItems.DyedArmor d)
                e.register((stack, tint) -> tint == 0 ? d.color() : -1, d);
        }
        // 주문서: 등급별 색 (아래층만)
        e.register((stack, tint) -> tint == 0 ? rpg.core.item.Scroll.color(stack) : -1, rpg.core.item.ModItems.SCROLL.get());
    }
}
