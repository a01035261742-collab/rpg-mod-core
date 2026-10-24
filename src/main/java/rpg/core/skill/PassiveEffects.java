package rpg.core.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.combat.Boss;
import rpg.core.combat.Combat;
import rpg.core.combat.CombatEvents;
import rpg.core.combat.MobData;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 패시브 트리 특수 효과 (stat 이름으로 PlayerData.bonus 에 들어온 값을 여기서 씀).
 * 처치: killHeal huntSpeed slayer rageHeal · 피격: thorns crisisShield frenzy · 타격: shatter mobPct
 * 매초: bloodHeat·frenzy(공속) · guardInstinct · intimidate · tauntDmg · warcry · oath (주변 파티 오라)
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class PassiveEffects {
    private PassiveEffects() {}

    // ───────── 처치 ─────────
    public static void onKill(ServerPlayer p, PlayerData d) {
        long now = p.level().getGameTime();
        if (d.bonus("killHeal") > 0) d.hp = Math.min(d.maxHp, d.hp + d.maxHp * d.bonus("killHeal") / 100.0);   // 전투 회복
        if (d.bonus("huntSpeed") > 0) { d.buffEnd.put("hunt_speed", now + 60); d.buffVal.put("hunt_speed", d.bonus("huntSpeed")); }   // 사냥 본능
        if (d.bonus("slayer") > 0) {   // 학살자: 4초, 최대 3중첩
            int st = d.buff("slayer", now) ? (int) Math.round(d.buffVal2.getOrDefault("slayer", 0.0)) : 0;
            st = Math.min(3, st + 1);
            d.buffEnd.put("slayer", now + 80);
            d.buffVal.put("slayer", d.bonus("slayer") * st);
            d.buffVal2.put("slayer", (double) st);
        }
        if (d.bonus("rageHeal") > 0 && d.buff("berserk", now))   // 광폭 체질
            d.hp = Math.min(d.maxHp, d.hp + d.maxHp * d.bonus("rageHeal") / 100.0);
    }

    // ───────── 타격 (몹에게 준 피해 배율) ─────────
    /** 일반 몹 데미지: 데미지%와 더해짐 */
    public static double hitMul(ServerPlayer p, PlayerData d, Mob mob, MobData m, boolean crit) {
        double mul = 1;
        if (!Boss.isBoss(m) && d.bonus("mobPct") > 0) {
            double dp = Combat.dmgPct(d);
            mul *= (1 + (dp + d.bonus("mobPct")) / 100.0) / (1 + dp / 100.0);
        }
        // 분쇄: 크리 시 일반 몹 경직 0.3초 (같은 몹 1초에 1번)
        if (crit && d.bonus("shatter") > 0 && !Boss.isBoss(m)) {
            long now = mob.level().getGameTime();
            if (now >= mob.getPersistentData().getLong("rpgShatterAt")) {
                mob.getPersistentData().putLong("rpgShatterAt", now + 20);
                m.stunUntil = Math.max(m.stunUntil, now + 6);
                ((ServerLevel) mob.level()).sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 6, 0.3, 0.1, 0.3, 0.1);
            }
        }
        return mul;
    }

    // ───────── 피격 ─────────
    /** 받는 피해 배율 (광란 +10%) */
    public static double takenMul(PlayerData d) {
        return d.bonus("frenzy") > 0 ? 1.10 : 1;
    }

    /** 맞은 뒤: 가시 갑옷 반사 · 위기 회복 보호막 */
    public static void afterHit(ServerPlayer p, PlayerData d, net.minecraft.world.entity.Entity src, net.minecraft.world.entity.Entity direct) {
        long now = p.level().getGameTime();
        if (d.bonus("thorns") > 0 && src instanceof Mob m && direct == m && Combat.isRpgMob(m))
            CombatEvents.fixedHit(p, m, Math.max(1, Combat.defense(d) * d.bonus("thorns") / 100.0));
        if (d.bonus("crisisShield") > 0 && d.hp > 0 && d.hp < d.maxHp * 0.3 && now >= d.crisisReadyAt) {
            d.crisisReadyAt = now + 1200;   // 60초
            d.shield = Math.max(now < d.shieldEnd ? d.shield : 0, d.maxHp * d.bonus("crisisShield") / 100.0);
            d.shieldEnd = now + 100;        // 5초
            p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
        }
    }

    // ───────── 매초: 공속 · 오라 ─────────
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        PlayerData d = RpgData.get(p);
        long now = p.level().getGameTime();
        ServerLevel l = p.serverLevel();
        // 공격 속도: 광란 + 피의 열기 (HP가 낮을수록)
        double hpR = d.maxHp <= 0 ? 1 : Math.max(0, Math.min(1, d.hp / d.maxHp));
        d.atkSpeedPct = d.bonus("frenzy") + d.bonus("bloodHeat") * (1 - hpR);
        // 수호 본능: 파티원이 8칸 안에 있으면
        boolean near = false;
        for (ServerPlayer m : rpg.core.party.Parties.nearby(p)) if (m != p && m.distanceToSqr(p) <= 64) { near = true; break; }
        d.guardNear = near;
        // 위압: 주변 4칸 일반 몹 둔화
        if (d.bonus("intimidate") > 0)
            for (Mob m : BranchSkills.mobsAround(l, p.position(), 4)) {
                MobData md = Combat.mob(m);
                if (md != null && !Boss.isBoss(md)) m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 0, false, false));
            }
        // 도발의 대가: 나를 노리는(도발) 몹 수
        if (d.bonus("tauntDmg") > 0) {
            int n = 0;
            for (Mob m : BranchSkills.mobsAround(l, p.position(), 16)) {
                MobData md = Combat.mob(m);
                if (md != null && p.getUUID().equals(md.tauntBy) && now < md.tauntUntil) n++;
            }
            d.tauntCount = n;
        } else d.tauntCount = 0;
        // 전장의 함성 · 수호자의 맹세: 주변 8칸 파티원 (나 포함), 1.5초 유지 (계속 갱신, 중첩 안 됨)
        double cry = d.bonus("warcry"), oath = d.bonus("oath");
        if (cry > 0 || oath > 0) {
            java.util.List<ServerPlayer> who = new java.util.ArrayList<>();
            who.add(p);
            for (ServerPlayer m : rpg.core.party.Parties.nearby(p)) if (m != p && m.distanceToSqr(p) <= 64) who.add(m);
            for (ServerPlayer m : who) {
                PlayerData md = RpgData.get(m);
                if (cry > 0) { md.warcryUntil = now + 30; md.warcryVal = Math.max(md.warcryVal, cry); }
                if (oath > 0) {
                    boolean was = now < md.oathUntil;
                    md.oathUntil = now + 30;
                    md.oathVal = Math.max(md.oathVal, oath);
                    if (!was) { md.recalc(); RpgData.sync(m); }   // 최대 HP +5% 반영
                }
            }
        }
        // 맹세가 끝나면 최대 HP 원래대로
        if (d.oathVal > 0 && now >= d.oathUntil) { d.oathVal = 0; d.recalc(); RpgData.sync(p); }
        if (now >= d.warcryUntil) d.warcryVal = 0;
    }

    /** 추가 데미지%: 광전사의 혼 · 학살자 · 도발의 대가 · 전장의 함성 */
    public static double extraDmg(PlayerData d) {
        long now = d.clock;
        double v = 0;
        if (d.bonus("lowHpDmg") > 0 && d.hp < d.maxHp * 0.5) v += d.bonus("lowHpDmg");
        v += d.buffV("slayer", now);
        v += Math.min(15, d.tauntCount * d.bonus("tauntDmg"));
        if (now < d.warcryUntil) v += d.warcryVal;
        return v;
    }

    /** 추가 방어%: 수호 본능 · 수호자의 맹세 */
    public static double extraDef(PlayerData d) {
        double v = 0;
        if (d.guardNear) v += d.bonus("guardInstinct");
        if (d.clock < d.oathUntil) v += d.oathVal;
        return v;
    }
}
