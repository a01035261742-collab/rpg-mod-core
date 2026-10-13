package rpg.core.job;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.equip.Gear;
import rpg.core.item.ModItems;

/** 1차 전직 (전직 교관 NPC) */
public final class Jobs {
    private Jobs() {}

    public static final String NPC_TAG = "rpgJobNpc";   // 엔티티 persistentData 에 계열 번호(1~4)

    public static String name(int job) {
        return switch (job) {
            case 1 -> "전사";
            case 2 -> "궁수";
            case 3 -> "마법사";
            case 4 -> "도적";
            default -> "초보자";
        };
    }

    public static int npcFamily(Entity e) {
        return e.getPersistentData().getInt(NPC_TAG);
    }

    /** 근처(8칸)에 그 계열 교관이 있나 */
    public static boolean nearInstructor(ServerPlayer p, int family) {
        return !p.level().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(8), v -> npcFamily(v) == family).isEmpty();
    }

    /** 전직 시도. 실패하면 이유 문자열 */
    public static String advance(ServerPlayer p, int family) {
        if (family < 1 || family > 4) return "잘못된 직업";
        PlayerData d = RpgData.get(p);
        if (d.job != 0) return "이미 " + name(d.job) + "예요";
        if (d.level < Balance.jobLevel1) return "Lv." + Balance.jobLevel1 + "이 되면 전직할 수 있어요";
        if (!nearInstructor(p, family)) return name(family) + " 교관 가까이에서 전직할 수 있어요";
        d.job = family;
        d.recalc();
        // 보상: 직업 무기(Lv10) · 스탯 초기화 주문서 · 물약
        String weapon = switch (family) {
            case 1 -> "warrior_sword_10";
            case 2 -> "archer_bow_10";
            case 3 -> "mage_staff_10";
            default -> "thief_dagger_10";
        };
        for (var g : ModItems.GEAR) {
            if (((Gear.GearItem) g.get()).gearId().equals(weapon)) give(p, g.get().getDefaultInstance());
        }
        give(p, new ItemStack(ModItems.STAT_RESET_SCROLL.get()));
        give(p, new ItemStack(ModItems.RED_POTION.get(), Balance.jobRewardPotions));
        give(p, new ItemStack(ModItems.BLUE_POTION.get(), Balance.jobRewardPotions));
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 60, 0.5, 1, 0.5, 0.4);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        p.sendSystemMessage(Component.literal("전직 완료! 이제 " + name(family) + "예요. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("직업 무기·스탯 초기화 주문서·물약을 받았어요").withStyle(ChatFormatting.YELLOW)));
        RpgData.sync(p);
        return null;
    }

    private static void give(ServerPlayer p, ItemStack s) {
        Gear.roll(s);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }
}
