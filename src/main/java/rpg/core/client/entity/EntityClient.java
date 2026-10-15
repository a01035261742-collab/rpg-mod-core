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
        e.registerLayerDefinition(FAIRY, MobModels::mushFairy);
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
        reg(e, ModEntities.MUSH_FAIRY.get(), FAIRY, "mush_fairy", 0.25f);
        reg(e, ModEntities.MUSH_BEAST.get(), BEAST, "mush_beast", 1.2f);
        ResourceLocation bear = RpgCore.id("textures/entity/brown_bear.png");
        e.registerEntityRenderer(ModEntities.BROWN_BEAR.get(), ctx -> new PolarBearRenderer(ctx) {
            @Override
            public ResourceLocation getTextureLocation(PolarBear ent) { return bear; }
        });
    }

    /** 직업 가죽 방어구: 아이콘 색 (아래층만 칠함, 위 오버레이는 그대로) */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void itemColors(net.minecraftforge.client.event.RegisterColorHandlersEvent.Item e) {
        for (var ro : rpg.core.item.ModItems.GEAR) {
            if (ro.get() instanceof rpg.core.equip.GearItems.DyedArmor d)
                e.register((stack, tint) -> tint == 0 ? d.color() : -1, d);
        }
    }
}
