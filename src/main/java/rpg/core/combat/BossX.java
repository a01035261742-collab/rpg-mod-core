package rpg.core.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/**
 * 보스 추가 기믹 (어둠거미여왕): 알 낳기 · 알 흡수 · 고치 · 독 · 실 당기기 · 새끼 거미 떼 · 둥지 붕괴 · 광폭 연타.
 * 상태는 Boss.State.x 에 (보스마다).
 */
final class BossX {
    private BossX() {}

    static final Set<String> TYPES = Set.of("eggs", "pull", "lanes", "absorb", "collapse", "frenzy");

    record Cocoon(UUID entity, Vec3 pos, long until) {}
    record Zone(Vec3 c, Vec3 dir, double r, double angle, long until) {}

    static final class X {
        final Map<UUID, Long> hatch = new HashMap<>();      // 알 → 부화 시각
        final Map<UUID, Long> absorb = new HashMap<>();     // 알 → 흡수 시각
        final List<UUID> adds = new ArrayList<>();          // 새끼 거미
        final Map<UUID, Cocoon> cocoons = new HashMap<>();  // 플레이어 → 고치
        final List<Zone> zones = new ArrayList<>();         // 독 구역 (둥지 붕괴)
        int step;
        int safeLane;
        double baseAngle;
        Vec3 axis = new Vec3(1, 0, 0);
        UUID mob;
        int level;
    }

    /** 독 (플레이어): 끝나는 시각 */
    private static final Map<UUID, Long> POISON = new HashMap<>();

    static void poison(ServerPlayer p, double sec, long now) {
        if (sec <= 0) return;
        POISON.merge(p.getUUID(), now + Math.round(sec * 20), Math::max);
    }

