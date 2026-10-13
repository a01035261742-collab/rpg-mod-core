package rpg.core;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import rpg.core.item.ModItems;
import rpg.core.net.Net;

/**
 * RPG Mod Core (Forge 1.20.1)
 *
 * 구조
 *  data/     플레이어 RPG 데이터 (저장·동기화)
 *  balance/  수치 파일 (data/rpgcore/rpg_balance/*.json, /reload 로 반영)
 *  net/      서버↔클라이언트 패킷
 *  item/     아이템
 *  cmd/      관리자 명령어 /rpg
 *  client/   HUD, 스탯 창, 키 (클라이언트 전용)
 */
@Mod(RpgCore.MODID)
public class RpgCore {
    public static final String MODID = "rpgcore";
    public static final Logger LOG = LogUtils.getLogger();

    public RpgCore() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        rpg.core.equip.EquipMenu.MENUS.register(modBus);
        rpg.core.craft.GoldShapedRecipe.SERIALIZERS.register(modBus);
        rpg.core.entity.ModEntities.TYPES.register(modBus);
        Net.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
