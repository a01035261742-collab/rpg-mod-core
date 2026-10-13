package rpg.core.data;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import rpg.core.balance.Balance;

/** 경험치·레벨업·골드·스탯 분배 */
public final class Progress {
    private Progress() {}

    public static void giveExp(ServerPlayer p, long amount) {
        PlayerData d = RpgData.get(p);
        if (d.level >= Balance.maxLevel || amount <= 0) return;
        d.exp += amount;
        int ups = 0;
        while (d.level < Balance.maxLevel && d.exp >= Balance.expNeed(d.level)) {
            d.exp -= Balance.expNeed(d.level);
            d.level++;
            d.statPoints += Balance.statPointsPerLevel;
            d.skillPoints += Balance.skillPointsPerLevel;
            d.passivePoints += Balance.passivePointsPerLevel;
            ups++;
        }
        if (d.level >= Balance.maxLevel) d.exp = 0;
        if (ups > 0) {
            d.recalc();
            d.hp = d.maxHp;
            d.mp = d.maxMp;
            levelUpFx(p, d.level);
        }
        RpgData.sync(p);
    }

    /** 레벨 지정 (테스트용): 스탯은 처음으로, 포인트는 레벨에 맞게 */
    public static void setLevel(ServerPlayer p, int level) {
        PlayerData d = RpgData.get(p);
        d.level = Math.max(1, Math.min(level, Balance.maxLevel));
        d.exp = 0;
        for (int i = 0; i < PlayerData.N; i++) d.stats[i] = PlayerData.start(i);
        d.statPoints = (d.level - 1) * Balance.statPointsPerLevel;
        d.skillPoints = (d.level - 1) * Balance.skillPointsPerLevel;
        d.passivePoints = (d.level - 1) * Balance.passivePointsPerLevel;
        d.skills.clear(); d.passives.clear(); java.util.Arrays.fill(d.slots, null);
        d.recalc();
        d.hp = d.maxHp; d.mp = d.maxMp;
        RpgData.sync(p);
    }

    public static void allocStat(ServerPlayer p, int stat, int amount) {
        if (stat < 0 || stat >= PlayerData.N) return;
        PlayerData d = RpgData.get(p);
        int n = Math.min(Math.max(0, amount), d.statPoints);
        if (n <= 0) return;
        d.stats[stat] += n;
        d.statPoints -= n;
        d.recalc();
        RpgData.sync(p);
    }

    private static void levelUpFx(ServerPlayer p, int level) {
        ServerLevel lv = p.serverLevel();
        lv.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        lv.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
        p.sendSystemMessage(Component.literal("LEVEL UP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("Lv." + level + "  (스탯 +" + Balance.statPointsPerLevel + " K · 스킬 +" + Balance.skillPointsPerLevel + " L · 패시브 +" + Balance.passivePointsPerLevel + " N)")
                        .withStyle(ChatFormatting.YELLOW)));
    }
}
