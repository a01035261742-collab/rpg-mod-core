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
                          double track, double delay, double pct, double push, int phase, int repeat2, Map<String, Double> x) {
        /** 패턴별 추가 값 (mobs.json 의 다른 숫자 키) */
        public double x(String k, double def) { return x == null ? def : x.getOrDefault(k, def); }
    }

    public record Mats(String mob, int times) {}

    public record Def(String key, double arena, int respawnSec, int resetSec, double phase2, double[] wait1, double[] wait2,
                      double penaltyPerLevel, double maxPenalty, List<Pattern> patterns,
                      String sure, int fragments, double scrollChance, String ring, double ringChance, List<Mats> mats, String bar,
                      Pattern melee, double meleeCd, Map<String, Double> gim, String pillarKey, String firstMedal, Map<String, Double> rx) {
        public double g(String k, double def) { return gim.getOrDefault(k, def); }
    }

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
                        (int) d(p, "phase", 1), (int) d(p, "repeat2", 1), nums(p)));
            }
            Pattern melee = null;
            if (o.has("melee")) {   // 기본 근접 공격 (패턴 사이, 붙어 있을 때)
                JsonObject p = o.getAsJsonObject("melee");
                melee = new Pattern("melee", s(p, "name", "공격"), "cone", d(p, "r", 5.5), d(p, "angle", 100), 0, 0, 0,
                        d(p, "delay", 0.8), d(p, "pct", 100), 0, 1, 1, null);
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
                    s(o, "bar", ""), melee, o.has("melee") ? d(o.getAsJsonObject("melee"), "cd", 1.5) : 0,
                    o.has("gimmick") ? nums(o.getAsJsonObject("gimmick")) : new HashMap<>(),
                    o.has("gimmick") ? s(o.getAsJsonObject("gimmick"), "pillar", "") : "", s(rw, "first", ""), nums(rw)));
        } catch (Exception ex) {
            RpgCore.LOG.error("[rpgcore] 보스 {} 읽기 실패", key, ex);
        }
    }

    private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }
    private static Map<String, Double> nums(JsonObject o) {
        Map<String, Double> m = new HashMap<>();
        for (var e : o.entrySet()) if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) m.put(e.getKey(), e.getValue().getAsDouble());
        return m;
    }
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
        long meleeReady;
        Vec3 dashFrom;
        int dashTick;
        final java.util.Set<UUID> dashHit = new java.util.HashSet<>();
        boolean alt;                  // 콤보 마지막 타가 원으로 바뀜
        double counterDmg;            // 카운터 중 받은 피해
        long vulnUntil;               // 이때까지 받는 피해 증가 (카운터 성공)
        final List<UUID> pillars = new ArrayList<>();
        boolean pillarsDone;
        final List<BlockPos> props = new ArrayList<>();                 // 단단한 버섯 (아래 칸 위치)
        final Map<BlockPos, net.minecraft.world.level.block.state.BlockState> saved = new HashMap<>();
        long propNext;
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

        // 기둥 기믹 (HP pillarAt% 에서 1번)
        if (!st.pillarsDone && !def.pillarKey().isEmpty() && m.hp <= m.maxHp * def.g("pillarAt", 50) / 100.0) spawnPillars(l, mob, m, st, def);
        // 단단한 버섯: propEvery 초마다 하나씩 (최대 props 개)
        if (def.g("props", 0) > 0 && now >= st.propNext) {
            st.propNext = now + Math.round(def.g("propEvery", 20) * 20);
            if (st.props.size() < def.g("props", 0)) placeProp(l, st, def);
        }

        if (st.cur != null) { run(mob, m, st, def, l, now); return; }

        // 기절 중: 제자리
        if (now < m.stunUntil) {
            mob.getNavigation().stop();
            if (mob.tickCount % 10 == 0) l.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 6, 0.5, 0.1, 0.5, 0);
            return;
        }

        // 경기장 밖으로 나가면 중앙으로
        if (flat(mob.position(), st.center) > st.radius + 2) {
            mob.teleportTo(st.center.x, st.center.y, st.center.z);
            mob.getNavigation().stop();
        }

        ServerPlayer t = pickTarget(mob, m, st, fighters, now);
        if (t == null) return;
        st.target = t.getUUID();
        if (now < st.nextAt || now < m.stunUntil) {
            // 패턴 사이: 붙어 있으면 기본 근접 공격
            if (def.melee() != null && now >= st.meleeReady && now >= m.stunUntil
                    && flat(mob.position(), t.position()) <= def.melee().r() && Math.abs(t.getY() - mob.getY()) < HEIGHT) {
                start(mob, st, def, def.melee(), t, fighters, l, now);
                return;
            }
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
        st.alt = false;
        mob.getNavigation().stop();
        Vec3 to = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
        st.dir = to.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, mob.getYRot()) : to.normalize();
        st.pos = mob.position();
        int dl = ticks(p.delay());
        switch (p.type()) {
            case "cone" -> Net.telegraph(l, st.pos, st.dir, MobAI.CONE, p.r(), p.angle(), dl);
            case "charge" -> { st.repeatLeft = st.phase >= 2 ? Math.max(1, p.repeat2()) : 1; beginCharge(mob, st, p, t, now); return; }
            case "beam" -> { st.repeatLeft = (int) Math.max(1, p.x("count", 1)); beginCharge(mob, st, p, t, now); return; }
            case "combo" -> {
                st.repeatLeft = (int) Math.max(1, st.phase >= 2 ? p.x("hits2", p.x("hits", 3)) : p.x("hits", 3));
                comboHit(mob, st, p, t, l, now, dl);
                return;
            }
            case "rocks" -> {
                st.rocks.clear();
                for (ServerPlayer f : fighters) st.rocks.put(f.getUUID(), f.position());
                beginTrack(mob, st, p, now);
                standing(mob, true);
                return;
            }
            case "delayed", "jump" -> {
                st.repeatLeft = p.type().equals("jump") && st.phase >= 2 ? Math.max(1, p.repeat2()) : 1;
                if (p.type().equals("jump") && st.props.isEmpty() && def.g("props", 0) > 0) placeProp(l, st, def);
                st.rocks.clear();
                st.rocks.put(t.getUUID(), t.position());
                beginTrack(mob, st, p, now);
                return;
            }
            case "counter" -> {
                int w = ticks(p.x("window", 2));
                st.counterDmg = 0;
                mob.setGlowingTag(true);
                Net.telegraph(l, st.pos, st.dir, MobAI.CIRCLE + 20, p.r(), 0, w);
                for (ServerPlayer x : arena(l, st, 16))
                    x.displayClientMessage(Component.literal("◆ " + p.name() + " — " + (int) p.x("window", 2) + "초 안에 때려서 끊으세요!").withStyle(ChatFormatting.AQUA), true);
                l.playSound(null, mob.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 1.4f);
                st.stageEnd = now + w;
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

    /** 직선 추적 (돌진·광선): 대상 쪽 노란 선 */
    private static void beginCharge(Mob mob, State st, Pattern p, ServerPlayer t, long now) {
        st.target = t == null ? st.target : t.getUUID();
        st.stage = 0;
        st.stageEnd = now + ticks(p.track());
        st.pos = mob.position();
        if (t != null) Net.track((ServerLevel) mob.level(), mob.getId(), t.getId(), MobAI.LINE, p.len(), p.w(), ticks(p.track()));
    }

    /** 원 추적 (바위·지연·점프): st.rocks 의 플레이어 발밑 노란 원 */
    private static void beginTrack(Mob mob, State st, Pattern p, long now) {
        ServerLevel l = (ServerLevel) mob.level();
        st.stage = 0;
        st.stageEnd = now + ticks(p.track());
        for (UUID id : st.rocks.keySet()) {
            Player pl = l.getPlayerByUUID(id);
            if (pl != null) Net.track(l, mob.getId(), pl.getId(), MobAI.CIRCLE, p.r(), 0, ticks(p.track()));
        }
    }

    /** 콤보 한 타: 대상 쪽으로 돌고 예고 (마지막 타는 확률로 주변 원) */
    private static void comboHit(Mob mob, State st, Pattern p, ServerPlayer t, ServerLevel l, long now, int dl) {
        if (t != null) {
            Vec3 to = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
            if (to.lengthSqr() > 1e-4) st.dir = to.normalize();
        }
        st.pos = mob.position();
        st.stage = 1;
        st.alt = st.repeatLeft == 1 && ThreadLocalRandom.current().nextDouble() * 100 < p.x("altChance", 0);
        if (st.alt) Net.telegraph(l, st.pos, st.dir, MobAI.CIRCLE, p.x("altR", p.r()), 0, dl);
        else Net.telegraph(l, st.pos, st.dir, MobAI.CONE, p.r(), p.angle(), dl);
        st.stageEnd = now + dl + HIT_LAG;
        l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1f, 0.5f);
    }

    private static void standing(Mob mob, boolean on) {
        if (mob instanceof net.minecraft.world.entity.animal.PolarBear b) b.setStanding(on);
    }

    private static ServerPlayer randomFighter(ServerLevel l, State st) {
        List<ServerPlayer> fs = new ArrayList<>();
        for (ServerPlayer x : arena(l, st, 0)) if (valid(x)) fs.add(x);
        return fs.isEmpty() ? null : fs.get(ThreadLocalRandom.current().nextInt(fs.size()));
    }

    private static ServerPlayer currentTarget(ServerLevel l, State st) {
        Player p = st.target == null ? null : l.getPlayerByUUID(st.target);
        return p instanceof ServerPlayer sp && valid(sp) ? sp : randomFighter(l, st);
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
                    ServerPlayer nt = randomFighter(l, st);
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
        boolean line = p.type().equals("charge") || p.type().equals("beam");
        if (st.stage == 0) {   // 추적 (노랑)
            if (line) {
                ServerPlayer t = st.target == null ? null : (ServerPlayer) l.getPlayerByUUID(st.target);
                if (t != null) {
                    Vec3 to = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
                    if (to.lengthSqr() > 1e-4) st.dir = to.normalize();
                }
            } else {
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
                if (line) Net.telegraph(l, st.pos, st.dir, MobAI.LINE, p.len(), p.w(), dl);
                else for (Vec3 q : st.rocks.values()) Net.telegraph(l, q, st.dir, MobAI.CIRCLE, p.r(), 0, dl);
                if (p.type().equals("jump")) {   // 뛰어오름
                    mob.setDeltaMovement(0, 1.1, 0);
                    mob.hurtMarked = true;
                    l.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1f, 1.4f);
                }
                l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1f, 0.5f);
            }
            return;
        }
        if (p.type().equals("counter") && st.stage == 1) {   // 카운터: 창 안에 충분히 맞으면 성공
            int people = 0;
            for (ServerPlayer x : arena(l, st, 0)) if (valid(x)) people++;
            double need = m.maxHp * (p.x("needPer", 0) > 0 ? p.x("needPer", 1) * Math.max(1, people) : p.x("need", 1)) / 100.0;   // 인원 비례
            if (st.counterDmg >= need) {
                long stun = Math.round(p.x("stun", 5) * 20);
                m.stunUntil = now + stun;
                st.vulnUntil = now + stun;
                mob.setGlowingTag(false);
                l.sendParticles(ParticleTypes.FLASH, mob.getX(), mob.getY() + 2, mob.getZ(), 1, 0, 0, 0, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 2f, 0.7f);
                for (ServerPlayer x : arena(l, st, 16))
                    x.sendSystemMessage(Component.literal("반격 성공! " + m.name + " 기절 " + (int) p.x("stun", 5) + "초 (받는 피해 +"
                            + (int) def.g("counterVuln", 30) + "%)").withStyle(ChatFormatting.AQUA));
                finish(mob, st, def, now);
                return;
            }
        }
        if (now < st.stageEnd) return;
        if (p.type().equals("delayed") && st.stage == 1) {   // 다 찬 뒤 잠깐 멈췄다가 터짐
            st.stage = 3;
            int hold = ticks(p.x("hold", 0.6));
            st.stageEnd = now + hold;
            for (Vec3 q : st.rocks.values()) {
                Net.telegraph(l, q, st.dir, MobAI.CIRCLE, p.r(), 0, hold);
                l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, q.x, q.y + 0.5, q.z, 20, p.r() / 2, 0.2, p.r() / 2, 0);
            }
            l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 1f, 0.5f);
            return;
        }
        boolean stunned = execute(mob, m, st, def, l, p, now);
        switch (p.type()) {
            case "charge" -> {   // 이어서 돌진 이동
                st.stage = 2;
                st.dashTick = 0;
                st.dashFrom = st.pos;
            }
            case "combo" -> {
                if (--st.repeatLeft > 0) comboHit(mob, st, p, currentTarget(l, st), l, now, ticks(p.x("delay2", p.delay())));
                else finish(mob, st, def, now);
            }
            case "beam" -> {
                ServerPlayer nt = --st.repeatLeft > 0 ? randomFighter(l, st) : null;
                if (nt != null) beginCharge(mob, st, p, nt, now);
                else finish(mob, st, def, now);
            }
            case "jump" -> {
                ServerPlayer nt = !stunned && --st.repeatLeft > 0 ? currentTarget(l, st) : null;
                if (nt != null) { st.rocks.clear(); st.rocks.put(nt.getUUID(), nt.position()); beginTrack(mob, st, p, now); }
                else finish(mob, st, def, now);
            }
            default -> finish(mob, st, def, now);
        }
    }

    private static void finish(Mob mob, State st, Def def, long now) {
        if (st.cur != null && st.stage == 0) Net.track((ServerLevel) mob.level(), mob.getId(), -1, 0, 0, 0, 0);
        if (st.cur != null && st.cur == def.melee()) {   // 기본 공격은 패턴 대기 시간을 안 늘림
            st.cur = null;
            st.meleeReady = now + Math.round(def.meleeCd() * 20);
            st.nextAt = Math.max(st.nextAt, now + 10);
            standing(mob, false);
            return;
        }
        if (st.cur != null && st.cur.type().equals("counter")) mob.setGlowingTag(false);
        st.cur = null;
        st.dashHit.clear();
        st.rocks.clear();
        standing(mob, false);
        double[] w = st.phase >= 2 ? def.wait2() : def.wait1();
        st.nextAt = now + Math.round((w[0] + ThreadLocalRandom.current().nextDouble() * Math.max(0, w[1] - w[0])) * 20);
    }

    private static void hitCircle(Mob mob, MobData m, State st, ServerLevel l, Vec3 c, double r, Pattern p) {
        for (ServerPlayer pl : arena(l, st, 4)) {
            if (!valid(pl)) continue;
            if (flat(pl.position(), c) <= r + PAD && Math.abs(pl.getY() - c.y) <= HEIGHT) hit(mob, m, st, l, pl, p);
        }
    }

    /** 발동. 점프 압착이 단단한 버섯에 닿아 보스가 기절하면 true */
    private static boolean execute(Mob mob, MobData m, State st, Def def, ServerLevel l, Pattern p, long now) {
        Vec3 c = st.pos, dir = st.dir;
        switch (p.type()) {
            case "cone", "combo" -> {
                if (st.alt) {
                    l.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 5, p.x("altR", p.r()) / 3, 0.1, p.x("altR", p.r()) / 3, 0);
                    l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1f, 0.8f);
                    hitCircle(mob, m, st, l, c, p.x("altR", p.r()), p);
                    break;
                }
                Vec3 q = c.add(dir.scale(p.r() * 0.5));
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, q.x, c.y + 1, q.z, 6, p.r() / 3, 0.2, p.r() / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5f, 0.6f);
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
            case "beam" -> {
                for (double dd = 0; dd <= p.len(); dd += 0.8) {
                    Vec3 q = c.add(dir.scale(dd));
                    l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, q.x, q.y + 1.2, q.z, 2, 0.3, 0.3, 0.3, 0);
                    l.sendParticles(ParticleTypes.END_ROD, q.x, q.y + 1.2, q.z, 1, 0.1, 0.1, 0.1, 0);
                }
                l.playSound(null, mob.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.5f, 1.6f);
                for (ServerPlayer pl : arena(l, st, 4)) {
                    if (!valid(pl)) continue;
                    Vec3 to = new Vec3(pl.getX() - c.x, 0, pl.getZ() - c.z);
                    double along = to.dot(dir), side = to.x * dir.z - to.z * dir.x;
                    if (along >= -1 && along <= p.len() + PAD && Math.abs(side) <= p.w() / 2 + PAD && Math.abs(pl.getY() - c.y) <= HEIGHT) hit(mob, m, st, l, pl, p);
                }
            }
            case "rocks", "delayed" -> {
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8f, 0.8f);
                for (Vec3 q : st.rocks.values()) {
                    l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y + 0.3, q.z, 2, p.r() / 3, 0.1, p.r() / 3, 0);
                    l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()), q.x, q.y + 0.5, q.z, 30, p.r() / 2, 0.3, p.r() / 2, 0.1);
                    hitCircle(mob, m, st, l, q, p.r(), p);
                }
            }
            case "jump" -> {
                Vec3 q = st.rocks.isEmpty() ? c : st.rocks.values().iterator().next();
                mob.teleportTo(q.x, q.y, q.z);
                mob.setDeltaMovement(Vec3.ZERO);
                l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y + 0.2, q.z, 6, p.r() / 3, 0.1, p.r() / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.6f);
                hitCircle(mob, m, st, l, q, p.r(), p);
                // 단단한 버섯이 범위 안에 있으면 부서지면서 보스 기절
                for (BlockPos bp : new ArrayList<>(st.props)) {
                    if (flat(Vec3.atBottomCenterOf(bp), q) > p.r() + 0.5) continue;
                    removeProp(l, st, bp);
                    long stun = Math.round(def.g("propStun", 10) * 20);
                    m.stunUntil = now + stun;
                    l.playSound(null, bp, SoundEvents.SHROOMLIGHT_BREAK, SoundSource.HOSTILE, 2f, 0.6f);
                    for (ServerPlayer x : arena(l, st, 16))
                        x.sendSystemMessage(Component.literal(m.name + "이(가) 단단한 버섯에 부딪혀 기절했어요! (" + (int) def.g("propStun", 10) + "초)").withStyle(ChatFormatting.GOLD));
                    return true;
                }
            }
            case "counter" -> {   // 카운터 실패: 폭발
                mob.setGlowingTag(false);
                l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2f, 0.6f);
                hitCircle(mob, m, st, l, c, p.r(), p);
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
                hitCircle(mob, m, st, l, c, p.r(), p);
                if (p.push() > 0) for (ServerPlayer pl : arena(l, st, 4)) {
                    if (!valid(pl) || flat(pl.position(), c) > p.r() + PAD) continue;
                    Vec3 to = new Vec3(pl.getX() - c.x, 0, pl.getZ() - c.z);
                    push(pl, to.lengthSqr() < 1e-4 ? dir : to.normalize(), p.push());
                }
            }
        }
        standing(mob, false);
        return false;
    }

    // ───────── 기믹: 단단한 버섯 (구조물) · 기둥 ─────────
    /** 단단한 버섯 하나 세우기: 버섯 줄기 2칸 + 빛나는 갓 (원래 블록은 기억했다가 되돌림) */
    private static void placeProp(ServerLevel l, State st, Def def) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double min = def.g("propMin", 6), max = def.g("propMax", 22);
        for (int tries = 0; tries < 20; tries++) {
            double ang = r.nextDouble() * Math.PI * 2, dist = min + r.nextDouble() * Math.max(0, max - min);
            BlockPos base = BlockPos.containing(st.center.x + Math.cos(ang) * dist, st.center.y, st.center.z + Math.sin(ang) * dist);
            boolean ok = true;
            for (int i = 0; i < 3; i++) if (!l.getBlockState(base.above(i)).isAir()) ok = false;
            if (!ok || l.getBlockState(base.below()).isAir()) continue;
            boolean near = false;
            for (BlockPos o : st.props) if (o.distSqr(base) < 16) near = true;
            if (near) continue;
            for (int i = 0; i < 3; i++) st.saved.put(base.above(i), l.getBlockState(base.above(i)));
            l.setBlockAndUpdate(base, net.minecraft.world.level.block.Blocks.MUSHROOM_STEM.defaultBlockState());
            l.setBlockAndUpdate(base.above(), net.minecraft.world.level.block.Blocks.MUSHROOM_STEM.defaultBlockState());
            l.setBlockAndUpdate(base.above(2), net.minecraft.world.level.block.Blocks.SHROOMLIGHT.defaultBlockState());
            st.props.add(base);
            l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, base.getX() + 0.5, base.getY() + 1.5, base.getZ() + 0.5, 20, 0.5, 1, 0.5, 0);
            return;
        }
    }

    private static void removeProp(ServerLevel l, State st, BlockPos base) {
        for (int i = 0; i < 3; i++) {
            BlockPos q = base.above(i);
            var old = st.saved.remove(q);
            l.setBlockAndUpdate(q, old != null ? old : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        st.props.remove(base);
    }

    private static void clearGimmicks(ServerLevel l, State st) {
        for (BlockPos bp : new ArrayList<>(st.props)) removeProp(l, st, bp);
        for (UUID id : st.pillars) { var e = l.getEntity(id); if (e != null) e.discard(); }
        st.pillars.clear();
        st.pillarsDone = false;
    }

    /** 기둥 소환 (보스 HP pillarAt% 에서 1번): 하나라도 남아 있으면 보스가 받는 피해 감소 */
    private static void spawnPillars(ServerLevel l, Mob mob, MobData m, State st, Def def) {
        st.pillarsDone = true;
        int n = (int) def.g("pillarCount", 4);
        double dist = def.g("pillarDist", 18);
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n + Math.PI / 4;
            BlockPos at = BlockPos.containing(st.center.x + Math.cos(ang) * dist, st.center.y, st.center.z + Math.sin(ang) * dist);
            Mob pl = Mobs.spawn(l, def.pillarKey(), m.level, at, "boss", false);
            if (pl == null) continue;
            pl.setNoAi(true);
            pl.getPersistentData().putBoolean("rpgPillar", true);
            st.pillars.add(pl.getUUID());
        }
        for (ServerPlayer x : arena(l, st, 16))
            x.sendSystemMessage(Component.literal("독버섯 기둥 " + n + "개가 솟아났어요! 기둥이 남아 있으면 " + m.name + "이(가) 받는 피해 -"
                    + (int) def.g("pillarReduce", 80) + "%").withStyle(ChatFormatting.DARK_GREEN));
    }

    private static boolean pillarsAlive(ServerLevel l, State st) {
        for (UUID id : st.pillars) { var e = l.getEntity(id); if (e != null && e.isAlive()) return true; }
        return false;
    }

    /** 보스가 세운 단단한 버섯 블록인지 (못 캐게) */
    public static boolean isProp(net.minecraft.world.level.Level level, BlockPos pos) {
        for (State st : STATES.values())
            for (BlockPos b : st.props) if (b.getX() == pos.getX() && b.getZ() == pos.getZ() && pos.getY() >= b.getY() && pos.getY() <= b.getY() + 2) return true;
        return false;
    }

    @SubscribeEvent
    public static void noBreakProp(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
        if (isProp((net.minecraft.world.level.Level) e.getLevel(), e.getPos())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void noExplodeProp(net.minecraftforge.event.level.ExplosionEvent.Detonate e) {
        e.getAffectedBlocks().removeIf(bp -> isProp(e.getLevel(), bp));
    }

    /** 보스 경기장 안인지 (다운 조건) */
    public static boolean inArena(ServerPlayer p) {
        for (var e : STATES.entrySet()) {
            State st = e.getValue();
            if (st.center != null && flat(p.position(), st.center) <= st.radius + 4 && Math.abs(p.getY() - st.center.y) < 24) return true;
        }
        return false;
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
        if (pt.x("maxPct", 0) > 0) CombatEvents.maxHpHit(mob, p, pt.x("maxPct", 0));   // 최대 HP 비율
        else CombatEvents.mobSkillHit(mob, p, pt.pct());
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
        clearGimmicks((ServerLevel) mob.level(), st);
        mob.setGlowingTag(false);
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
        dmg *= 1 + RpgData.get(p).bonus("bossPct") / 100.0;   // 보스 공격 시 피해 (잠재능력)
        int diff = m.level - RpgData.get(p).level;
        if (diff > 0) dmg *= Math.max(1 - def.maxPenalty() / 100.0, 1 - def.penaltyPerLevel() * diff / 100.0);
        dmg = Math.max(1, Math.round(dmg));
        State st = STATES.get(mob.getUUID());
        if (st != null) {
            ServerLevel l = (ServerLevel) mob.level();
            long now = l.getGameTime();
            if (!st.pillars.isEmpty() && pillarsAlive(l, st)) dmg = Math.max(1, Math.round(dmg * (1 - def.g("pillarReduce", 80) / 100.0)));   // 기둥
            if (now < st.vulnUntil) dmg = Math.round(dmg * (1 + def.g("counterVuln", 30) / 100.0));                                            // 카운터 성공
            if (st.cur != null && st.cur.type().equals("counter")) st.counterDmg += dmg;
            st.dmg.merge(p.getUUID(), Math.min(dmg, m.hp), Double::sum);
        }
        return dmg;
    }

    // ───────── 처치 ─────────
    public static void onDeath(Mob mob, MobData m, ServerPlayer killer) {
        Def def = DEFS.get(m.key);
        State st = STATES.remove(mob.getUUID());
        if (def == null) return;
        ServerLevel l = (ServerLevel) mob.level();
        if (st != null && st.bar != null) st.bar.removeAllPlayers();
        if (st != null) clearGimmicks(l, st);
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
        // 첫 처치 훈장: 사람마다 보스별 한 번 (죽어도 남는 저장 칸)
        if (!def.firstMedal().isEmpty()) for (ServerPlayer p : part.keySet()) {
            var keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            var done = keep.getCompound("rpgBossFirst");
            if (done.getBoolean(m.key)) continue;
            done.putBoolean(m.key, true);
            keep.put("rpgBossFirst", done);
            p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
            ItemStack medal = rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + def.firstMedal(), 1);
            rpg.core.equip.Gear.rollPlain(medal);
            String nm = medal.getHoverName().getString();
            if (!p.getInventory().add(medal)) p.drop(medal, false);
            p.sendSystemMessage(Component.literal("[첫 처치] " + nm + " 획득!").withStyle(ChatFormatting.GOLD));
        }

        // 드롭 세트: 참여 인원수만큼, 바닥에 (누구나 주움)
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double mul = 1 + bestDrop / 100.0;
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < part.size(); i++) {
            if (!def.sure().isEmpty()) out.add(rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + def.sure(), 1));
            for (Mats mt : def.mats()) rpg.core.loot.Drops.rollMats(mt.mob(), mt.times(), mul, out);
            if (def.fragments() > 0) out.add(new ItemStack(rpg.core.item.ModItems.SCROLL_FRAGMENT.get(), def.fragments()));
            if (r.nextDouble() * 100 < def.scrollChance() * mul) out.add(rpg.core.equip.Enhance.randomScroll(m.level, r));
            // 잠재능력: 수상한 큐브 · 에픽 부여 주문서
            if (r.nextDouble() * 100 < def.rx().getOrDefault("cubeChance", 0.0) * mul)
                out.add(new ItemStack(rpg.core.item.ModItems.CUBE.get(), (int) Math.max(1, def.rx().getOrDefault("cubes", 1.0))));
            if (r.nextDouble() * 100 < def.rx().getOrDefault("epicScrollChance", 0.0) * mul)
                out.add(new ItemStack(rpg.core.item.ModItems.EPIC_POTENTIAL_SCROLL.get()));
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
        if (st != null && e.getLevel() instanceof ServerLevel sl) clearGimmicks(sl, st);
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
