package rpg.core.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.Progress;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/**
 * 보스 (mobs.json 의 "boss" 블록이 있는 몹).
 * 경기장 중앙·반경 안에서 싸움, 보스바, 카드 더미 패턴, 2페이즈, 레벨 차이 피해 감소,
 * 피해 비율 보상 분배, 참여 인원수만큼 드롭 세트, 아무도 없으면 초기화.
 * 패턴 종류: cone(앞 부채꼴) · slam(주변 원) · charge(대상 추적 직선 → 돌진) · rocks(모든 플레이어 발밑 추적 원) · roar(주변 원, 피해 없이 밀어냄)
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Boss {
    private Boss() {}

    public record Pattern(String id, String name, String type, double r, double angle, double len, double w,
                          double track, double delay, double pct, double push, int phase, int repeat2) {}

    public record Mats(String mob, int times) {}

    public record Def(String key, double arena, int respawnSec, int resetSec, double phase2, double[] wait1, double[] wait2,
                      double penaltyPerLevel, double maxPenalty, List<Pattern> patterns,
                      String sure, int fragments, double scrollChance, String ring, double ringChance, List<Mats> mats, String bar) {}

    public static final Map<String, Def> DEFS = new HashMap<>();

    // ───────── 읽기 (Balance 가 mobs.json 읽을 때) ─────────
    public static void clear() { DEFS.clear(); }

    public static void define(String key, JsonObject o) {
        try {
            List<Pattern> ps = new ArrayList<>();
            for (var x : o.getAsJsonArray("patterns")) {
                JsonObject p = x.getAsJsonObject();
                ps.add(new Pattern(s(p, "id", "p" + ps.size()), s(p, "name", "?"), s(p, "type", "slam"), d(p, "r", 4), d(p, "angle", 90),
                        d(p, "len", 10), d(p, "w", 3), d(p, "track", 0), d(p, "delay", 2), d(p, "pct", 100), d(p, "push", 0),
                        (int) d(p, "phase", 1), (int) d(p, "repeat2", 1)));
            }
            JsonObject rw = o.has("reward") ? o.getAsJsonObject("reward") : new JsonObject();
            List<Mats> mats = new ArrayList<>();
            if (rw.has("mats")) for (var x : rw.getAsJsonArray("mats")) {
                JsonArray a = x.getAsJsonArray();
                mats.add(new Mats(a.get(0).getAsString(), a.get(1).getAsInt()));
            }
            DEFS.put(key, new Def(key, d(o, "arena", 24), (int) d(o, "respawnSec", 600), (int) d(o, "resetSec", 30), d(o, "phase2", 50),
                    pair(o, "wait1", 2, 4), pair(o, "wait2", 1.5, 3), d(o, "penaltyPerLevel", 5), d(o, "maxPenalty", 90), ps,
                    s(rw, "sure", ""), (int) d(rw, "fragments", 0), d(rw, "scrollChance", 0), s(rw, "ring", ""), d(rw, "ringChance", 0), mats,
                    s(o, "bar", "")));
        } catch (Exception ex) {
            RpgCore.LOG.error("[rpgcore] 보스 {} 읽기 실패", key, ex);
        }
    }

    private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }
    private static String s(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }
    private static double[] pair(JsonObject o, String k, double a, double b) {
        if (!o.has(k)) return new double[]{a, b};
        JsonArray x = o.getAsJsonArray(k);
        return new double[]{x.get(0).getAsDouble(), x.get(1).getAsDouble()};
    }

    public static boolean isBoss(MobData m) { return m != null && DEFS.containsKey(m.key); }

    // ───────── 상태 (저장 안 함) ─────────
    static final class State {
        boolean init;
        Vec3 center;
        double radius;
        String point = "";
        int phase = 1;
        List<String> deck = new ArrayList<>();
        String last = "";
        long nextAt;
        Pattern cur;
        int stage;            // 0 추적 · 1 확정 · 2 돌진 중
        long stageEnd;
        int repeatLeft;
        UUID target;
        Vec3 pos, dir;
        final Map<UUID, Vec3> rocks = new LinkedHashMap<>();
        final Map<UUID, Double> dmg = new HashMap<>();
        long emptySince = -1;
        ServerBossEvent bar;
        Vec3 dashFrom;
        int dashTick;
        final java.util.Set<UUID> dashHit = new java.util.HashSet<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(Mob mob, MobData m, Def def) {
        State st = STATES.computeIfAbsent(mob.getUUID(), k -> new State());
        if (!st.init) {
            st.init = true;
            var pd = mob.getPersistentData();
            st.center = pd.contains("rpgBossCX") ? new Vec3(pd.getDouble("rpgBossCX"), pd.getDouble("rpgBossCY"), pd.getDouble("rpgBossCZ")) : mob.position();
            st.radius = pd.contains("rpgBossR") ? pd.getDouble("rpgBossR") : def.arena();
            st.point = pd.getString("rpgBossPoint");
            if (!pd.contains("rpgBossCX")) { pd.putDouble("rpgBossCX", st.center.x); pd.putDouble("rpgBossCY", st.center.y); pd.putDouble("rpgBossCZ", st.center.z); }
            st.phase = m.hp <= m.maxHp * def.phase2() / 100.0 ? 2 : 1;
            // 바닐라 AI 전부 끄기 (움직임·패턴은 여기서)
            var goals = MobAI.selector(mob, 0);
            var targets = MobAI.selector(mob, 1);
            if (goals != null) goals.removeAllGoals(g -> true);
            if (targets != null) targets.removeAllGoals(g -> true);
            mob.setTarget(null);
            var kb = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1.0);
            st.bar = new ServerBossEvent(Component.literal(barName(m, def)), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            if (st.phase == 2) st.bar.setColor(BossEvent.BossBarColor.PURPLE);
            st.nextAt = mob.level().getGameTime() + 40;
        }
        return st;
    }

    private static String barName(MobData m, Def def) {
        return (def.bar().isEmpty() ? m.name : def.bar()) + " Lv." + m.level;
    }

    private static boolean valid(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && !RpgData.get(p).isDown();
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static List<ServerPlayer> arena(ServerLevel l, State st, double extra) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : l.players()) if (flat(p.position(), st.center) <= st.radius + extra && Math.abs(p.getY() - st.center.y) < 24) out.add(p);
        return out;
    }

    // ───────── 매 틱 (MobAI 에서 부름) ─────────
    public static void tick(Mob mob, MobData m, Balance.MobDef mdef) {
        Def def = DEFS.get(m.key);
        if (def == null) return;
        ServerLevel l = (ServerLevel) mob.level();
        State st = state(mob, m, def);
        long now = l.getGameTime();

        List<ServerPlayer> fighters = new ArrayList<>();
        for (ServerPlayer p : arena(l, st, 0)) if (valid(p)) fighters.add(p);

        if (mob.tickCount % 10 == 0) updateBar(l, st, m);

        // 아무도 없으면 일정 시간 뒤 초기화
        if (fighters.isEmpty()) {
            if (st.emptySince < 0) st.emptySince = now;
            else if (now - st.emptySince > def.resetSec() * 20L) reset(mob, m, st);
            mob.getNavigation().stop();
            if (st.cur != null) finish(mob, st, def, now);
            return;
        }
        st.emptySince = -1;

        // 2페이즈
        if (st.phase == 1 && m.hp <= m.maxHp * def.phase2() / 100.0) {
            st.phase = 2;
            st.deck.clear();
            st.bar.setColor(BossEvent.BossBarColor.PURPLE);
            for (ServerPlayer p : arena(l, st, 16))
                p.sendSystemMessage(Component.literal("[" + m.name + "] 분노했어요! 패턴이 더 거세져요").withStyle(ChatFormatting.LIGHT_PURPLE));
            l.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.6f);
        }

        if (st.cur != null) { run(mob, m, st, def, l, now); return; }

        // 경기장 밖으로 나가면 중앙으로
        if (flat(mob.position(), st.center) > st.radius + 2) {
            mob.teleportTo(st.center.x, st.center.y, st.center.z);
            mob.getNavigation().stop();
        }

        ServerPlayer t = pickTarget(mob, m, st, fighters, now);
        if (t == null) return;
        st.target = t.getUUID();
        if (now < st.nextAt || now < m.stunUntil) {
            if (mob.tickCount % 10 == 0) {
                if (mob.distanceTo(t) > 5) mob.getNavigation().moveTo(t, 1.0);
                else mob.getNavigation().stop();
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            return;
        }
        Pattern p = draw(st, def);
        if (p != null) start(mob, st, def, p, t, fighters, l, now);
    }

    /** 대상: 도발 > 피해를 가장 많이 준 사람 > 가까운 사람 */
    private static ServerPlayer pickTarget(Mob mob, MobData m, State st, List<ServerPlayer> fighters, long now) {
        Player tt = MobAI.taunter(mob, m, now);
        if (tt instanceof ServerPlayer sp && fighters.contains(sp)) return sp;
        ServerPlayer best = null;
        double bd = -1;
        for (ServerPlayer p : fighters) {
            double v = st.dmg.getOrDefault(p.getUUID(), 0.0);
            if (v > bd) { bd = v; best = p; }
        }
        if (bd > 0) return best;
        double near = Double.MAX_VALUE;
        for (ServerPlayer p : fighters) {
            double dd = p.distanceToSqr(mob);
            if (dd < near) { near = dd; best = p; }
        }
        return best;
    }

    /** 카드 더미: 지금 페이즈 패턴을 한 번씩 다 쓰면 다시 섞음, 같은 패턴 연속 없음 */
    private static Pattern draw(State st, Def def) {
        if (st.deck.isEmpty()) {
            for (Pattern p : def.patterns()) if (p.phase() <= st.phase) st.deck.add(p.id());
            Collections.shuffle(st.deck);
            if (st.deck.size() > 1 && st.deck.get(0).equals(st.last)) Collections.swap(st.deck, 0, st.deck.size() - 1);
        }
        if (st.deck.isEmpty()) return null;
        String id = st.deck.remove(0);
        st.last = id;
        for (Pattern p : def.patterns()) if (p.id().equals(id)) return p;
        return null;
    }

    // ───────── 패턴 ─────────
    private static int ticks(double sec) { return (int) Math.max(4, Math.round(sec * 20)); }

    /** 돌진 이동 시간 (틱) */
    private static final int DASH_TICKS = 10;

    private static void start(Mob mob, State st, Def def, Pattern p, ServerPlayer t, List<ServerPlayer> fighters, ServerLevel l, long now) {
        st.cur = p;
        st.stage = 1;
        mob.getNavigation().stop();
        Vec3 to = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
        st.dir = to.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, mob.getYRot()) : to.normalize();
        st.pos = mob.position();
        int dl = ticks(p.delay());
        switch (p.type()) {
            case "cone" -> Net.telegraph(l, st.pos, st.dir, MobAI.CONE, p.r(), p.angle(), dl);
            case "charge" -> { st.repeatLeft = st.phase >= 2 ? Math.max(1, p.repeat2()) : 1; beginCharge(mob, st, p, t, now); return; }
            case "rocks" -> {
                st.rocks.clear();
                for (ServerPlayer f : fighters) st.rocks.put(f.getUUID(), f.position());
                st.stage = 0;
                st.stageEnd = now + ticks(p.track());
                for (ServerPlayer f : fighters) Net.track(l, mob.getId(), f.getId(), MobAI.CIRCLE, p.r(), 0, ticks(p.track()));
                standing(mob, true);
                return;
            }
            case "roar" -> { Net.telegraph(l, st.pos, st.dir, MobAI.CIRCLE, p.r(), 0, dl); standing(mob, true); }
            default -> { Net.telegraph(l, st.pos, st.dir, MobAI.CIRCLE, p.r(), 0, dl); standing(mob, true); }   // slam
        }
        st.stageEnd = now + dl + HIT_LAG;
        l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1f, 0.5f);
    }

    /** 판정을 화면보다 조금 늦게 (빨강이 다 찬 걸 보고 맞게) */
    private static final int HIT_LAG = 2;
    /** 판정 여유: 몸 폭 0.3 + 0.3 */
    private static final double PAD = 0.6, HEIGHT = 4;

    private static void beginCharge(Mob mob, State st, Pattern p, ServerPlayer t, long now) {
        st.target = t == null ? st.target : t.getUUID();
        st.stage = 0;
        st.stageEnd = now + ticks(p.track());
        st.pos = mob.position();
        if (t != null) Net.track((ServerLevel) mob.level(), mob.getId(), t.getId(), MobAI.LINE, p.len(), p.w(), ticks(p.track()));
    }

    private static void standing(Mob mob, boolean on) {
        if (mob instanceof net.minecraft.world.entity.animal.PolarBear b) b.setStanding(on);
    }

    private static void run(Mob mob, MobData m, State st, Def def, ServerLevel l, long now) {
        Pattern p = st.cur;
        // 돌진 중: 직선을 따라 이동, 보스 몸이 지나가는 자리의 플레이어가 맞음 (한 사람당 1번)
        if (st.stage == 2) {
            double before = p.len() * Math.min(1, st.dashTick / (double) DASH_TICKS);
            st.dashTick++;
            double f = Math.min(1, st.dashTick / (double) DASH_TICKS);
            double along = p.len() * f;
            Vec3 q = st.dashFrom.add(st.dir.scale(along));
            mob.moveTo(q.x, st.dashFrom.y, q.z, mob.getYRot(), mob.getXRot());
            mob.setDeltaMovement(Vec3.ZERO);
            l.sendParticles(ParticleTypes.CLOUD, q.x, q.y + 0.3, q.z, 3, 0.6, 0.1, 0.6, 0.01);
            double half = mob.getBbWidth() / 2;
            for (ServerPlayer pl : arena(l, st, 4)) {
                if (!valid(pl) || st.dashHit.contains(pl.getUUID())) continue;
                Vec3 to = new Vec3(pl.getX() - st.dashFrom.x, 0, pl.getZ() - st.dashFrom.z);
                double a = to.dot(st.dir), side = to.x * st.dir.z - to.z * st.dir.x;
                if (a < before - half - PAD || a > along + half + PAD) continue;          // 이번 틱에 지나간 구간
                if (Math.abs(side) > p.w() / 2 + PAD || Math.abs(pl.getY() - st.dashFrom.y) > HEIGHT) continue;
                st.dashHit.add(pl.getUUID());
                hit(mob, m, st, l, pl, p);
                double sg = side == 0 ? 1 : Math.signum(side);
                push(pl, new Vec3(st.dir.z * sg, 0, -st.dir.x * sg).add(st.dir.scale(0.6)).normalize(), 4);
            }
            if (st.dashTick >= DASH_TICKS) {
                st.dashHit.clear();
                if (--st.repeatLeft > 0) {
                    List<ServerPlayer> fs = new ArrayList<>();
                    for (ServerPlayer x : arena(l, st, 0)) if (valid(x)) fs.add(x);
                    ServerPlayer nt = fs.isEmpty() ? null : fs.get(ThreadLocalRandom.current().nextInt(fs.size()));
                    if (nt == null) { finish(mob, st, def, now); return; }
                    beginCharge(mob, st, p, nt, now);
                } else finish(mob, st, def, now);
            }
            return;
        }
        // 예고 중: 제자리 + 방향 고정
        mob.getNavigation().stop();
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(0, v.y, 0);
        if (st.dir != null) {
            float yaw = (float) Math.toDegrees(Math.atan2(-st.dir.x, st.dir.z));
            mob.setYRot(yaw); mob.setYHeadRot(yaw); mob.setYBodyRot(yaw);
        }
        if (st.stage == 0) {   // 추적 (노랑)
            if (p.type().equals("charge")) {
                ServerPlayer t = st.target == null ? null : (ServerPlayer) l.getPlayerByUUID(st.target);
                if (t != null) {
                    Vec3 to = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
                    if (to.lengthSqr() > 1e-4) st.dir = to.normalize();
                }
            } else {   // rocks
                for (var e : st.rocks.entrySet()) {
                    Player t = l.getPlayerByUUID(e.getKey());
                    if (t != null && valid(t)) e.setValue(t.position());
                }
            }
            if (now >= st.stageEnd) {   // 확정 (빨강)
                int dl = ticks(p.delay());
                st.stage = 1;
                st.stageEnd = now + dl + HIT_LAG;
                st.pos = mob.position();
                Net.track(l, mob.getId(), -1, 0, 0, 0, 0);   // 노랑 지우기
                if (p.type().equals("charge")) Net.telegraph(l, st.pos, st.dir, MobAI.LINE, p.len(), p.w(), dl);
                else for (Vec3 q : st.rocks.values()) Net.telegraph(l, q, st.dir, MobAI.CIRCLE, p.r(), 0, dl);
                l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1f, 0.5f);
            }
            return;
        }
        if (now < st.stageEnd) return;
        execute(mob, m, st, def, l, p);
        if (p.type().equals("charge")) {   // 이어서 돌진 이동
            st.stage = 2;
            st.dashTick = 0;
            st.dashFrom = st.pos;
            return;
        }
        finish(mob, st, def, now);
    }

    private static void finish(Mob mob, State st, Def def, long now) {
        if (st.cur != null && st.stage == 0) Net.track((ServerLevel) mob.level(), mob.getId(), -1, 0, 0, 0, 0);
        st.cur = null;
        st.dashHit.clear();
        st.rocks.clear();
        standing(mob, false);
        double[] w = st.phase >= 2 ? def.wait2() : def.wait1();
        st.nextAt = now + Math.round((w[0] + ThreadLocalRandom.current().nextDouble() * Math.max(0, w[1] - w[0])) * 20);
    }

    private static void execute(Mob mob, MobData m, State st, Def def, ServerLevel l, Pattern p) {
        Vec3 c = st.pos, dir = st.dir;
        switch (p.type()) {
            case "cone" -> {
                Vec3 q = c.add(dir.scale(p.r() * 0.5));
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, q.x, c.y + 1, q.z, 6, p.r() / 3, 0.2, p.r() / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.HOSTILE, 1.5f, 0.6f);
                double cos = Math.cos(Math.toRadians(p.angle() / 2));
                for (ServerPlayer pl : arena(l, st, 4)) {
                    if (!valid(pl)) continue;
                    Vec3 to = new Vec3(pl.getX() - c.x, 0, pl.getZ() - c.z);
                    double dd = to.length();
                    if (dd > p.r() + PAD || Math.abs(pl.getY() - c.y) > HEIGHT) continue;
                    if (dd < 0.8 || to.normalize().dot(dir) >= cos) hit(mob, m, st, l, pl, p);
                }
            }
            case "charge" -> l.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1f, 1.2f);   // 피해는 돌진 이동 중
            case "rocks" -> {
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8f, 0.8f);
                for (Vec3 q : st.rocks.values()) {
                    l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y + 0.3, q.z, 2, p.r() / 3, 0.1, p.r() / 3, 0);
                    l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), q.x, q.y + 0.5, q.z, 30, p.r() / 2, 0.3, p.r() / 2, 0.1);
                    for (ServerPlayer pl : arena(l, st, 4)) {
                        if (!valid(pl)) continue;
                        if (flat(pl.position(), q) <= p.r() + PAD && Math.abs(pl.getY() - q.y) <= HEIGHT) hit(mob, m, st, l, pl, p);
                    }
                }
            }
            case "roar" -> {
                l.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.5f);
                l.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.5, c.z, 1, 0, 0, 0, 0);
                for (ServerPlayer pl : arena(l, st, 4)) {
                    if (!valid(pl)) continue;
                    Vec3 to = new Vec3(pl.getX() - c.x, 0, pl.getZ() - c.z);
                    if (to.length() > p.r() + PAD) continue;
                    push(pl, to.lengthSqr() < 1e-4 ? dir : to.normalize(), p.push());
                }
            }
            default -> {   // slam
                l.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 6, p.r() / 3, 0.1, p.r() / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1f, 0.7f);
                for (ServerPlayer pl : arena(l, st, 4)) {
                    if (!valid(pl)) continue;
                    if (flat(pl.position(), c) <= p.r() + PAD && Math.abs(pl.getY() - c.y) <= HEIGHT) hit(mob, m, st, l, pl, p);
                }
            }
        }
        standing(mob, false);
    }

    /** 밀어내기: blocks 칸 정도 (무게중심 등 넉백 저항만큼 줄어듦) */
    private static void push(ServerPlayer p, Vec3 dir, double blocks) {
        double resist = Math.min(100, RpgData.get(p).bonus("kbResist"));
        double b = blocks * (1 - resist / 100.0);
        if (b <= 0.1) return;
        double sp = Math.min(2.5, b * 0.2);
        p.setDeltaMovement(dir.x * sp, 0.42, dir.z * sp);
        p.hurtMarked = true;
    }

    /** 패턴 피해 + 쓰러지면 경기장에 알림 */
    private static void hit(Mob mob, MobData m, State st, ServerLevel l, ServerPlayer p, Pattern pt) {
        CombatEvents.mobSkillHit(mob, p, pt.pct());
        PlayerData d = RpgData.get(p);
        if (!p.isAlive() || d.isDown() || d.hp <= 0) {
            for (ServerPlayer x : arena(l, st, 16))
                x.sendSystemMessage(Component.literal(p.getGameProfile().getName() + "님이 " + m.name + "의 [" + pt.name() + "]에 쓰러졌어요").withStyle(ChatFormatting.RED));
        }
    }

    // ───────── 보스바 · 초기화 ─────────
    private static void updateBar(ServerLevel l, State st, MobData m) {
        if (st.bar == null) return;
        st.bar.setProgress((float) Math.max(0, Math.min(1, m.hp / m.maxHp)));
        List<ServerPlayer> near = arena(l, st, 16);
        for (ServerPlayer p : new ArrayList<>(st.bar.getPlayers())) if (!near.contains(p)) st.bar.removePlayer(p);
        for (ServerPlayer p : near) if (!st.bar.getPlayers().contains(p)) st.bar.addPlayer(p);
    }

    private static void reset(Mob mob, MobData m, State st) {
        m.hp = m.maxHp;
        mob.setHealth(mob.getMaxHealth());
        st.phase = 1;
        st.deck.clear();
        st.cur = null;
        st.dmg.clear();
        st.emptySince = -1;
        standing(mob, false);
        if (st.bar != null) { st.bar.setColor(BossEvent.BossBarColor.RED); st.bar.setProgress(1f); }
        mob.teleportTo(st.center.x, st.center.y, st.center.z);
        Mobs.sync(mob);
    }

    // ───────── 피해 (CombatEvents 에서 부름) ─────────
    /** 플레이어가 보스에게 준 피해: 레벨 차이 감소 + 기여도 기록 */
    public static double onHurt(Mob mob, MobData m, ServerPlayer p, double dmg) {
        Def def = DEFS.get(m.key);
        if (def == null) return dmg;
        int diff = m.level - RpgData.get(p).level;
        if (diff > 0) dmg *= Math.max(1 - def.maxPenalty() / 100.0, 1 - def.penaltyPerLevel() * diff / 100.0);
        dmg = Math.max(1, Math.round(dmg));
        State st = STATES.get(mob.getUUID());
        if (st != null) st.dmg.merge(p.getUUID(), Math.min(dmg, m.hp), Double::sum);
        return dmg;
    }

    // ───────── 처치 ─────────
    public static void onDeath(Mob mob, MobData m, ServerPlayer killer) {
        Def def = DEFS.get(m.key);
        State st = STATES.remove(mob.getUUID());
        if (def == null) return;
        ServerLevel l = (ServerLevel) mob.level();
        if (st != null && st.bar != null) st.bar.removeAllPlayers();
        BossPoints.died(l, mob, def);

        // 참여자: 피해를 준 사람 중 경기장 근처에 있는 사람
        Map<ServerPlayer, Double> part = new LinkedHashMap<>();
        if (st != null) for (var e : st.dmg.entrySet()) {
            Player p = l.getPlayerByUUID(e.getKey());
            if (p instanceof ServerPlayer sp && flat(sp.position(), st.center) <= st.radius + 24) part.put(sp, e.getValue());
        }
        if (part.isEmpty() && killer != null) part.put(killer, 1.0);
        double total = 0;
        for (double v : part.values()) total += v;
        double bestDrop = 0;
        for (var e : part.entrySet()) {
            ServerPlayer p = e.getKey();
            PlayerData d = RpgData.get(p);
            bestDrop = Math.max(bestDrop, d.dropBonus());
            double share = e.getValue() / Math.max(1, total);
            long exp = Math.max(1, Math.round(m.exp * share));
            long gold = Math.max(1, Math.round(m.gold * share * (1 + d.goldBonus() / 100.0)));
            d.gold += gold;
            Progress.giveExp(p, exp);
            Net.reward(p, exp, gold);
            RpgData.sync(p);
            p.sendSystemMessage(Component.literal("[" + m.name + " 처치] 기여도 " + Math.round(share * 100) + "% · 경험치 " + String.format("%,d", exp)
                    + " · " + String.format("%,d", gold) + " G").withStyle(ChatFormatting.GOLD));
        }
        rpg.core.quest.QuestEvents.onKill(new ArrayList<>(part.keySet()), m);

        // 드롭 세트: 참여 인원수만큼, 바닥에 (누구나 주움)
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double mul = 1 + bestDrop / 100.0;
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < part.size(); i++) {
            if (!def.sure().isEmpty()) out.add(rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + def.sure(), 1));
            for (Mats mt : def.mats()) rpg.core.loot.Drops.rollMats(mt.mob(), mt.times(), mul, out);
            if (def.fragments() > 0) out.add(new ItemStack(rpg.core.item.ModItems.SCROLL_FRAGMENT.get(), def.fragments()));
            if (r.nextDouble() * 100 < def.scrollChance() * mul) out.add(rpg.core.equip.Enhance.randomScroll(m.level, r));
            if (!def.ring().isEmpty() && r.nextDouble() * 100 < def.ringChance() * mul) {
                ItemStack g = rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + def.ring(), 1);
                rpg.core.equip.Gear.roll(g);
                out.add(g);
                for (ServerPlayer p : part.keySet())
                    p.sendSystemMessage(Component.literal("✦ " + g.getHoverName().getString() + " 이(가) 떨어졌어요!").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
        for (ItemStack s : out) {
            if (s.isEmpty()) continue;
            ItemEntity it = new ItemEntity(l, mob.getX() + r.nextDouble() * 2 - 1, mob.getY() + 0.5, mob.getZ() + r.nextDouble() * 2 - 1, s);
            it.setDeltaMovement(r.nextDouble() * 0.3 - 0.15, 0.3, r.nextDouble() * 0.3 - 0.15);
            it.setDefaultPickUpDelay();
            l.addFreshEntity(it);
        }
        l.playSound(null, mob.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.HOSTILE, 1f, 1f);
    }

    /** 보스가 사라지면(청크 언로드·명령어 삭제) 보스바 정리 */
    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        State st = STATES.remove(e.getEntity().getUUID());
        if (st != null && st.bar != null) st.bar.removeAllPlayers();
    }

    /** 보스 레벨 (mobs.json levels 의 첫 레벨) */
    public static int level(String key) {
        Balance.MobDef md = Balance.MOBS.get(key);
        return md == null || md.levels().isEmpty() ? 1 : md.levels().firstKey();
    }

    public static BlockPos center(Mob mob) {
        State st = STATES.get(mob.getUUID());
        return st == null ? mob.blockPosition() : BlockPos.containing(st.center);
    }
}
