package rpg.core.client;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.RpgCore;

/** Epic Fight 설정에 우리 무기를 "전투용"으로 넣음 (이미 저장된 EF 설정에도 적용) → 블록 조준·캐기 대신 공격 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class EfClientCompat {
    private EfClientCompat() {}

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn e) {
        try {
            Class<?> cfg = Class.forName("yesman.epicfight.config.ClientConfig");
            Set<Item> combat = (Set<Item>) cfg.getField("combatPreferredItems").get(null);
            Set<Item> mining = (Set<Item>) cfg.getField("miningPreferredItems").get(null);
            if (combat == null) return;
            for (Item it : ForgeRegistries.ITEMS.getValues()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
                if (id == null || !RpgCore.MODID.equals(id.getNamespace())) continue;
                String n = id.getPath();
                if (n.startsWith("warrior_sword") || n.startsWith("archer_bow") || n.startsWith("mage_staff") || n.startsWith("thief_dagger")) {
                    combat.add(it);
                    if (mining != null) mining.remove(it);
                }
            }
        } catch (Throwable ex) {
            RpgCore.LOG.debug("[rpgcore] EF 무기 설정 실패: {}", ex.toString());
        }
    }
}
