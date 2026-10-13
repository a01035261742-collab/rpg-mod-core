package rpg.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/** 클라이언트: 키, HUD 등록, 바닐라 하트·배고픔·경험치 숨기기 */
public final class ClientEvents {
    private ClientEvents() {}

    public static final String CATEGORY = "key.categories.rpgcore";
    public static final KeyMapping STAT_KEY = new KeyMapping("key.rpgcore.stats", InputConstants.KEY_K, CATEGORY);

    @Mod.EventBusSubscriber(modid = RpgCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(STAT_KEY);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rpg_hud", RpgHud::render);
        }
    }

    @Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void hideVanilla(RenderGuiOverlayEvent.Pre e) {
            NamedGuiOverlay o = e.getOverlay();
            if (o == VanillaGuiOverlay.PLAYER_HEALTH.type()
                    || o == VanillaGuiOverlay.FOOD_LEVEL.type()
                    || o == VanillaGuiOverlay.ARMOR_LEVEL.type()
                    || o == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                    || o == VanillaGuiOverlay.AIR_LEVEL.type()) {
                e.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (STAT_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) mc.setScreen(new StatScreen());
            }
        }
    }
}
