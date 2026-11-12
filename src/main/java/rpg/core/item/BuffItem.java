package rpg.core.item;

import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 버프 아이템 (물약 상인): 우클릭 → 10분 동안 고정 수치 버프 (% 없음). 다시 먹으면 시간만 처음부터.
 *  효과 반영: 공격력·마력 = Combat.weaponAtk, 방어력 = Combat.defense, 모든 스탯 = PlayerData.total,
 *  최대 HP = PlayerData.recalc, 5초 회복 = PlayerEvents 틱. (버프는 저장 안 함 → 다시 접속하면 사라짐) */
public class BuffItem extends Item {
    public record Def(String buff, String name, String desc, double val, boolean food) {}

    /** 아이템 id → 버프 */
    public static final Map<String, Def> DEFS = Map.of(
            "power_potion", new Def("item_atk", "힘의 물약", "공격력 +10", 10, false),
            "magic_potion", new Def("item_matk", "마력의 물약", "마력 +10", 10, false),
            "guard_potion", new Def("item_def", "수호의 물약", "방어력 +30", 30, false),
            "luck_cookie", new Def("item_stat", "행운 쿠키", "모든 스탯 +3", 3, true),
            "life_candy", new Def("item_hp", "생명의 사탕", "최대 HP +200", 200, true),
            "red_apple", new Def("item_regen", "사과", "5초마다 HP +20", 20, true));
    /** 버프 id → HUD 이름 */
    public static final Map<String, String> NAMES = new java.util.HashMap<>();
    static { DEFS.values().forEach(d -> NAMES.put(d.buff(), d.name())); }

    public static final int TICKS = 20 * 600;   // 10분

    private final String id;

    public BuffItem(Properties p, String id) {
        super(p.stacksTo(100));
        this.id = id;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        Def def = DEFS.get(id);
        PlayerData d = RpgData.get(sp);
        if (def == null || d.isDown()) return InteractionResultHolder.fail(stack);
        d.buffEnd.put(def.buff(), d.clock + TICKS);
        d.buffVal.put(def.buff(), def.val());
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, sp.blockPosition(), def.food() ? SoundEvents.GENERIC_EAT : SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6f, 1.1f);
        sp.displayClientMessage(Component.literal(def.name() + ": " + def.desc() + " (10분)").withStyle(ChatFormatting.GREEN), true);
        d.recalc();
        RpgData.sync(sp);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        Def def = DEFS.get(id);
        if (def == null) return;
        tip.add(Component.literal("우클릭: " + def.desc()).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("10분 지속 · 다시 쓰면 시간이 처음부터").withStyle(ChatFormatting.DARK_GRAY));
    }
}