    // ───────── 매 틱 (패턴과 상관없이) ─────────
    static void ambient(Mob mob, MobData m, Boss.State st, Boss.Def def, ServerLevel l, long now) {
        X x = st.x;
        x.mob = mob.getUUID();
        x.level = m.level;
        // 독: 1초마다 최대 HP %
        if (now % 20 == 0 && !POISON.isEmpty()) {
            for (ServerPlayer p : Boss.arena(l, st, 8)) {
                Long until = POISON.get(p.getUUID());
                if (until == null) continue;
                if (now >= until) { POISON.remove(p.getUUID()); continue; }
                if (!Boss.valid(p)) continue;
                CombatEvents.maxHpHit(mob, p, def.g("poisonPct", 2));
                l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1, p.getZ(), 4, 0.3, 0.4, 0.3, 0);
            }
        }
        // 알: 부화 · 흡수
        for (var it = x.hatch.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity egg = l.getEntity(e.getKey());
            if (egg == null || !egg.isAlive()) { it.remove(); continue; }
            if (now % 10 == 0) l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, egg.getX(), egg.getY() + 0.8, egg.getZ(), 2, 0.3, 0.3, 0.3, 0);
            if (now < e.getValue()) continue;
            it.remove();
            int n = (int) def.g("hatchCount", 4);
            for (int i = 0; i < n; i++) {
                Mob s = Mobs.spawn(l, "spiderling", m.level, egg.blockPosition(), "boss", false);
                if (s == null) continue;
                s.getPersistentData().putBoolean("rpgPillar", true);   // 보상 없음
                x.adds.add(s.getUUID());
            }
            l.playSound(null, egg.blockPosition(), SoundEvents.TURTLE_EGG_HATCH, SoundSource.HOSTILE, 2f, 0.6f);
            egg.discard();
            for (ServerPlayer p : Boss.arena(l, st, 16))
                p.displayClientMessage(Component.literal("알이 부화했어요! 새끼 거미 " + n + "마리").withStyle(ChatFormatting.RED), true);
        }
        for (var it = x.absorb.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity egg = l.getEntity(e.getKey());
            if (egg == null || !egg.isAlive()) { it.remove(); continue; }
            if (now % 4 == 0) {   // 여왕 쪽으로 빨려가는 줄
                Vec3 a = egg.position().add(0, 0.6, 0), b = mob.position().add(0, 1, 0);
                for (int k = 0; k <= 8; k++) { Vec3 q = a.lerp(b, k / 8.0); l.sendParticles(ParticleTypes.HAPPY_VILLAGER, q.x, q.y, q.z, 1, 0, 0, 0, 0); }
            }
            if (now < e.getValue()) continue;
            it.remove();
            double heal = m.maxHp * def.g("absorbHeal", 3) / 100.0;
            m.hp = Math.min(m.maxHp, m.hp + heal);
            Mobs.sync(mob);
            egg.discard();
            l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.HOSTILE, 2f, 0.5f);
            for (ServerPlayer p : Boss.arena(l, st, 16))
                p.sendSystemMessage(Component.literal(m.name + "이(가) 알을 흡수했어요! 체력 +" + (int) def.g("absorbHeal", 3) + "%").withStyle(ChatFormatting.DARK_GREEN));
        }
        // 고치: 묶어두기 · 풀기
        for (var it = x.cocoons.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = (ServerPlayer) l.getPlayerByUUID(e.getKey());
            Cocoon c = e.getValue();
            Entity ce = l.getEntity(c.entity());
            boolean done = p == null || !p.isAlive() || RpgData.get(p).isDown() || now >= c.until() || ce == null || !ce.isAlive();
            if (done) {
                if (ce != null) ce.discard();
                if (p != null) p.displayClientMessage(Component.literal("고치에서 풀려났어요").withStyle(ChatFormatting.GREEN), true);
                it.remove();
                continue;
            }
            if (p.position().distanceToSqr(c.pos()) > 0.09) { p.teleportTo(c.pos().x, c.pos().y, c.pos().z); p.setDeltaMovement(Vec3.ZERO); }
            if (now % 10 == 0) {
                p.displayClientMessage(Component.literal("고치에 묶였어요! 고치를 연타 (파티원이 때려도 풀려요)  " + (c.until() - now + 19) / 20 + "초").withStyle(ChatFormatting.WHITE), true);
                l.sendParticles(ParticleTypes.ITEM_SNOWBALL, c.pos().x, c.pos().y + 1, c.pos().z, 4, 0.3, 0.6, 0.3, 0);
            }
        }
        // 독 구역 (둥지 붕괴): 0.5초마다
        x.zones.removeIf(z -> now >= z.until());
        if (now % 10 == 0 && !x.zones.isEmpty()) {
            for (ServerPlayer p : Boss.arena(l, st, 4)) {
                if (!Boss.valid(p)) continue;
                for (Zone z : x.zones) {
                    if (!inSector(p.position(), z)) continue;
                    CombatEvents.maxHpHit(mob, p, def.g("zonePct", 8));
                    l.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 0.5, p.getZ(), 6, 0.3, 0.2, 0.3, 0);
                    break;
                }
            }
        }
        x.adds.removeIf(id -> { Entity a = l.getEntity(id); return a == null || !a.isAlive(); });
    }

    private static boolean inSector(Vec3 p, Zone z) {
        Vec3 to = new Vec3(p.x - z.c().x, 0, p.z - z.c().z);
        double d = to.length();
        if (d > z.r()) return false;
        if (d < 0.5) return true;
        return to.normalize().dot(z.dir()) >= Math.cos(Math.toRadians(z.angle() / 2));
    }

    /** 고치: 플레이어 자리에 거미줄 고치 몹 (때리면 풀림, 혼자면 본인이 연타) */
    static void cocoon(Mob boss, MobData m, ServerLevel l, ServerPlayer p, double sec, long now) {
        if (!Boss.valid(p) || sec <= 0) return;
        Boss.State st = Boss.stateOf(boss);
        if (st == null || st.x.cocoons.containsKey(p.getUUID())) return;
        Mob c = Mobs.spawn(l, "web_cocoon", m.level, p.blockPosition(), "boss", false);
        if (c == null) return;
        c.moveTo(p.getX(), p.getY(), p.getZ(), 0, 0);
        c.setNoAi(true);
        c.setInvisible(true);
        c.setSilent(true);
        c.getPersistentData().putBoolean("rpgPillar", true);
        c.getPersistentData().putUUID("rpgCocoon", p.getUUID());
        st.x.cocoons.put(p.getUUID(), new Cocoon(c.getUUID(), p.position(), now + Math.round(sec * 20)));
        l.playSound(null, p.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.5f, 1.5f);
    }

    /** 고치를 때림: 본인 = 10번, 다른 사람 = 3번이면 풀림 */
    static double cocoonHit(Mob c, MobData cm, ServerPlayer attacker) {
        UUID victim = c.getPersistentData().getUUID("rpgCocoon");
        return cm.maxHp * (attacker.getUUID().equals(victim) ? 0.101 : 0.34);
    }

    /** 부채꼴 (독니) */
    static void cone(Mob mob, MobData m, Boss.State st, ServerLevel l, Boss.Pattern p, Vec3 c, Vec3 dir) {
        Vec3 q = c.add(dir.scale(p.r() * 0.5));
        l.sendParticles(ParticleTypes.SWEEP_ATTACK, q.x, c.y + 1, q.z, 6, p.r() / 3, 0.2, p.r() / 3, 0);
        l.sendParticles(ParticleTypes.ITEM_SLIME, q.x, c.y + 1, q.z, 12, p.r() / 3, 0.2, p.r() / 3, 0);
        l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 1.5f, 0.5f);
        double cos = Math.cos(Math.toRadians(p.angle() / 2));
        for (ServerPlayer pl : Boss.arena(l, st, 4)) {
            if (!Boss.valid(pl)) continue;
            Vec3 to = new Vec3(pl.getX() - c.x, 0, pl.getZ() - c.z);
            double dd = to.length();
            if (dd > p.r() + Boss.PAD || Math.abs(pl.getY() - c.y) > Boss.HEIGHT) continue;
            if (dd < 0.8 || to.normalize().dot(dir) >= cos) Boss.hit(mob, m, st, l, pl, p);
        }
    }

    private static void eggsAt(Mob mob, MobData m, Boss.State st, ServerLevel l, int n, Map<UUID, Long> into, long when, boolean scale) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = 6 + r.nextDouble() * Math.max(1, st.radius * 0.7 - 6);
            BlockPos at = BlockPos.containing(st.center.x + Math.cos(ang) * dist, st.center.y, st.center.z + Math.sin(ang) * dist);
            Mob egg = Mobs.spawn(l, "spider_egg", m.level, at, "boss", false);
            if (egg == null) continue;
            egg.setNoAi(true);
            egg.getPersistentData().putBoolean("rpgPillar", true);
            MobData em = Combat.mob(egg);
            int people = Math.max(1, mob.getPersistentData().getInt("rpgRaidPeople"));
            if (em != null && scale) { em.maxHp *= people; em.hp = em.maxHp; Mobs.sync(egg); }
            into.put(egg.getUUID(), when);
            l.sendParticles(ParticleTypes.POOF, egg.getX(), egg.getY() + 0.5, egg.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
        }
    }

    // ───────── 패턴 시작 ─────────
    static void start(Mob mob, Boss.State st, Boss.Def def, Boss.Pattern p, ServerPlayer t, ServerLevel l, long now) {
        MobData m = Combat.mob(mob);
        X x = st.x;
        x.step = 0;
        st.stage = 1;
        switch (p.type()) {
            case "eggs" -> {
                eggsAt(mob, m, st, l, (int) p.x("count", 3), x.hatch, now + Math.round(p.x("hatch", 20) * 20), true);
                say(l, st, "◆ " + p.name() + " — " + (int) p.x("hatch", 20) + "초 안에 알을 깨세요!", ChatFormatting.GOLD);
                l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2f, 0.4f);
                st.stageEnd = now + 30;
            }
            case "absorb" -> {
                eggsAt(mob, m, st, l, (int) p.x("count", 2), x.absorb, now + Math.round(p.x("absorbSec", 8) * 20), true);
                say(l, st, "◆ " + p.name() + " — " + (int) p.x("absorbSec", 8) + "초 뒤 여왕이 알을 흡수해요! 깨세요", ChatFormatting.DARK_GREEN);
                l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2f, 0.4f);
                st.stageEnd = now + 30;
            }
            case "pull" -> {
                int dl = Boss.ticks(p.delay());
                st.pos = mob.position();
                Net.telegraph(l, st.pos, st.dir, MobAI.CIRCLE, p.r(), 0, dl);
                say(l, st, "◆ " + p.name() + " — 중앙에서 멀어지세요! (" + (int) p.delay() + "초)", ChatFormatting.RED);
                l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2f, 0.3f);
                st.stageEnd = now + dl + Boss.HIT_LAG;
            }
            case "lanes" -> {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                double a = r.nextInt(4) * Math.PI / 2;
                x.axis = new Vec3(Math.cos(a), 0, Math.sin(a));
                x.safeLane = r.nextInt(3);
                int dl = Boss.ticks(p.delay());
                double R = st.radius, w = 2 * R / 3;
                Vec3 side = new Vec3(-x.axis.z, 0, x.axis.x);
                for (int i = 0; i < 3; i++) {
                    double off = (i - 1) * w;
                    Vec3 from = st.center.subtract(x.axis.scale(R)).add(side.scale(off));
                    Net.telegraph(l, from, x.axis, i == x.safeLane ? 30 + MobAI.LINE : MobAI.LINE, 2 * R, w, dl);
                }
                say(l, st, "◆ " + p.name() + " — 초록 줄로 피하세요!", ChatFormatting.RED);
                l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 2f, 0.5f);
                st.stageEnd = now + dl + Boss.HIT_LAG;
            }
            case "collapse" -> {
                x.baseAngle = ThreadLocalRandom.current().nextInt(4) * Math.PI / 2;
                say(l, st, "◆ " + p.name() + " — 둥지가 무너져요! 독 늪을 피해 돌아가세요", ChatFormatting.DARK_GREEN);
                collapseStep(mob, st, p, l, now);
            }
            case "frenzy" -> {
                st.repeatLeft = (int) p.x("hits", 3);
                frenzyFang(mob, st, p, t, l, now);
            }
            default -> st.stageEnd = now;
        }
    }

    private static Vec3 quarterDir(X x, int i) {
        double a = x.baseAngle + i * Math.PI / 2 + Math.PI / 4;
        return new Vec3(Math.cos(a), 0, Math.sin(a));
    }

    private static void collapseStep(Mob mob, Boss.State st, Boss.Pattern p, ServerLevel l, long now) {
        int dl = Boss.ticks(p.delay());
        Net.telegraph(l, st.center, quarterDir(st.x, st.x.step), MobAI.CONE, st.radius, 90, dl);
        l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1f, 0.5f);
        st.stageEnd = now + dl + Boss.HIT_LAG;
    }

    private static void frenzyFang(Mob mob, Boss.State st, Boss.Pattern p, ServerPlayer t, ServerLevel l, long now) {
        if (t != null) {
            Vec3 to = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
            if (to.lengthSqr() > 1e-4) st.dir = to.normalize();
        }
        st.pos = mob.position();
        int dl = Boss.ticks(p.x("fangDelay", 0.8));
        Net.telegraph(l, st.pos, st.dir, MobAI.CONE, p.r(), p.angle(), dl);
        l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1f, 0.6f);
        st.stageEnd = now + dl + Boss.HIT_LAG;
    }

    // ───────── 패턴 진행 ─────────
    static void run(Mob mob, MobData m, Boss.State st, Boss.Def def, ServerLevel l, long now) {
        Boss.Pattern p = st.cur;
        X x = st.x;
        mob.getNavigation().stop();
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(0, v.y, 0);
        if (p.type().equals("pull")) {   // 끌어당김 (걸으면 버팀)
            for (ServerPlayer pl : Boss.arena(l, st, 4)) {
                if (!Boss.valid(pl)) continue;
                Vec3 to = new Vec3(st.pos.x - pl.getX(), 0, st.pos.z - pl.getZ());
                if (to.lengthSqr() < 1) continue;
                // 공중에선 마찰이 없어서 확 끌려옴 → 공중일 땐 30%만, 그리고 끌리는 속도 상한
                double f = p.x("pull", 0.07) * (pl.onGround() ? 1.0 : 0.3);
                Vec3 d = to.normalize().scale(f);
                Vec3 v = pl.getDeltaMovement();
                double along = v.x * to.normalize().x + v.z * to.normalize().z;   // 이미 중앙 쪽으로 가는 속도
                if (along < 0.25) { pl.push(d.x, 0, d.z); pl.hurtMarked = true; }
            }
            if (now % 5 == 0) l.sendParticles(ParticleTypes.ITEM_SNOWBALL, st.pos.x, st.pos.y + 1, st.pos.z, 10, p.r() / 2, 0.5, p.r() / 2, 0.1);
        }
        if (now < st.stageEnd) return;
        switch (p.type()) {
            case "pull" -> {
                l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, st.pos.x, st.pos.y + 0.5, st.pos.z, 1, 0, 0, 0, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2f, 0.6f);
                Boss.hitCircle(mob, m, st, l, st.pos, p.r(), p);
                Boss.finish(mob, st, def, now);
            }
            case "lanes" -> {
                double R = st.radius, w = 2 * R / 3;
                Vec3 side = new Vec3(-x.axis.z, 0, x.axis.x);
                for (int i = 0; i < 3; i++) {
                    if (i == x.safeLane) continue;
                    double off = (i - 1) * w;
                    for (double a = -R; a <= R; a += 2) {
                        Vec3 q = st.center.add(x.axis.scale(a)).add(side.scale(off));
                        l.sendParticles(ParticleTypes.SMOKE, q.x, q.y + 0.4, q.z, 3, w / 4, 0.1, 0.5, 0.02);
                    }
                }
                l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2.5f, 1.4f);
                for (ServerPlayer pl : Boss.arena(l, st, 4)) {
                    if (!Boss.valid(pl)) continue;
                    Vec3 to = new Vec3(pl.getX() - st.center.x, 0, pl.getZ() - st.center.z);
                    double s = to.x * side.x + to.z * side.z;
                    int lane = (int) Math.floor((s + R) / w);
                    lane = Math.max(0, Math.min(2, lane));
                    if (lane != x.safeLane) Boss.hit(mob, m, st, l, pl, p);
                }
                Boss.finish(mob, st, def, now);
            }
            case "collapse" -> {
                Vec3 d = quarterDir(x, x.step);
                long linger = Math.round(p.x("linger", 3) * 20);
                x.zones.add(new Zone(st.center, d, st.radius, 90, now + linger));
                Net.telegraph(l, st.center, d, 50 + MobAI.CONE, st.radius, 90, (int) linger);   // 독 늪 (탁한 황록)
                l.playSound(null, mob.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 2f, 0.5f);
                // 막 무너진 칸 안에 있던 사람은 바로 한 번 맞음
                for (ServerPlayer pl : Boss.arena(l, st, 4))
                    if (Boss.valid(pl) && inSector(pl.position(), x.zones.get(x.zones.size() - 1))) Boss.hit(mob, m, st, l, pl, p);
                if (++x.step < 4) collapseStep(mob, st, p, l, now);
                else Boss.finish(mob, st, def, now);
            }
            case "frenzy" -> {
                if (st.repeatLeft > 0) {   // 독니
                    cone(mob, m, st, l, p, st.pos, st.dir);
                    if (--st.repeatLeft > 0) frenzyFang(mob, st, p, Boss.currentTarget(l, st), l, now);
                    else {   // 마지막: 경기장 전체 + 여왕 주변 안전 원
                        int dl = Boss.ticks(p.x("finalDelay", 2));
                        st.pos = mob.position();
                        Net.telegraph(l, st.center, st.dir, MobAI.CIRCLE, st.radius + 2, 0, dl);
                        Net.telegraph(l, st.pos, st.dir, 30 + MobAI.CIRCLE, p.x("safeR", 6), 0, dl);
                        say(l, st, "◆ 여왕에게 붙으세요! (초록 원만 안전)", ChatFormatting.RED);
                        st.stageEnd = now + dl + Boss.HIT_LAG;
                        st.repeatLeft = -1;
                    }
                    return;
                }
                l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, st.center.x, st.center.y + 0.5, st.center.z, 3, st.radius / 3, 0.2, st.radius / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.5f, 0.5f);
                for (ServerPlayer pl : Boss.arena(l, st, 4)) {
                    if (!Boss.valid(pl) || Boss.flat(pl.position(), st.pos) <= p.x("safeR", 6) + Boss.PAD) continue;
                    CombatEvents.mobSkillHit(mob, pl, p.x("finalPct", 300));
                }
                Boss.finish(mob, st, def, now);
            }
            default -> Boss.finish(mob, st, def, now);   // eggs · absorb: 바로 다음 패턴 (알은 따로 돌아감)
        }
    }

    private static void say(ServerLevel l, Boss.State st, String s, ChatFormatting c) {
        for (ServerPlayer p : Boss.arena(l, st, 16)) Net.alert(p, s, c, 70);   // 화면 위 가운데
    }

    /** 리셋·처치·사라짐: 알·새끼·고치·독 구역 정리 */
    static void clear(ServerLevel l, Boss.State st) {
        X x = st.x;
        for (UUID id : new ArrayList<>(x.hatch.keySet())) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
        for (UUID id : new ArrayList<>(x.absorb.keySet())) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
        for (UUID id : x.adds) { Entity e = l.getEntity(id); if (e != null) e.discard(); }
        for (Cocoon c : x.cocoons.values()) { Entity e = l.getEntity(c.entity()); if (e != null) e.discard(); }
        x.hatch.clear(); x.absorb.clear(); x.adds.clear(); x.cocoons.clear(); x.zones.clear();
        for (ServerPlayer p : Boss.arena(l, st, 24)) POISON.remove(p.getUUID());   // 독도 끝
    }
}
