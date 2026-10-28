package rpg.core.combat;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.net.Net;

/**
 * RPG 몹 행동: 먼저 공격하게 만들기, 다가오기(chase), 예고 후 공격 패턴 (mobs.json skills)
 * 예고: 바닥에 빨간 범위 → delay 초 뒤 그 범위 안 플레이어에게 피해
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class MobAI {
    private MobAI() {}

    public static final int CIRCLE = 0, LINE = 1, CONE = 2;

    // ───────── 스폰할 때 ─────────
    public static void setup(Mob mob, Balance.MobDef def) {
        MobData md = Combat.mob(mob);
        if (md != null) { if (md.aiReady) return; md.aiReady = true; }
        GoalSelector goals = selector(mob, 0), targets = selector(mob, 1);
        // 근접 공격 거리 통일: 기존 근접 공격(거미·곰·늑대 등 포함)을 RPG 근접 공격으로 바꿈
        if (goals != null && mob instanceof PathfinderMob pm) {
            java.util.List<net.minecraft.world.entity.ai.goal.WrappedGoal> melee = new java.util.ArrayList<>();
            for (var g : goals.getAvailableGoals())
                if (g.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal && !(g.getGoal() instanceof RpgMeleeGoal)) melee.add(g);
            for (var g : melee) {
                goals.removeGoal(g.getGoal());
                goals.addGoal(g.getPriority(), new RpgMeleeGoal(pm, 1.15));
            }
        }
        if (goals != null) {
            // 좀벌레: 블록에 숨기·친구 부르기 없음
            goals.getAvailableGoals().removeIf(g -> {
                String n = g.getGoal().getClass().getName();
                return n.contains("Silverfish") && (n.contains("Merge") || n.contains("WakeUp"));
            });
        }
        // 중립 몹(늑대·여우·곰 등)도 플레이어를 먼저 공격
        if (!def.look().passive() && !def.look().chase() && !(mob instanceof Enemy) && targets != null
                && mob instanceof PathfinderMob && mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            targets.addGoal(1, new NearestAttackableTargetGoal<>(mob, Player.class, true));
        }
    }

    /** Mob 의 GoalSelector 필드 (0 = 행동, 1 = 대상) — 이름 대신 순서로 찾기 */
    private static Field[] SEL;

    static GoalSelector selector(Mob mob, int idx) {
        try {
            if (SEL == null) {
                List<Field> fs = new java.util.ArrayList<>();
                for (Field f : Mob.class.getDeclaredFields()) {
                    if (f.getType() == GoalSelector.class) { f.setAccessible(true); fs.add(f); }
                }
                SEL = fs.toArray(new Field[0]);
            }
            return idx < SEL.length ? (GoalSelector) SEL[idx].get(mob) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** 저장된 RPG 몹을 다시 불러올 때(접속·청크 로드)도 AI 다시 설정 — 안 하면 바닐라 근접 공격으로 돌아감 */
    @SubscribeEvent
    public static void rejoin(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof Mob mob)) return;
        MobData m = Combat.mob(mob);
        if (m == null || !m.isRpg() || m.aiReady) return;
        Balance.MobDef def = Balance.MOBS.get(m.key);
        if (def != null) setup(mob, def);
    }

    // ───────── 매 틱 ─────────
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || mob.level().isClientSide || !mob.isAlive()) return;
        MobData m = Combat.mob(mob);
        if (m == null || !m.isRpg()) return;
        Balance.MobDef def = Balance.MOBS.get(m.key);
        if (def == null) return;
        long now = mob.level().getGameTime();
        if (def.look().baby() && mob instanceof AgeableMob am && mob.tickCount % 200 == 0) am.setAge(-24000);
        applySlow(mob, m, now);
        if (Boss.isBoss(m)) { Boss.tick(mob, m, def); return; }   // 보스는 따로
        if (m.returning) return;   // 사냥터로 돌아가는 중 (ZoneLeash)

        if (m.casting >= 0) {
            if (m.casting >= def.skills().size()) { m.casting = -1; return; }
            Balance.MobSkill sk = def.skills().get(m.casting);
            if (!m.castAnim && mob instanceof rpg.core.entity.GeoRpgMonster g
                    && now >= m.castEnd - g.patternLead(sk.type())) {
                m.castAnim = true;
                g.playPattern(sk.type());
            }
            if (now >= m.castEnd) {
                m.casting = -1;
                execute(mob, m, sk);
            } else {   // 예고 중: 제자리
                mob.getNavigation().stop();
                Vec3 v = mob.getDeltaMovement();
                mob.setDeltaMovement(0, v.y, 0);
                if (m.castDir != null) {
                    float yaw = (float) Math.toDegrees(Math.atan2(-m.castDir.x, m.castDir.z));
                    mob.setYRot(yaw); mob.setYHeadRot(yaw); mob.setYBodyRot(yaw);
                }
            }
            return;
        }
        if (now < m.stunUntil) {   // 경직 중: 제자리, 패턴 시작 안 함
            mob.getNavigation().stop();
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
            return;
        }
        if (now < m.decoyUntil && m.decoyPos != null) {   // 분신술: 분신한테 감 (플레이어 안 노림)
            if (mob.getTarget() != null) mob.setTarget(null);
            if (mob.tickCount % 10 == 0) mob.getNavigation().moveTo(m.decoyPos.x, m.decoyPos.y, m.decoyPos.z, 1.2);
            return;
        }
        if (def.skills().isEmpty() && !def.look().chase()) return;
        if (mob.tickCount % 5 != 0) return;

        Player t = mob.getTarget() instanceof Player p && valid(p) ? p : null;
        Player taunter = taunter(mob, m, now);
        if (taunter != null) {
            t = taunter;
            if (mob.getTarget() != taunter) mob.setTarget(taunter);
        }
        if (t == null && (def.look().chase() || def.look().passive())) t = nearest(mob, 16);
        if (t == null) return;
        if (def.look().chase() && mob.tickCount % 10 == 0) mob.getNavigation().moveTo(t, 1.2);
        // 브레인 AI 몹(호글린 등)은 멀리서 멈추지 않고 1.2칸까지 붙게
        if (mob instanceof net.minecraft.world.entity.monster.hoglin.Hoglin && mob.tickCount % 10 == 0
                && edge(mob, t) > RpgMeleeGoal.REACH * 0.8) mob.getNavigation().moveTo(t, 1.1);

        double dist = mob.distanceTo(t);
        for (int i = 0; i < def.skills().size() && i < m.skillReady.length; i++) {
            Balance.MobSkill sk = def.skills().get(i);
            if (now < m.skillReady[i] || dist > sk.range() || !mob.hasLineOfSight(t)) continue;
            if (sk.hpBelow() > 0 && m.hp > m.maxHp * sk.hpBelow() / 100.0) continue;   // HP 몇 % 아래에서만 쓰는 패턴
            start(mob, m, sk, i, t, now);
            return;
        }
    }

    /**
     * 경직 (EF 방식): 충격(impact) - 경직 방어 = 남는 충격 → 없음 / 짧은 경직 / 긴 경직(뒤로 밀림)
     * 패턴 예고 중에는 안 끊김, 연속 경직이 많으면 잠깐 면역
     */
    public static void stun(Mob mob, MobData m, double impact, net.minecraft.world.entity.Entity from) {
        if (m == null || m.casting >= 0) return;
        Balance.MobDef def = Balance.MOBS.get(m.key);
        double armor = (def == null ? 0 : def.stunArmor()) + (m.elite ? Balance.eliteStunArmor : 0);
        double left = impact - armor;
        if (left < Balance.stunShortMin) return;
        long now = mob.level().getGameTime();
        if (now < m.stunImmuneUntil) return;
        if (now - m.stunWindow > Balance.stunChainWindow) { m.stunWindow = now; m.stunCount = 0; }
        if (++m.stunCount > Balance.stunChain) { m.stunImmuneUntil = now + Balance.stunImmuneTicks; m.stunCount = 0; return; }
        boolean lng = left >= Balance.stunLongMin;
        m.stunUntil = Math.max(m.stunUntil, now + (lng ? Balance.stunLongTicks : Balance.stunShortTicks));
        m.stunCancel = true;
        mob.getNavigation().stop();
        if (lng && from != null) {
            double dx = mob.getX() - from.getX(), dz = mob.getZ() - from.getZ();
            double l = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            mob.setDeltaMovement(mob.getDeltaMovement().add(dx / l * 0.35, 0.05, dz / l * 0.35));
        }
    }

    private static final java.util.UUID SLOW_ID = java.util.UUID.fromString("6f1f0d2e-7a51-4c1e-9b0a-52d0c1a1e010");

    /** 덫·독안개 둔화: 이동속도 속성에 -slowPct% (100 = 속박) */
    private static void applySlow(Mob mob, MobData m, long now) {
        var inst = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        double want = now < m.slowUntil ? -Math.min(100, m.slowPct) / 100.0 : 0;
        var cur = inst.getModifier(SLOW_ID);
        if (cur != null && Math.abs(cur.getAmount() - want) < 1e-6) return;
        if (cur != null) inst.removeModifier(SLOW_ID);
        if (want != 0) inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SLOW_ID, "rpg slow", want,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (want <= -1) mob.getNavigation().stop();
    }

    /** 도발한 플레이어 (끝났거나 없으면 null) */
    public static Player taunter(Mob mob, MobData m, long now) {
        if (m.tauntBy == null) return null;
        if (now >= m.tauntUntil) { m.tauntBy = null; return null; }
        Player p = mob.level().getPlayerByUUID(m.tauntBy);
        if (p == null || !p.isAlive() || p.isSpectator() || p.distanceToSqr(mob) > 32 * 32) { m.tauntBy = null; return null; }
        return p;
    }

    /** 도발: 이 몹이 duration 틱 동안 p 만 노림 */
    public static void taunt(Mob mob, MobData m, Player p, int ticks) {
        m.tauntBy = p.getUUID();
        m.tauntUntil = mob.level().getGameTime() + ticks;
        mob.setTarget(p);
    }

    /** 몸 바깥끼리 거리 */
    public static double edge(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz) - a.getBbWidth() / 2 - b.getBbWidth() / 2;
    }

    private static boolean valid(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && !rpg.core.skill.JobSkills.hidden(p);
    }

    private static Player nearest(Mob mob, double r) {
        Player best = null;
        double bd = r * r;
        for (Player p : mob.level().players()) {
            if (!valid(p)) continue;
            double d = p.distanceToSqr(mob);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ───────── 예고 시작 ─────────
    private static void start(Mob mob, MobData m, Balance.MobSkill sk, int idx, Player t, long now) {
        int delay = (int) Math.max(4, Math.round(sk.delay() * 20));
        m.casting = idx;
        m.castEnd = now + delay;
        m.castAnim = false;
        m.skillReady[idx] = now + delay + Math.round(sk.cd() * 20);
        Vec3 dir = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
        m.castDir = dir.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, mob.getYRot()) : dir.normalize();
        int shape;
        double a, b;
        switch (sk.type()) {
            case "leap", "zone" -> { m.castPos = t.position(); shape = CIRCLE; a = sk.r(); b = 0; }
            case "dash", "shot" -> { m.castPos = mob.position(); shape = LINE; a = sk.len(); b = sk.w(); }
            case "cone" -> { m.castPos = mob.position(); shape = CONE; a = sk.r(); b = sk.angle(); }
            default -> { m.castPos = mob.position(); shape = CIRCLE; a = sk.r(); b = 0; }   // slam
        }
        Net.telegraph((ServerLevel) mob.level(), m.castPos, m.castDir, shape, a, b, delay);
        m.castExtra = null;
        if ("zone".equals(sk.type()) && sk.count() > 1) {   // 장판 여러 개: 대상 주변에 흩어서
            m.castExtra = new Vec3[sk.count() - 1];
            var rnd = mob.getRandom();
            for (int i = 0; i < m.castExtra.length; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2, dd = sk.r() * (1.4 + rnd.nextDouble() * 0.8);
                m.castExtra[i] = t.position().add(Math.cos(ang) * dd, 0, Math.sin(ang) * dd);
                Net.telegraph((ServerLevel) mob.level(), m.castExtra[i], m.castDir, CIRCLE, sk.r(), 0, delay);
            }
        }
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 0.8f, 0.6f);
    }

    // ───────── 발동 ─────────
    private static void execute(Mob mob, MobData m, Balance.MobSkill sk) {
        ServerLevel l = (ServerLevel) mob.level();
        Vec3 p0 = m.castPos, dir = m.castDir;
        if (p0 == null || dir == null) return;
        int hits = Math.max(1, sk.hits());
        double pct = sk.pct() * hits;
        switch (sk.type()) {
            case "leap" -> {
                mob.teleportTo(p0.x, p0.y, p0.z);
                l.sendParticles(ParticleTypes.EXPLOSION, p0.x, p0.y + 0.2, p0.z, 2, sk.r() / 3, 0.1, sk.r() / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.5f, 1.4f);
                hitCircle(mob, p0, sk.r(), pct, sk.effect());
            }
            case "zone" -> {
                l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, p0.x, p0.y + 0.5, p0.z, 40, sk.r() / 2, 0.3, sk.r() / 2, 0);
                l.sendParticles(ParticleTypes.SNEEZE, p0.x, p0.y + 0.3, p0.z, 20, sk.r() / 2, 0.2, sk.r() / 2, 0.02);
                l.playSound(null, net.minecraft.core.BlockPos.containing(p0), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.HOSTILE, 0.8f, 0.8f);
                hitCircle(mob, p0, sk.r(), pct, sk.effect());
                if (m.castExtra != null) for (Vec3 q : m.castExtra) {
                    l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, q.x, q.y + 0.5, q.z, 40, sk.r() / 2, 0.3, sk.r() / 2, 0);
                    l.sendParticles(ParticleTypes.SNEEZE, q.x, q.y + 0.3, q.z, 20, sk.r() / 2, 0.2, sk.r() / 2, 0.02);
                    hitCircle(mob, q, sk.r(), pct, sk.effect());
                }
                m.castExtra = null;
            }
            case "dash" -> {
                mob.setDeltaMovement(dir.x * Math.min(3.0, sk.len() / 3.0), 0.15, dir.z * Math.min(3.0, sk.len() / 3.0));
                mob.hurtMarked = true;
                trail(l, p0, dir, sk.len(), ParticleTypes.CLOUD);
                l.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.4f, 1.6f);
                hitLine(mob, p0, dir, sk.len(), sk.w(), pct, sk.effect());
            }
            case "shot" -> {
                trail(l, p0.add(0, 1.2, 0), dir, sk.len(), ParticleTypes.CRIT);
                l.playSound(null, mob.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1f, 0.8f);
                hitLine(mob, p0, dir, sk.len(), sk.w(), pct, sk.effect());
            }
            case "cone" -> {
                for (int i = 0; i < hits; i++) {
                    Vec3 c = p0.add(dir.scale(sk.r() * 0.5));
                    l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, p0.y + 1, c.z, 2, 0.6, 0.2, 0.6, 0);
                }
                l.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1f, 0.7f);
                hitCone(mob, p0, dir, sk.r(), sk.angle(), pct, sk.effect());
            }
            default -> {   // slam
                l.sendParticles(ParticleTypes.EXPLOSION, p0.x, p0.y + 0.2, p0.z, 3, sk.r() / 3, 0.1, sk.r() / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6f, 1.2f);
                hitCircle(mob, p0, sk.r(), pct, sk.effect());
            }
        }
    }

    private static void trail(ServerLevel l, Vec3 from, Vec3 dir, double len,
                              net.minecraft.core.particles.SimpleParticleType fx) {
        for (double d = 0; d <= len; d += 0.7) {
            Vec3 q = from.add(dir.scale(d));
            l.sendParticles(fx, q.x, q.y + 0.4, q.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    // ───────── 맞았는지 ─────────
    private static List<ServerPlayer> players(Mob mob, Vec3 c, double r) {
        return mob.level().getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(c, c).inflate(r + 2, 3, r + 2),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
    }

    private static void hitCircle(Mob mob, Vec3 c, double r, double pct, String effect) {
        for (ServerPlayer p : players(mob, c, r)) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            if (dx * dx + dz * dz <= (r + 0.3) * (r + 0.3) && Math.abs(p.getY() - c.y) < 2.5) hit(mob, p, pct, effect);
        }
    }

    private static void hitLine(Mob mob, Vec3 s, Vec3 dir, double len, double w, double pct, String effect) {
        for (ServerPlayer p : players(mob, s.add(dir.scale(len / 2)), len / 2 + w)) {
            Vec3 to = new Vec3(p.getX() - s.x, 0, p.getZ() - s.z);
            double along = to.dot(dir);
            double side = Math.abs(to.x * dir.z - to.z * dir.x);
            if (along >= -0.5 && along <= len + 0.3 && side <= w / 2 + 0.3 && Math.abs(p.getY() - s.y) < 2.5) hit(mob, p, pct, effect);
        }
    }

    private static void hitCone(Mob mob, Vec3 c, Vec3 dir, double r, double angle, double pct, String effect) {
        double cos = Math.cos(Math.toRadians(angle / 2));
        for (ServerPlayer p : players(mob, c, r)) {
            Vec3 to = new Vec3(p.getX() - c.x, 0, p.getZ() - c.z);
            double d = to.length();
            if (d > r + 0.3 || Math.abs(p.getY() - c.y) >= 2.5) continue;
            if (d < 0.6 || to.normalize().dot(dir) >= cos) hit(mob, p, pct, effect);
        }
    }

    private static void hit(Mob mob, ServerPlayer p, double pct, String effect) {
        CombatEvents.mobSkillHit(mob, p, pct);
        if ("slow".equals(effect)) {   // 무게중심: 둔화 시간 줄어듦
            double resist = Math.min(100, rpg.core.data.RpgData.get(p).bonus("kbResist"));
            int ticks = (int) Math.round(60 * (1 - resist / 100.0));
            if (ticks > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
        }
    }
}
