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

/** 1차·2차 전직 (전직 교관 NPC) */
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

    /** 2차 갈래 이름 (계열, 1·2) */
    public static String branchName(int job, int branch) {
        return switch (job * 10 + branch) {
            case 11 -> "가디언";
            case 12 -> "버서커";
            case 21 -> "스나이퍼";
            case 22 -> "헌터";
            case 31 -> "아크메이지";
            case 32 -> "클레릭";
            case 41 -> "어쌔신";
            case 42 -> "트릭스터";
            default -> name(job);
        };
    }

    /** 갈래 역할 한 줄 */
    public static String branchRole(int job, int branch) {
        return switch (job * 10 + branch) {
            case 11 -> "탱커 · 방패와 검";
            case 12 -> "딜러 · 대검";
            case 21 -> "딜러 · 명사수";
            case 22 -> "제어 · 석궁";
            case 31 -> "딜러 · 원소 마법";
            case 32 -> "서포터 · 치유";
            case 41 -> "딜러 · 암살";
            case 42 -> "서포터 · 책략";
            default -> "";
        };
    }

    /** 교관 창 상태: 0 1차 전직 가능 · 1 레벨 부족 · 2 2차까지 끝 · 3 다른 직업 · 4 2차 레벨 부족 · 5 갈래 고르기 · 6 시련 중 · 7 시련 통과 */
    public static int state(PlayerData d, int fam) {
        if (d.job == 0) return d.level < Balance.jobLevel1 ? 1 : 0;
        if (d.job != fam) return 3;
        if (d.branch > 0) return 2;
        if (d.level < Balance.jobLevel2) return 4;
        if (d.trialCleared) return 7;
        return d.pendingBranch > 0 ? 6 : 5;
    }

    /** 교관 창 버튼: 0 1차 전직 · 1 갈래 고르기(arg) · 2 갈래 포기 · 3 시련 시작 · 4 2차 전직 완료. 실패하면 이유 */
    public static String action(ServerPlayer p, int family, int action, int arg) {
        return switch (action) {
            case 0 -> advance(p, family);
            case 1 -> choose(p, family, arg);
            case 2 -> abandon(p, family);
            case 3 -> Trials.start(p, family);
            case 4 -> complete(p, family);
            default -> "잘못된 요청";
        };
    }

    private static String check2(ServerPlayer p, PlayerData d, int family) {
        if (d.job != family) return "이 교관에게는 2차 전직을 할 수 없어요";
        if (d.branch > 0) return "이미 " + branchName(d.job, d.branch) + "예요";
        if (d.level < Balance.jobLevel2) return "Lv." + Balance.jobLevel2 + "이 되면 2차 전직을 할 수 있어요";
        if (!nearInstructor(p, family)) return name(family) + " 교관 가까이에서 할 수 있어요";
        if (Trials.inRun(p)) return "시련 중에는 할 수 없어요";
        return null;
    }

    private static String choose(ServerPlayer p, int family, int b) {
        PlayerData d = RpgData.get(p);
        String err = check2(p, d, family);
        if (err != null) return err;
        if (b < 1 || b > 2) return "잘못된 갈래";
        if (d.trialCleared) return "시련을 통과한 뒤에는 갈래를 바꿀 수 없어요";
        d.pendingBranch = b;
        p.sendSystemMessage(Component.literal("[전직] " + branchName(family, b) + "의 길을 골랐어요. 교관의 시련을 통과하면 전직할 수 있어요")
                .withStyle(ChatFormatting.YELLOW));
        RpgData.sync(p);
        return null;
    }

    private static String abandon(ServerPlayer p, int family) {
        PlayerData d = RpgData.get(p);
        String err = check2(p, d, family);
        if (err != null) return err;
        if (d.trialCleared) return "시련을 통과한 뒤에는 포기할 수 없어요";
        d.pendingBranch = 0;
        p.sendSystemMessage(Component.literal("[전직] 갈래 선택을 취소했어요").withStyle(ChatFormatting.GRAY));
        RpgData.sync(p);
        return null;
    }

    /** 2차 전직 완료: 갈래 확정 + 보상 (직업 무기 Lv30 · 스탯 초기화 주문서 · 물약) */
    private static String complete(ServerPlayer p, int family) {
        PlayerData d = RpgData.get(p);
        String err = check2(p, d, family);
        if (err != null) return err;
        if (d.pendingBranch == 0 || !d.trialCleared) return "교관의 시련을 먼저 통과해야 해요";
        d.branch = d.pendingBranch;
        d.pendingBranch = 0;
        d.trialCleared = false;
        d.recalc();
        String weapon = switch (family) {
            case 1 -> "warrior_sword_30";
            case 2 -> "archer_bow_30";
            case 3 -> "mage_staff_30";
            default -> "thief_dagger_30";
        };
        for (var g : ModItems.GEAR) {
            if (((Gear.GearItem) g.get()).gearId().equals(weapon)) give(p, g.get().getDefaultInstance());
        }
        give(p, new ItemStack(ModItems.STAT_RESET_SCROLL.get()));
        give(p, new ItemStack(ModItems.ORANGE_POTION.get(), Balance.jobReward2Potions));
        give(p, new ItemStack(ModItems.DEEP_BLUE_POTION.get(), Balance.jobReward2Potions));
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 80, 0.5, 1, 0.5, 0.5);
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1f);
        d.freeTreeReset += 1;   // 2차 전직: 무료 트리 초기화 1회
        p.sendSystemMessage(Component.literal("2차 전직 완료! 이제 " + branchName(family, d.branch) + "예요. ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("스킬 창(L)에서 새 스킬을 배울 수 있어요").withStyle(ChatFormatting.YELLOW)));
        RpgData.sync(p);
        return null;
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
