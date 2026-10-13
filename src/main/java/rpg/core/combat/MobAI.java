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

    private static GoalSelector selector(Mob mob, int idx) {
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
        if (def.skills().isEmpty() && !def.look().chase()) return;
        if (mob.tickCount % 5 != 0) return;

        Player t = mob.getTarget() instanceof Player p && valid(p) ? p : null;
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
            start(mob, m, sk, i, t, now);
            return;
        }
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
        if ("slow".equals(effect)) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
    }
}
