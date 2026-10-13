package rpg.core.craft;

import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import rpg.core.RpgCore;
import rpg.core.data.RpgData;

/**
 * 작업대 모양 조합 + 골드 수수료. 골드가 모자라면 결과가 안 나옴.
 * 골드는 결과를 꺼낼 때 CraftEvents 에서 뺌.
 */
public class GoldShapedRecipe extends ShapedRecipe {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, RpgCore.MODID);
    public static final RegistryObject<RecipeSerializer<?>> SERIALIZER = SERIALIZERS.register("gold_shaped", Serializer::new);

    public final long gold;

    public GoldShapedRecipe(ShapedRecipe base, long gold) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(),
                base.getResultItem(null), base.showNotification());
        this.gold = gold;
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        if (!super.matches(inv, level)) return false;
        if (level.isClientSide || gold <= 0) return true;
        Player p = playerOf(inv);
        if (!(p instanceof ServerPlayer sp)) return true;
        boolean ok = RpgData.get(sp).gold >= gold;
        sp.displayClientMessage(Component.literal(ok ? "제작 수수료 " + String.format("%,d", gold) + " G"
                : "골드 부족: 제작 수수료 " + String.format("%,d", gold) + " G 필요").withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED), true);
        return ok;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    // ───────── 조합 칸 → 플레이어 (이름 대신 타입으로 필드 찾기) ─────────

    public static Player playerOf(CraftingContainer inv) {
        try {
            Object menu = fieldOfType(inv, AbstractContainerMenu.class);
            return menu == null ? null : (Player) fieldOfType(menu, Player.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object fieldOfType(Object o, Class<?> type) throws IllegalAccessException {
        Class<?> key = o.getClass();
        String k = key.getName() + "|" + type.getName();
        Field f = FIELDS.get(k);
        if (f == null && !FIELDS.containsKey(k)) {
            outer:
            for (Class<?> c = key; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field x : c.getDeclaredFields()) {
                    if (type.isAssignableFrom(x.getType())) {
                        x.setAccessible(true);
                        f = x;
                        break outer;
                    }
                }
            }
            FIELDS.put(k, f);
        }
        return f == null ? null : f.get(o);
    }

    private static final Map<String, Field> FIELDS = new HashMap<>();

    public static class Serializer implements RecipeSerializer<GoldShapedRecipe> {
        @Override
        public GoldShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromJson(id, json);
            return new GoldShapedRecipe(base, json.has("gold") ? json.get("gold").getAsLong() : 0);
        }

        @Override
        public GoldShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf b) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, b);
            return new GoldShapedRecipe(base, b.readVarLong());
        }

        @Override
        public void toNetwork(FriendlyByteBuf b, GoldShapedRecipe r) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(b, r);
            b.writeVarLong(r.gold);
        }
    }
}
