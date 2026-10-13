package rpg.core.combat;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.item.RpgWeapon;

/**
 * 전투 공식
 *  스공 = (주스탯×4 + 부스탯) × 무기 공격력 / 100
 *  데미지 = 스공 × 배율 × (숙련도~100% 랜덤), 치명타면 × 치명타 피해
 *  받는 피해 = 몹 공격력 × (1 - 방어/(방어+기준값)),  방어 = 체력 × defPerVit
 */
public final class Combat {
    private Combat() {}

    public record Hit(double amount, boolean crit) {}

    public static MobData mob(LivingEntity e) {
        return e.getCapability(MobData.Provider.CAP).orElse(null);
    }

    public static boolean isRpgMob(LivingEntity e) {
        MobData m = mob(e);
        return m != null && m.isRpg();
    }

    public static double weaponAtk(Player p) {
        ItemStack s = p.getMainHandItem();
        if (s.getItem() instanceof RpgWeapon w) return w.atk;
        return Balance.fistAttack;
    }

    /** 직업별 주·부 스탯 (초보자·전사: 힘/민첩, 궁수: 민첩/힘, 마법사: 지력/운, 도적: 운/민첩) */
    public static int mainStat(PlayerData d) {
        return switch (d.job) {
            case 2 -> d.stats[PlayerData.DEX];
            case 3 -> d.stats[PlayerData.INT];
            case 4 -> d.stats[PlayerData.LUK];
            default -> d.stats[PlayerData.STR];
        };
    }

    public static int subStat(PlayerData d) {
        return switch (d.job) {
            case 2 -> d.stats[PlayerData.STR];
            case 3 -> d.stats[PlayerData.LUK];
            case 4 -> d.stats[PlayerData.DEX];
            default -> d.stats[PlayerData.DEX];
        };
    }

    public static double statAttack(Player p, PlayerData d) {
        return (mainStat(d) * 4.0 + subStat(d)) * weaponAtk(p) / 100.0 * (1 + d.bonus("atkPct") / 100.0);
    }

    public static Hit roll(Player p, PlayerData d, double mult) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double max = statAttack(p, d);
        double min = max * Balance.mastery;
        double dmg = (min + r.nextDouble() * (max - min)) * mult;
        boolean crit = r.nextDouble() * 100.0 < d.crit();
        if (crit) dmg *= d.critDamage();
        return new Hit(Math.max(1, Math.round(dmg)), crit);
    }

    public static double defense(PlayerData d) {
        return d.stats[PlayerData.VIT] * Balance.defPerVit + d.bonus("def");
    }

    /** 몹 → 플레이어 피해 */
    public static double mobToPlayer(double mobAtk, PlayerData d) {
        double def = defense(d);
        return Math.max(1, mobAtk * (1.0 - def / (def + Balance.defConstant)));
    }

    /** 방어로 줄어드는 비율 (%) — 상세 창 표시용 */
    public static double defReducePct(PlayerData d) {
        double def = defense(d);
        return def / (def + Balance.defConstant) * 100.0;
    }
}
