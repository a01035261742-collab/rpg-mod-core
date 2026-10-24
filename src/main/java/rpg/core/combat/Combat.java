package rpg.core.combat;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.equip.Gear;
import rpg.core.equip.GearDefs;
import rpg.core.equip.PlayerEquip;

/**
 * 전투 공식
 *  스공 = (주스탯×4 + 부스탯) × 공격력 / 100  (마법사: 마력)
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

    /** 무기 칸 무기의 공격력(마법사는 마력) + 장비 옵션 공격력/마력. 무기 없으면 맨손 */
    public static double weaponAtk(Player p, PlayerData d) {
        String key = d.job == 3 ? "matk" : "atk";
        ItemStack s = PlayerEquip.weapon(p);
        GearDefs.GearDef w = Gear.def(s);
        boolean has = w != null && Gear.canEquip(p, s);
        double base = has ? 0 : Balance.fistAttack;
        return base + d.eq(key);
    }

    /** 직업별 주·부 스탯 (초보자·전사: 힘/민첩, 궁수: 민첩/힘, 마법사: 지력/운, 도적: 운/민첩) */
    public static double mainStat(PlayerData d) {
        return switch (d.job) {
            case 2 -> d.total(PlayerData.DEX);
            case 3 -> d.total(PlayerData.INT);
            case 4 -> d.total(PlayerData.LUK);
            default -> d.total(PlayerData.STR);
        };
    }

    public static double subStat(PlayerData d) {
        return switch (d.job) {
            case 2 -> d.total(PlayerData.STR);
            case 3 -> d.total(PlayerData.LUK);
            case 4 -> d.total(PlayerData.DEX);
            default -> d.total(PlayerData.DEX);
        };
    }

    public static double statAttack(Player p, PlayerData d) {
        // 메이플식 (C안): 공격력 × (1 + 공%) → 스탯 공식 → × (1 + 데미지%). 보스% 는 보스한테 데미지%와 더해짐 (Boss.onHurt)
        return (mainStat(d) * 4.0 + subStat(d)) * weaponAtk(p, d) * (1 + d.bonus("atkPct") / 100.0) / 100.0 * (1 + dmgPct(d) / 100.0);
    }

    /** 데미지% 합: 장비·패시브 데미지% + 광폭화 */
    public static double dmgPct(PlayerData d) {
        return d.bonus("dmgPct") + d.buffV("berserk", d.clock) + rpg.core.skill.PassiveEffects.extraDmg(d);
    }

    /** 숙련도 (최소 데미지 = 최대 × 숙련도): 직업 기본값 + 마스터리 스킬 (최대 95%) */
    public static double mastery(PlayerData d) {
        double base = Balance.masteryByJob[Math.max(0, Math.min(4, d.job))];
        return Math.min(0.95, base + d.bonus("mastery") / 100.0);
    }

    public static Hit roll(Player p, PlayerData d, double mult) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double max = statAttack(p, d);
        double min = max * mastery(d);
        double dmg = (min + r.nextDouble() * (max - min)) * mult;
        boolean crit = r.nextDouble() * 100.0 < d.crit();
        if (crit) dmg *= d.critDamage();
        return new Hit(Math.max(1, Math.round(dmg)), crit);
    }

    public static double defense(PlayerData d) {
        return (d.total(PlayerData.VIT) * Balance.defPerVit + d.bonus("def") + d.eq("def") + d.buffV("iron_body", d.clock))
                * (1 + (d.eq("defPct") + d.bonus("defPct") + rpg.core.skill.PassiveEffects.extraDef(d)) / 100.0);
    }

    /**
     * 방어 비율: 방어 / (방어 + defBase + 몹 레벨 × defPerMobLevel).
     * 0 은 안 됨 (방어가 아무리 높아도 조금은 아픔), 레벨 높은 몹일수록 방어가 덜 먹힘
     */
    public static double defRatio(PlayerData d, int mobLevel) {
        double def = Math.max(0, defense(d));
        return def / (def + Balance.defBase + Math.max(1, mobLevel) * Balance.defPerMobLevel);
    }

    /** 몹 → 플레이어 피해 */
    public static double mobToPlayer(double mobAtk, int mobLevel, PlayerData d) {
        return Math.max(1, mobAtk * (1 - defRatio(d, mobLevel)));
    }
}
