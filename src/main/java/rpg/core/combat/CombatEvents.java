package rpg.core.combat;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.Progress;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/**
 * 전투 처리. RPG 몹은 가상 체력(MobData.hp)을 쓰고, 바닐라 체력은 그 비율을 비춘다.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class CombatEvents {
    private CombatEvents() {}

    /**
     * 근접 배율: 들어온 바닐라 피해 ÷ 공격력 속성. Epic Fight 모션별 배율(콤보·강공격)과
     * 바닐라 쿨타임·치명타가 그대로 RPG 피해에 곱해짐. 대상 엔티티 id 별로 기억.
     */
    private static final Map<Integer, Double> MELEE_RATIO = new java.util.HashMap<>();

    @SubscribeEvent
    public static void attachMob(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Mob && !e.getObject().getCapability(MobData.Provider.CAP).isPresent()) {
            e.addCapability(RpgCore.id("mob"), new MobData.Provider());
        }
    }

    /** RPG 몹끼리는 안 싸움: 다른 몹을 노리지 않음. 은신한 플레이어도 못 노림 */
    @SubscribeEvent
    public static void noInfight(net.minecraftforge.event.entity.living.LivingChangeTargetEvent e) {
        if (!(e.getEntity() instanceof Mob m) || m.level().isClientSide || e.getNewTarget() == null) return;
        if (rpg.core.skill.JobSkills.hidden(e.getNewTarget())) { e.setCanceled(true); return; }
        MobData md = Combat.mob(m);
        if (md != null && md.tauntBy != null && m.level().getGameTime() < md.tauntUntil
                && !md.tauntBy.equals(e.getNewTarget().getUUID())) { e.setCanceled(true); return; }   // 도발 중
        if (isRpg(m) && !(e.getNewTarget() instanceof net.minecraft.world.entity.player.Player)) e.setCanceled(true);
    }

    // ───────── 스킬 피해 ─────────
    /** 다음 피해를 스킬 배율(%)로 / 고정 피해로 */
    private static final Map<Integer, Double> SKILL_PCT = new java.util.HashMap<>();
    private static final Map<Integer, Double> FIXED = new java.util.HashMap<>();

    /** 몹 패턴 피해: 몹 공격력 × pct% */
    private static final Map<Integer, Double> MOB_PCT = new java.util.HashMap<>();

    public static void mobSkillHit(Mob mob, ServerPlayer p, double pct) {
        MOB_PCT.put(p.getId(), pct);
        p.hurt(mob.damageSources().mobAttack(mob), 4.0f);
        MOB_PCT.remove(p.getId());
    }

    /** 보스 패턴 피해: 최대 HP × pct% (방어 무시) */
    private static final Map<Integer, Double> MAXHP_PCT = new java.util.HashMap<>();

    public static void maxHpHit(Mob mob, ServerPlayer p, double pct) {
        MAXHP_PCT.put(p.getId(), pct);
        p.hurt(mob.damageSources().mobAttack(mob), 4.0f);
        MAXHP_PCT.remove(p.getId());
    }

    /** RPG 몹 근접 공격은 몸 바깥 1.2칸(+여유) 안에서만 맞음 (멀리서 휘두르는 몹 막기) */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void meleeReach(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getSource().getEntity() instanceof Mob m)) return;
        if (e.getSource().getDirectEntity() != m || !isRpg(m) || MOB_PCT.containsKey(p.getId()) || MAXHP_PCT.containsKey(p.getId())) return;   // 패턴·투사체는 제외
        if (MobAI.edge(m, p) > RpgMeleeGoal.REACH + 0.6) e.setCanceled(true);
    }

    /**
     * RPG 몹이 맞을 수 있는 공격인가: 플레이어 근접(가시 제외) · RPG 스킬·화살·지속 피해 · 공허/kill.
     * 바닐라 활·석궁·삼지창·물약·불·독·펫·다른 모드 공격은 0 (공유 창고로 들여온 아이템 악용 막기)
     */
    public static boolean allowedHit(Mob t, net.minecraft.world.damagesource.DamageSource src) {
        if (src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        if (SKILL_PCT.containsKey(t.getId()) || FIXED.containsKey(t.getId())) return true;
        if (!(src.getEntity() instanceof ServerPlayer ap)) return false;
        Entity direct = src.getDirectEntity();
        if (direct == ap) return !src.is(net.minecraft.world.damagesource.DamageTypes.THORNS);
        if (direct == null) return false;
        var pd = direct.getPersistentData();
        return pd.contains("rpgSkillPct") || pd.contains("rpgPower") || pd.getBoolean("rpgArrow");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onlyRpgHits(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (e.getEntity() instanceof Mob t && !t.level().isClientSide && isRpg(t) && !allowedHit(t, e.getSource())) e.setCanceled(true);
    }

    /** PvP 끔: 플레이어(또는 플레이어가 쏜 것)가 다른 플레이어를 때리면 막음 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void noPvp(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player victim)) return;
        if (e.getSource().getEntity() instanceof net.minecraft.world.entity.player.Player a && a != victim) e.setCanceled(true);
    }

    /** 다른 직업(또는 레벨 부족) 무기로는 근접 공격이 안 들어감 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void wrongWeapon(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getSource().getDirectEntity() != p) return;
        String why = rpg.core.equip.Gear.weaponBlock(p, p.getMainHandItem());
        if (why == null) return;
        e.setCanceled(true);
        p.displayClientMessage(net.minecraft.network.chat.Component.literal(why).withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    /** 바닐라 독·시듦은 플레이어에게 안 걸림 (RPG 피해만) */
    @SubscribeEvent
    public static void noVanillaDot(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable e) {
        if (!(e.getEntity() instanceof net.minecraft.world.entity.player.Player pl) || !rpg.core.world.RpgWorld.in(pl)) return;   // 야생은 바닐라 그대로
        var eff = e.getEffectInstance().getEffect();
        if (eff == net.minecraft.world.effect.MobEffects.POISON || eff == net.minecraft.world.effect.MobEffects.WITHER)
            e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    /** 스킬로 몹 때리기: 스공 × pct% */
    public static void skillHit(ServerPlayer p, Mob t, double pct) {
        SKILL_PCT.put(t.getId(), pct);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), 1.0f);
        SKILL_PCT.remove(t.getId());
    }

    /** 고정 피해 (출혈 등) */
    public static void fixedHit(ServerPlayer p, Mob t, double amount) {
        FIXED.put(t.getId(), amount);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), 1.0f);
        FIXED.remove(t.getId());
    }

    /** 스킬 화살은 연속으로 맞아도 다 들어가게 */
    @SubscribeEvent
    public static void skillArrowIframes(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        Entity direct = e.getSource().getDirectEntity();
        if (direct != null && direct.getPersistentData().contains("rpgSkillPct")) e.getEntity().invulnerableTime = 0;
    }

    // ── 지속 피해 (출혈·화상·독): "몹 id:종류" → (주인, 초당 피해, 끝나는 틱) ──
    private record Dot(ServerPlayer owner, int mob, String kind, double dmg, long end) {}
    private static final Map<String, Dot> DOTS = new java.util.HashMap<>();

    /** 이 몹에 걸린 지속 피해 (초당, 없으면 0) */
    public static double dotDmg(Mob m, String kind) {
        Dot b = DOTS.get(m.getId() + ":" + kind);
        return b == null || m.level().getGameTime() > b.end() ? 0 : b.dmg();
    }

    /** 지속 피해 걸기 (같은 종류는 새로 덮어씀). kind: bleed 출혈 / burn 화상 / poison 독 */
    public static void dot(ServerPlayer owner, Mob m, String kind, double perSecond, int ticks) {
        if (perSecond <= 0 || ticks <= 0) return;
        PlayerData od = RpgData.get(owner);   // 트리: 지속 피해 +% · 출혈/독 전문가
        perSecond *= 1 + (od.bonus("dotPct") + od.bonus(kind + "Pct")) / 100.0;
        DOTS.put(m.getId() + ":" + kind, new Dot(owner, m.getId(), kind, perSecond, owner.level().getGameTime() + ticks));
    }

    @SubscribeEvent
    public static void dotTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || DOTS.isEmpty()) return;
        var it = DOTS.entrySet().iterator();
        java.util.List<Dot> run = new java.util.ArrayList<>();
        while (it.hasNext()) {
            Dot b = it.next().getValue();
            if (!b.owner().isAlive()) { it.remove(); continue; }
            Entity ent = b.owner().serverLevel().getEntity(b.mob());
            long now = b.owner().level().getGameTime();
            if (!(ent instanceof Mob m) || !m.isAlive() || now > b.end()) { it.remove(); continue; }
            if (now % 20 == 0) run.add(b);
        }
        if (run.isEmpty()) return;
        // 몹별로 묶어서: 피해는 각자 넣고, 숫자 1개(합계)·체력 동기화 1번·파티클 2개
        java.util.Map<Integer, java.util.List<Dot>> byMob = new java.util.LinkedHashMap<>();
        for (Dot b : run) byMob.computeIfAbsent(b.mob(), k -> new java.util.ArrayList<>()).add(b);
        for (var en : byMob.values()) {
            Dot first = en.get(0);
            Entity ent = first.owner().serverLevel().getEntity(first.mob());
            if (!(ent instanceof Mob m) || !m.isAlive()) continue;
            java.util.Map<String, Double> byKind = new java.util.LinkedHashMap<>();   // 종류별 합계 (독·출혈·화상 색 다르게)
            DOT_HIT = true;
            try {
                for (Dot b : en) {
                    if (!m.isAlive() || !b.owner().isAlive()) continue;
                    dotDealt = 0;
                    fixedHit(b.owner(), m, b.dmg());
                    byKind.merge(b.kind(), dotDealt, Double::sum);
                }
            } finally { DOT_HIT = false; }
            Mobs.sync(m);
            for (var k : byKind.entrySet())
                if (k.getValue() > 0) Net.damageText(m, k.getValue(), k.getKey().equals("poison") ? 2 : k.getKey().equals("burn") ? 4 : 3);
            var l = first.owner().serverLevel();
            double y = m.getY() + m.getBbHeight() * 0.6;
            String kind = en.get(en.size() - 1).kind();
            if (kind.equals("burn")) l.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, m.getX(), y, m.getZ(), 2, 0.25, 0.35, 0.25, 0.02);
            else l.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                    kind.equals("poison") ? new org.joml.Vector3f(0.3f, 0.8f, 0.2f) : new org.joml.Vector3f(0.7f, 0f, 0f), 1.2f),
                    m.getX(), y, m.getZ(), 2, 0.2, 0.3, 0.2, 0);
        }
    }

    /** 무게중심: 넉백 줄임 */
    @SubscribeEvent
    public static void knockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        double r = Math.min(100, RpgData.get(sp).bonus("kbResist"));
        if (r <= 0) return;
        if (r >= 100) e.setCanceled(true);
        else e.setStrength((float) (e.getStrength() * (1 - r / 100.0)));
    }

    /** RPG 몹끼리 피해 없음 (스켈레톤 화살 오사 등) */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void noFriendlyFire(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (e.getEntity() instanceof Mob t && !t.level().isClientSide && e.getSource().getEntity() instanceof Mob a
                && isRpg(t) && isRpg(a)) e.setCanceled(true);
    }

    /** 좀비 → 드라운드 같은 변신 없음 */
    @SubscribeEvent
    public static void noConvert(net.minecraftforge.event.entity.living.LivingConversionEvent.Pre e) {
        if (e.getEntity() instanceof Mob m && isRpg(m)) {
            e.setCanceled(true);
            e.setConversionTimer(Integer.MAX_VALUE / 2);
        }
    }

    // ── 슬라임 분열 막기: RPG 슬라임이 죽은 자리에서 같은 순간 생기는 슬라임은 취소 ──
    private record Split(net.minecraft.world.level.Level level, net.minecraft.world.phys.Vec3 pos, long time) {}
    private static final java.util.List<Split> SPLITS = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void noSplit(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof net.minecraft.world.entity.monster.Slime s) || SPLITS.isEmpty()) return;
        long now = e.getLevel().getGameTime();
        SPLITS.removeIf(sp -> now - sp.time() > 40);   // 분열은 죽는 모션(약 1초) 뒤에 일어남
        if (Combat.mob(s) != null && Combat.mob(s).isRpg()) return;
        for (Split sp : SPLITS) {
            if (sp.level() == e.getLevel() && sp.pos().distanceToSqr(s.position()) < 16) { e.setCanceled(true); return; }
        }
    }

    /** RPG 몹은 햇빛에 안 탐 */
    @SubscribeEvent
    public static void noBurn(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
        if (e.getEntity() instanceof Mob m && !m.level().isClientSide && m.getRemainingFireTicks() > 0 && !m.isInLava()
                && m.level().canSeeSky(m.blockPosition()) && isRpg(m)) m.clearFire();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (e.getSource().getEntity() instanceof ServerPlayer ap && e.getSource().getDirectEntity() == ap) {
            double base = Math.max(0.5, ap.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            MELEE_RATIO.put(e.getEntity().getId(), Math.max(0.2, Math.min(4.0, e.getAmount() / base)));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide) return;
        Entity src = e.getSource().getEntity();

        // ── 플레이어가 맞음 ──
        if (target instanceof ServerPlayer sp) {
            if (!rpg.core.world.RpgWorld.in(sp)) return;   // 야생: 바닐라 피해
            PlayerData d = RpgData.get(sp);
            long tnow = sp.level().getGameTime();
            if (tnow < d.invulUntil) { e.setCanceled(true); return; }   // 잠재능력: 확률 무적 중
            if (src instanceof Mob hm) { d.lastHitBy = isRpg(hm) ? Combat.mob(hm).name : hm.getName().getString(); d.lastHitAt = tnow; }   // 사망 메시지용
            double dmg;
            Double mx = MAXHP_PCT.get(sp.getId());
            if (mx != null) dmg = d.maxHp * mx / 100.0;   // 최대 HP 비율 피해
            else if (src instanceof Mob m && isRpg(m)) {
                Double pc = MOB_PCT.get(sp.getId());
                dmg = Combat.mobToPlayer(Combat.mob(m).atk * (pc == null ? 1 : pc / 100.0), Combat.mob(m).level, d)
                        * takeLevelMul(Combat.mob(m).level, d.level);   // 높은 몹: 레벨당 받는 피해 +5%
            }
            else if (src instanceof Mob) dmg = Math.max(1, e.getAmount() / 20.0 * d.maxHp * (1 - Combat.defRatio(d, d.level)));  // 일반 몹: 비율, 내 레벨 몹으로 보고 방어
            else dmg = e.getAmount() / 20.0 * d.maxHp;   // 환경 피해: 바닐라와 같은 비율
            long now = sp.level().getGameTime();
            dmg *= 1 - Math.min(90, d.buffV("iron_wall", now)) / 100.0;           // 철벽: 받는 피해 감소
            dmg *= 1 + d.buffV2("berserk", now) / 100.0;                           // 광폭화: 받는 피해 증가
            dmg *= rpg.core.skill.PassiveEffects.takenMul(d);                     // 트리: 광란
            double guard = d.buffV("magic_guard", now);                            // 매직 가드: 일부를 MP로
            if (guard > 0 && src != null) {
                double toMp = Math.min(d.mp, dmg * Math.min(90, guard) / 100.0);
                d.mp -= toMp;
                dmg -= toMp;
            }
            // 잠재능력 피격 옵션
            var rnd = java.util.concurrent.ThreadLocalRandom.current();
            if (d.eq("ignorePct") > 0 && rnd.nextDouble() * 100 < rpg.core.equip.Potential.ignoreChance)
                dmg *= 1 - Math.min(80, d.eq("ignorePct")) / 100.0;                  // 확률로 피해 일부 무시
            if (d.eq("invulProc") > 0 && rnd.nextDouble() * 100 < Math.min(50, d.eq("invulProc"))) {
                d.invulUntil = now + Math.round(d.eq("invulProcSec") * 20);          // 확률로 몇 초 무적
                sp.displayClientMessage(net.minecraft.network.chat.Component.literal("무적!").withStyle(net.minecraft.ChatFormatting.AQUA), true);
            }
            if (d.shield > 0 && now < d.shieldEnd) {                               // 보호막 (넘치는 치유)
                double absorb = Math.min(d.shield, dmg);
                d.shield -= absorb;
                dmg -= absorb;
            }
            d.hp = Math.max(0, d.hp - dmg);
            mirror(e, sp, d.hp, d.maxHp);
            rpg.core.skill.PassiveEffects.afterHit(sp, d, src, e.getSource().getDirectEntity());   // 트리: 가시 갑옷 · 위기 회복
            if (d.eq("iframe") > 0) sp.invulnerableTime = 20 + (int) Math.round(Math.min(2, d.eq("iframe")) * 20);   // 피격 후 무적 시간 증가
            RpgData.sync(sp);
            return;
        }

        // ── RPG 몹이 맞음 ──
        if (!(target instanceof Mob mob) || !isRpg(mob)) return;
        MobData m = Combat.mob(mob);
        double dmg;
        boolean crit = false;
        if (src instanceof ServerPlayer ap) {
            Entity direct = e.getSource().getDirectEntity();
            Double mr = MELEE_RATIO.remove(target.getId());
            Double fixed = FIXED.get(target.getId()), sk = SKILL_PCT.get(target.getId());
            PlayerData ad = RpgData.get(ap);
            long now = ap.level().getGameTime();
            if (fixed != null) {
                dmg = Math.max(1, Math.round(fixed));
            } else {
                double ratio;
                if (sk != null) ratio = sk / 100.0;                                    // 스킬
                else if (direct != null && direct.getPersistentData().contains("rpgSkillPct"))
                    ratio = direct.getPersistentData().getDouble("rpgSkillPct") / 100.0;   // 스킬 화살
                else if (direct == ap) ratio = (mr == null ? 1.0 : mr)                 // 근접 (Epic Fight 모션 배율) × 직업 평타 계수
                        * rpg.core.skill.PassiveEffects.basicMul(ad, now);
                else if (direct != null && direct.getPersistentData().contains("rpgPower"))
                    ratio = Math.max(0.1, direct.getPersistentData().getFloat("rpgPower"))   // 활: 당긴 정도 × 직업 평타 계수
                            * rpg.core.skill.PassiveEffects.basicMul(ad, now);
                else if (direct != null && direct.getPersistentData().getBoolean("rpgArrow"))
                    ratio = rpg.core.skill.PassiveEffects.basicMul(ad, now);           // 궁수 평타 화살 (직업 평타 계수)
                else ratio = 0;                                                          // RPG 공격이 아님 (allowedHit 에서 이미 막힘)
                // 은신 중 첫 공격: 추가 피해 + 출혈, 은신 풀림
                if (ad.buff("stealth", now)) {
                    ratio *= 1 + ad.buffV("stealth", now) / 100.0;
                    double per = Combat.statAttack(ap, ad) * ad.buffV2("stealth", now) / 100.0;
                    dot(ap, mob, "bleed", per, 100);
                    rpg.core.skill.JobSkills.breakStealth(ap, ad);
                    ap.serverLevel().playSound(null, target.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1f, 0.7f);
                }
                if (ad.buff("shadow_step", now)) rpg.core.skill.JobSkills.breakShadow(ap, ad);   // 공격하면 투명 풀림
                if (ad.bonus("executePct") > 0 && m.hp <= m.maxHp * 0.3) ratio *= 1 + ad.bonus("executePct") / 100.0;   // 처형
                Combat.Hit h = Combat.roll(ap, ad, ratio);
                dmg = h.amount();
                crit = h.crit();
                dmg = Math.max(1, Math.round(dmg * rpg.core.skill.PassiveEffects.hitMul(ap, ad, mob, m, crit) * Combat.defMul(ad, m)));   // 트리 효과 · 몹 방어율 (방무)
                if (crit && ad.bonus("backCritDmg") > 0 && behind(mob, ap))                                   // 급소: 뒤에서 치명타
                    dmg = Math.round(dmg * (ad.critDamage() + ad.bonus("backCritDmg") / 100.0) / ad.critDamage());
                rpg.core.skill.JobSkills.onHit(ap, ad, now);                                                  // 전투 흥분 등
                boolean basic = sk == null && (direct == ap || (direct != null && direct.getPersistentData().contains("rpgPower")));
                rpg.core.skill.PassiveEffects.afterDeal(ap, ad, mob, m, direct, basic, crit, dmg);              // 트리: 독·관통·표식
                // 경직 충격: 근접은 EF 모션 배율, 스킬은 배율, 화살은 당긴 정도 (출혈 등 고정 피해는 경직 없음)
                double impact = Math.min(3.0, ratio * (crit ? 1.2 : 1.0));
                MobAI.stun(mob, m, impact, ap);
            }
            if (!Boss.isBoss(m)) dmg = Math.max(1, Math.round(dmg * dealLevelMul(m.level, ad.level)));   // 높은 몹: 레벨당 주는 피해 -5% (보스는 Boss.onHurt)
            if (direct != null && direct.getPersistentData().contains("rpgSlow")) {
                mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                        direct.getPersistentData().getInt("rpgSlow"), 1));
            }
        } else if (e.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            dmg = m.hp;   // 공허·/kill
        } else {
            dmg = 0;      // RPG 몹은 RPG 공격만 맞음 (환경·바닐라·다른 모드 피해 없음)
        }
        if (src instanceof ServerPlayer cp && mob.getPersistentData().contains("rpgCocoon")) dmg = DOT_HIT ? 0 : BossX.cocoonHit(mob, m, cp);   // 고치: 횟수로 풀림
        if (Boss.isBoss(m)) {   // 보스: 플레이어 피해만 (레벨 차이 감소·기여도), 나머지는 무시
            dmg = src instanceof ServerPlayer bp ? Boss.onHurt(mob, m, bp, dmg) : 0;
        }
        m.hp = Math.max(0, m.hp - dmg);
        mirror(e, mob, m.hp, m.maxHp);
        mob.invulnerableTime = 0;   // 무적 시간 없음: 여러 명이 동시에 때려도 타격이 안 씹히게
        if (DOT_HIT) { dotDealt += dmg; return; }   // 지속 피해: 숫자·체력 동기화는 1초에 한 번 묶어서
        Mobs.sync(mob);
        if (src instanceof ServerPlayer) Net.damageText(mob, dmg, crit ? 1 : 0);
    }

    /** 지속 피해 묶음 처리 중 (숫자·동기화 따로 안 보냄) */
    private static boolean DOT_HIT;
    private static double dotDealt;

    /** 공격자가 몹 등 뒤(몸이 보는 방향 반대쪽)에 있나 */
    private static boolean behind(Mob mob, Entity a) {
        net.minecraft.world.phys.Vec3 look = net.minecraft.world.phys.Vec3.directionFromRotation(0, mob.yBodyRot);
        net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(a.getX() - mob.getX(), 0, a.getZ() - mob.getZ());
        return to.lengthSqr() > 1e-4 && to.normalize().dot(look) < -0.2;
    }

    private static boolean isRpg(Mob m) {
        return Combat.isRpgMob(m);
    }

    /** RPG 체력 비율에 맞게 바닐라가 받을 피해량을 정함 */
    private static void mirror(LivingDamageEvent e, LivingEntity t, double hp, double maxHp) {
        float cur = t.getHealth();
        if (hp <= 0) {
            e.setAmount(cur + 1000f);
            return;
        }
        float want = (float) Math.max(0.5, hp / maxHp * t.getMaxHealth());
        e.setAmount(Math.max(0f, cur - want));
    }

    @SubscribeEvent
    public static void slimeDied(LivingDeathEvent e) {
        if (e.getEntity() instanceof net.minecraft.world.entity.monster.Slime s && !s.level().isClientSide && isRpg(s)) {
            SPLITS.add(new Split(s.level(), s.position(), s.level().getGameTime()));
        }
    }

    /** 몹 처치 보상 */
    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || mob.level().isClientSide || !isRpg(mob)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer)) return;
        MobData m = Combat.mob(mob);
        PlayerData d = RpgData.get(killer);
        rpg.core.skill.JobSkills.onKill(killer, d);   // 연속 분노
        rpg.core.skill.PassiveEffects.onKillMob(killer, d, mob);   // 트리: 도미노
        if (rpg.core.job.Trials.isTrialMob(mob)) return;   // 시련 몹: 보상 없음 (통과는 Trials)
        if (Boss.isBoss(m)) { Boss.onDeath(mob, m, killer); return; }
        if (mob.getPersistentData().getBoolean("rpgPillar")) return;   // 보스 기둥: 보상 없음   // 보스: 기여도 분배 · 드롭 세트
        // 연속 처치 콤보 (골드 주는 몹만, 보스방 밖에서만): 10초 안에 다음 처치
        long now = killer.level().getGameTime();
        if (m.gold > 0 && !Boss.inArena(killer)) {
            d.killCombo = now < d.killComboUntil ? d.killCombo + 1 : 1;
            d.killComboUntil = now + 200;
            if (d.killCombo >= 10 && d.killCombo % 10 == 0)
                killer.displayClientMessage(net.minecraft.network.chat.Component.literal(d.killCombo + " 콤보! 골드 +" + d.comboGoldPct(now) + "%")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
        rpg.core.loot.Drops.roll(mob, m, killer, d);   // 드롭은 막타 기준 (골드는 주머니로, 콤보 보너스 포함)
        // 경험치: 근처 파티원끼리 균등 분배 + 인원당 보너스, 레벨 차이 5 넘으면 넘는 레벨당 -10% (각자 레벨 기준)
        var members = rpg.core.party.Parties.nearby(killer);
        int n = members.size();
        double total = m.exp * (1 + rpg.core.balance.Balance.partyBonusPerMember * (n - 1) / 100.0);
        double eachBase = total / n;
        for (ServerPlayer mem : members) {
            long each = Math.max(1, Math.round(eachBase * expLevelMul(m.level, RpgData.get(mem).level)));
            Net.reward(mem, each, 0);
            Progress.giveExp(mem, each);
        }
        rpg.core.quest.QuestEvents.onKill(members, m);   // 퀘스트 처치 진행도 (근처 파티원 모두)
    }

    /** 레벨 차이 경험치 배율: 위아래 5레벨까지 100%, 넘는 1레벨마다 -10% (최소 10%) */
    public static double expLevelMul(int mobLevel, int playerLevel) {
        int over = Math.abs(mobLevel - playerLevel) - 5;
        return over <= 0 ? 1.0 : Math.max(0.1, 1 - over * 0.1);
    }

    /** 몹이 나보다 높으면 레벨당 주는 피해 -5% (최소 10%) */
    public static double dealLevelMul(int mobLevel, int playerLevel) {
        int diff = mobLevel - playerLevel;
        return diff <= 0 ? 1.0 : Math.max(0.1, 1 - diff * 0.05);
    }

    /** 몹이 나보다 높으면 레벨당 받는 피해 +5% (최대 2배) */
    public static double takeLevelMul(int mobLevel, int playerLevel) {
        int diff = mobLevel - playerLevel;
        return diff <= 0 ? 1.0 : Math.min(2.0, 1 + diff * 0.05);
    }

    /** RPG 몹은 바닐라 드롭·경험치 구슬 없음 (RPG 드롭은 loot.Drops) */
    @SubscribeEvent
    public static void drops(LivingDropsEvent e) {
        if (e.getEntity() instanceof Mob m && isRpg(m)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void xpDrop(LivingExperienceDropEvent e) {
        if (e.getEntity() instanceof Mob m && isRpg(m)) e.setCanceled(true);
    }


    /** 몹이 보이기 시작하면 레벨·체력 보내기 */
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof Mob mob && e.getEntity() instanceof ServerPlayer sp) {
            MobData m = Combat.mob(mob);
            if (m != null && m.isRpg()) Net.mobInfoTo(sp, mob, m);
        }
    }
}
