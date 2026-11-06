package rpg.core.item;

import java.util.List;
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
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 물약: 우클릭 즉시 회복. 종류·회복량·쿨타임은 player.json 의 potions (HP 고정 / MP 고정 / 최대치 %) */
public class Potion extends Item {
    private final String id;

    public Potion(Properties p, String id) {
        super(p.stacksTo(100));
        this.id = id;
    }

    private Balance.PotionDef def() {
        return Balance.POTIONS.getOrDefault(id, new Balance.PotionDef(0, 0, 0, 0, 0));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        return drink(sp, stack) ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    /** 마시기 (손에 든 것 · 물약 칸 키 둘 다): 마셨으면 true. 쿨타임은 물약 전부 같이 */
    public boolean drink(ServerPlayer sp, ItemStack stack) {
        Level level = sp.level();
        PlayerData d = RpgData.get(sp);
        if (d.isDown()) return false;
        Balance.PotionDef p = def();
        boolean heals = p.hp() > 0 || p.hpPct() > 0, mana = p.mp() > 0 || p.mpPct() > 0;
        boolean hpFull = d.hp >= d.maxHp, mpFull = d.mp >= d.maxMp;
        if ((!heals || hpFull) && (!mana || mpFull)) {
            String what = heals && mana ? "HP·MP가" : heals ? "HP가" : "MP가";
            sp.displayClientMessage(Component.literal(what + " 가득 차 있어요").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        double mul = 1 + d.bonus("healPct") / 100.0;
        if (heals) d.hp = Math.min(d.maxHp, d.hp + (p.hp() + d.maxHp * p.hpPct() / 100.0) * mul);
        if (mana) d.mp = Math.min(d.maxMp, d.mp + (p.mp() + d.maxMp * p.mpPct() / 100.0) * mul);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        double cd = p.cooldown() > 0 ? p.cooldown() : Balance.potionCooldown;
        if (rpg.core.combat.Boss.inArena(sp)) cd = Math.max(cd, Balance.bossPotionCooldown);   // 보스전: 물약 쿨 5초
        int ticks = (int) Math.round(cd * 20);
        for (Item it : net.minecraftforge.registries.ForgeRegistries.ITEMS) if (it instanceof Potion) sp.getCooldowns().addCooldown(it, ticks);
        level.playSound(null, sp.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6f, 1.2f);
        RpgData.sync(sp);
        return true;
    }

    /** 가방 정렬 (핫바 0번 = 무기 칸은 그대로): 같은 물건 합치고 물약 먼저 · 이름순 */
    public static void sortBag(ServerPlayer p) {
        var inv = p.getInventory().items;
        java.util.List<ItemStack> merged = new java.util.ArrayList<>();
        for (int i = 1; i < 36; i++) {
            ItemStack s = inv.get(i);
            inv.set(i, ItemStack.EMPTY);
            if (s.isEmpty()) continue;
            for (ItemStack m : merged) {
                if (s.isEmpty()) break;
                if (ItemStack.isSameItemSameTags(m, s) && m.getCount() < m.getMaxStackSize()) {
                    int mv = Math.min(s.getCount(), m.getMaxStackSize() - m.getCount());
                    m.grow(mv); s.shrink(mv);
                }
            }
            if (!s.isEmpty()) merged.add(s);
        }
        merged.sort(java.util.Comparator.comparing((ItemStack s) -> !(s.getItem() instanceof Potion))
                .thenComparing(s -> String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem()))));
        int[] order = new int[35];
        for (int i = 0; i < 27; i++) order[i] = 9 + i;
        for (int i = 0; i < 8; i++) order[27 + i] = 1 + i;
        for (int i = 0; i < merged.size() && i < 35; i++) inv.set(order[i], merged.get(i));
        p.getInventory().setChanged();
        p.containerMenu.broadcastChanges();
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        Balance.PotionDef p = def();
        List<String> parts = new java.util.ArrayList<>();
        if (p.hp() > 0) parts.add("HP " + (long) p.hp());
        if (p.hpPct() > 0) parts.add("HP " + fmt(p.hpPct()) + "%");
        if (p.mp() > 0) parts.add("MP " + (long) p.mp());
        if (p.mpPct() > 0) parts.add("MP " + fmt(p.mpPct()) + "%");
        tip.add(Component.literal("우클릭: " + String.join(" · ", parts) + " 회복").withStyle(ChatFormatting.GRAY));
        if (p.cooldown() > 0) tip.add(Component.literal("쿨타임 " + fmt(p.cooldown()) + "초").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
