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
 * 궁수: farDmg moveDmg firstStrike rollDmg critPierce windVeil killMp quickShot · 스나 stillAtk lone · 헌터 chaseSpeed weakHunt poisonArrow domino trapMaster
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
        double mpPct = d.bonus("killMp") + d.buffV2("hunt_instinct", now);   // 사냥 회복 · 사냥 본능
        if (mpPct > 0) d.mp = Math.min(d.maxMp, d.mp + d.maxMp * mpPct / 100.0);
        if (d.bonus("chaseSpeed") > 0) { d.buffEnd.put("chase", now + 60); d.buffVal.put("chase", d.bonus("chaseSpeed")); }   // 추적자
    }

    /** 처치한 몹 자리: 도미노 (반경 2칸 폭발) */
    public static void onKillMob(ServerPlayer p, PlayerData d, Mob dead) {
        double burn = CombatEvents.dotDmg(dead, "burn");
        if (burn > 0 && d.bonus("burnSpread") > 0) {   // 불길 번짐
            for (Mob m : BranchSkills.mobsAround(p.serverLevel(), dead.position(), 3)) if (m != dead) CombatEvents.dot(p, m, "burn", burn, 60);
            p.serverLevel().sendParticles(ParticleTypes.FLAME, dead.getX(), dead.getY() + 0.5, dead.getZ(), 20, 1.2, 0.3, 1.2, 0.03);
        }
        double pct = d.bonus("domino");
        if (pct <= 0) return;
        net.minecraft.world.phys.Vec3 at = dead.position();
        JobSkills.later(p, 1, () -> {
            ServerLevel l = p.serverLevel();
            for (Mob m : BranchSkills.mobsAround(l, at, 2)) if (m != dead) CombatEvents.skillHit(p, m, pct);
            l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
            l.playSound(null, net.minecraft.core.BlockPos.containing(at), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
        });
    }

    /** 구르기: 재빠른 회피 (2초 데미지 +) */
    public static void onRoll(PlayerData d, long now) {
        if (d.bonus("rollDmg") > 0) { d.buffEnd.put("roll_dmg", now + 40); d.buffVal.put("roll_dmg", d.bonus("rollDmg")); }
        if (d.bonus("rollCrit") > 0) { d.buffEnd.put("roll_crit", now + 40); d.buffVal.put("roll_crit", d.bonus("rollCrit")); }   // 잔상 걸음
    }

    /** 평타 계수 (직업 평타% + 트리 평타%, 속사면 ×1.5) */
    public static double basicMul(PlayerData d, long now) {
        double v = (rpg.core.balance.Balance.basicAttackPct[Math.max(0, Math.min(4, d.job))] + d.bonus("basicPct")) / 100.0;
        if (d.quickShotReady) v *= 1 + d.bonus("quickShot") / 100.0;
        return v;
    }

    /** 피해 준 뒤: 속사 소모 · 독 바른 화살 · 꿰뚫기 · 표식 */
    public static void afterDeal(ServerPlayer p, PlayerData d, Mob mob, MobData m, net.minecraft.world.entity.Entity direct, boolean basic, boolean crit, double dmg) {
        long now = p.level().getGameTime();
        d.lastAttack = now;
        ThiefSkills.onDeal(p, d, mob, now);   // 그림자 칼날
        double sa = -1;
        if (crit && d.bonus("critBleed") > 0)   // 치명적 출혈
            CombatEvents.dot(p, mob, "bleed", (sa = Combat.statAttack(p, d)) * d.bonus("critBleed") / 100.0, 60);
        if (basic && d.bonus("deepWound") > 0 && p.getRandom().nextFloat() < 0.2f)   // 깊은 상처
            CombatEvents.dot(p, mob, "bleed", (sa >= 0 ? sa : Combat.statAttack(p, d)) * d.bonus("deepWound") / 100.0, 60);
        if (crit && d.bonus("lifesteal") > 0) {   // 흡혈: 준 피해의 1%, 초당 최대 HP 2%까지
            long sec = now / 20;
            if (sec != d.lifestealSec) { d.lifestealSec = sec; d.lifestealUsed = 0; }
            double cap = d.maxHp * 0.02 - d.lifestealUsed;
            double heal = Math.min(cap, dmg * d.bonus("lifesteal") / 100.0);
            if (heal > 0) { d.lifestealUsed += heal; d.hp = Math.min(d.maxHp, d.hp + heal); }
        }
        if (basic && d.quickShotReady) d.quickShotReady = false;
        if (crit && d.bonus("burnOnCrit") > 0)   // 원소 공명
            CombatEvents.dot(p, mob, "burn", Combat.statAttack(p, d) * d.bonus("burnOnCrit") / 100.0, 60);
        if (basic && d.bonus("poisonArrow") > 0 && direct != p)   // 독: 평타 화살
            CombatEvents.dot(p, mob, "poison", Combat.statAttack(p, d) * d.bonus("poisonArrow") / 100.0, 60);
        if (crit && d.bonus("critPierce") > 0 && direct instanceof net.minecraft.world.entity.projectile.AbstractArrow a)   // 꿰뚫기
            a.setPierceLevel((byte) Math.min(10, a.getPierceLevel() + 1));
        if (direct != null && direct.getPersistentData().contains("rpgMark")) {   // 표식 사격
            var pd = mob.getPersistentData();
            pd.putUUID("rpgMarkBy", p.getUUID());
            pd.putLong("rpgMarkUntil", now + direct.getPersistentData().getInt("rpgMarkTicks"));
            pd.putDouble("rpgMarkPct", direct.getPersistentData().getDouble("rpgMark"));
            ((ServerLevel) mob.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /** 데미지%와 더해지는 추가% 배율 */
    private static double add(PlayerData d, double pct) {
        if (pct == 0) return 1;
        double dp = Combat.dmgPct(d);
        return (1 + (dp + pct) / 100.0) / (1 + dp / 100.0);
    }

    // ───────── 타격 (몹에게 준 피해 배율) ─────────
    /** 일반 몹 데미지: 데미지%와 더해짐 */
    public static double hitMul(ServerPlayer p, PlayerData d, Mob mob, MobData m, boolean crit) {
        long t = mob.level().getGameTime();
        double extra = 0;
        if (!Boss.isBoss(m)) extra += d.bonus("mobPct");
        if (d.bonus("farDmg") > 0 && p.distanceToSqr(mob) > 100) extra += d.bonus("farDmg");        // 명궁: 10칸 넘게
        if (d.moving) extra += d.bonus("moveDmg");                                                   // 질풍
        if (d.bonus("firstStrike") > 0 && m.hp >= m.maxHp * 0.9) extra += d.bonus("firstStrike");   // 선제 사격
        extra += d.buffV("roll_dmg", t);                                                             // 재빠른 회피
        if (d.bonus("weakHunt") > 0 && (t < m.slowUntil && m.slowPct > 0 || mob.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)))
            extra += d.bonus("weakHunt");                                                            // 약점 사냥
        if (d.bonus("burnDmg") > 0 && CombatEvents.dotDmg(mob, "burn") > 0) extra += d.bonus("burnDmg");   // 원소의 화신
        if (d.bonus("backDmg") > 0 && behind(mob, p)) extra += d.bonus("backDmg");                     // 그림자 일격
        if (d.bonus("doubleAgony") > 0 && CombatEvents.dotDmg(mob, "bleed") > 0 && CombatEvents.dotDmg(mob, "poison") > 0)
            extra += d.bonus("doubleAgony");                                                         // 이중 고통
        if (d.bonus("chaosField") > 0 && !Boss.isBoss(m) && p.distanceToSqr(mob) <= 25) extra += d.bonus("chaosField");   // 혼돈의 장
        double mul = add(d, extra);
        var pd = mob.getPersistentData();   // 표식: 그 몹이 받는 내 피해 +%
        if (pd.contains("rpgMarkBy") && t < pd.getLong("rpgMarkUntil") && p.getUUID().equals(pd.getUUID("rpgMarkBy")))
            mul *= 1 + pd.getDouble("rpgMarkPct") / 100.0;
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
        if (d.bonus("windVeil") > 0 && d.hp > 0 && d.hp < d.maxHp * 0.3 && now >= d.windVeilReadyAt) {   // 바람의 장막
            d.windVeilReadyAt = now + 1200;
            d.invulUntil = Math.max(d.invulUntil, now + 10);
            d.buffEnd.put("wind_veil", now + 60);
            d.buffVal.put("wind_veil", 50.0);
            p.serverLevel().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1, p.getZ(), 25, 0.5, 0.6, 0.5, 0.08);
        }
        if (d.bonus("escape") > 0 && d.hp > 0 && d.hp < d.maxHp * 0.3 && now >= d.escapeReadyAt) {   // 도망자: 2초 투명 + 이속
            d.escapeReadyAt = now + 1200;
            d.buffEnd.put("stealth", now + 40); d.buffVal.put("stealth", 0.0); d.buffVal2.put("stealth", 0.0);
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
            for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32))) if (m.getTarget() == p) m.setTarget(null);
            d.buffEnd.put("escape_speed", now + 40); d.buffVal.put("escape_speed", 50.0);
            p.serverLevel().sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 25, 0.4, 0.6, 0.4, 0.02);
        }
    }

    /** 공격자가 몹 등 뒤에 있나 */
    static boolean behind(Mob mob, net.minecraft.world.entity.Entity a) {
        net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(0, mob.yBodyRot);
        net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(a.getX() - mob.getX(), 0, a.getZ() - mob.getZ());
        return to.lengthSqr() > 1e-4 && to.normalize().dot(look) < -0.2;
    }

    // ───────── 매초: 공속 · 오라 ─────────
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        PlayerData d = RpgData.get(p);
        if (!rpg.core.world.RpgWorld.in(p)) { d.atkSpeedPct = 0; return; }   // 야생: 트리 효과 없음
        long now = p.level().getGameTime();
        ServerLevel l = p.serverLevel();
        // 움직이는 중 (질풍) · 냉정 · 고독한 저격수
        double dx = p.getX() - d.lastX, dz = p.getZ() - d.lastZ;
        d.moving = dx * dx + dz * dz > 0.01;
        d.lastX = p.getX(); d.lastZ = p.getZ();
        if (d.bonus("stillAtk") > 0) {
            if (d.moving) d.stillStacks = 0;
            else if (p.tickCount % 40 == 0) d.stillStacks = Math.min(5, d.stillStacks + 1);
        } else d.stillStacks = 0;
        d.loneOk = d.bonus("lone") > 0 && BranchSkills.mobsAround(l, p.position(), 6).isEmpty();
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
        if (d.bonus("chaosField") > 0)   // 혼돈의 장: 주변 5칸 일반 몹 둔화 20%
            for (Mob m : BranchSkills.mobsAround(l, p.position(), 5)) {
                MobData md = Combat.mob(m);
                if (md != null && !Boss.isBoss(md)) md.slow(20, now, now + 15);
            }
        if (d.bonus("bloodFeast") > 0) {   // 피의 향연: 주변 8칸 출혈 몹 수
            int n = 0;
            for (Mob m : BranchSkills.mobsAround(l, p.position(), 8)) if (CombatEvents.dotDmg(m, "bleed") > 0 && ++n >= 5) break;
            d.bleedNear = n;
        } else d.bleedNear = 0;
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
        double holy = d.bonus("holyAura");   // 신성 영역: 주변 8칸 파티원 최대 HP +%
        if (holy > 0) {
            for (ServerPlayer m : rpg.core.party.Parties.nearby(p)) {
                if (m.distanceToSqr(p) > 64) continue;
                PlayerData md = RpgData.get(m);
                boolean was = now < md.holyUntil;
                md.holyUntil = now + 30;
                if (!was || md.holyVal < holy) { md.holyVal = Math.max(md.holyVal, holy); md.recalc(); RpgData.sync(m); }
            }
        }
        if (d.holyVal > 0 && now >= d.holyUntil) { d.holyVal = 0; d.recalc(); RpgData.sync(p); }
        // 맹세가 끝나면 최대 HP 원래대로
        if (d.oathVal > 0 && now >= d.oathUntil) { d.oathVal = 0; d.recalc(); RpgData.sync(p); }
        if (now >= d.warcryUntil) d.warcryVal = 0;
        // HUD 버프 칸: 조건부 효과가 바뀌면 바로 보내기
        int sig = java.util.Objects.hash(d.guardNear && d.bonus("guardInstinct") > 0, d.moving && d.bonus("moveDmg") > 0, d.stillStacks, d.loneOk,
                d.bleedNear, d.tauntCount, d.bonus("lowHpDmg") > 0 && d.hp < d.maxHp * 0.5, Math.round(overload(d) * 10), loneAssassin(d));
        if (sig != d.buffSig) { d.buffSig = sig; RpgData.sync(p); }
    }

    /** 추가 데미지%: 광전사의 혼 · 학살자 · 도발의 대가 · 전장의 함성 */
    public static double extraDmg(PlayerData d) {
        long now = d.clock;
        double v = 0;
        if (d.bonus("lowHpDmg") > 0 && d.hp < d.maxHp * 0.5) v += d.bonus("lowHpDmg");
        v += d.buffV("slayer", now);
        v += Math.min(15, d.tauntCount * d.bonus("tauntDmg"));
        if (now < d.warcryUntil) v += d.warcryVal;
        v += overload(d);                       // 마력 과부하
        v += d.buffV("tp_dmg", now);           // 순간 영창
        v += d.buffV("blessed_hand", now);     // 축복받은 손
        if (loneAssassin(d)) v += 25;           // 고독한 암살자
        v += Math.min(25, d.bleedNear * d.bonus("bloodFeast"));   // 피의 향연
        return v;
    }

    /** 마력 과부하: 최대 MP 200당 +1% (최대 +25%) */
    public static double overload(PlayerData d) {
        double per = d.bonus("overloadMp");
        if (per <= 0) return 0;
        return Math.min(25, Math.max(0, d.maxMp) / 200.0 * per);
    }

    /** 추가 공격력%: 냉정 (가만히 2초마다 +3%, 최대 5중첩) */
    public static double extraAtk(PlayerData d) {
        return d.stillStacks * d.bonus("stillAtk");
    }

    /** 추가 크리 피해%: 고독한 저격수 */
    public static double extraCritDmg(PlayerData d) {
        return (d.loneOk ? 20 : 0) + (loneAssassin(d) ? 15 : 0);
    }

    /** 추가 크리 확률%: 잔상 걸음 · 고독한 암살자 */
    public static double extraCrit(PlayerData d) {
        return d.buffV("roll_crit", d.clock) + (loneAssassin(d) ? 15 : 0);
    }

    /** 고독한 암살자: 8칸 안에 파티원 없음 */
    static boolean loneAssassin(PlayerData d) {
        return d.bonus("loneAssassin") > 0 && !d.guardNear;
    }

    /** 추가 보스%: 고독한 저격수 */
    public static double extraBoss(PlayerData d) {
        return d.loneOk ? 10 : 0;
    }

    /** 추가 방어%: 수호 본능 · 수호자의 맹세 */
    public static double extraDef(PlayerData d) {
        double v = 0;
        if (d.guardNear) v += d.bonus("guardInstinct");
        if (d.clock < d.oathUntil) v += d.oathVal;
        return v;
    }
}
