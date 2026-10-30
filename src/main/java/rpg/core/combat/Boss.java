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
    public static void clear() { DEFS.clear(); TREASURE.clear(); POKE.clear(); GAP.clear(); }

    /** 원거리 평타 (대상이 평타 거리 밖일 때, 패턴 사이) · 뛰어 붙기 (대상이 dist칸 넘게 멀 때) — 보스 키별 */
    static final Map<String, Pattern> POKE = new HashMap<>(), GAP = new HashMap<>();

    /** 보스 보물 열기 표: 보물 아이템 id → [보스 키, 줄들]. 줄 = {아이템 id, 비중, 개수}. 특수 id: gold(개수 = 골드) · scroll(보스 레벨 무작위 주문서) · job:a|b|c|d(내 직업 것, 초보자는 무작위) */
    public record TreasureRow(String id, double weight, int count) {}
    public record TreasureTable(String boss, List<TreasureRow> rows) {}
    public static final Map<String, TreasureTable> TREASURE = new HashMap<>();

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
            if (o.has("poke")) {   // 원거리 평타: 대상 발밑 원
                JsonObject p = o.getAsJsonObject("poke");
                POKE.put(key, new Pattern("poke", s(p, "name", "원거리 공격"), "poke", d(p, "r", 2.5), 0, 0, 0, 0,
                        d(p, "delay", 1.0), d(p, "pct", 100), 0, 1, 1, nums(p)));
            }
            if (o.has("gap")) {    // 뛰어 붙기: 대상 자리로 점프
                JsonObject p = o.getAsJsonObject("gap");
                GAP.put(key, new Pattern("gap", s(p, "name", "덮치기"), "jump", d(p, "r", 3), 0, 0, 0, d(p, "track", 0),
                        d(p, "delay", 1.2), d(p, "pct", 120), 0, 1, 1, nums(p)));
            }
            JsonObject rw = o.has("reward") ? o.getAsJsonObject("reward") : new JsonObject();
            List<Mats> mats = new ArrayList<>();
            if (rw.has("mats")) for (var x : rw.getAsJsonArray("mats")) {
                JsonArray a = x.getAsJsonArray();
                mats.add(new Mats(a.get(0).getAsString(), a.get(1).getAsInt()));
            }
            List<String[]> ex = new ArrayList<>();   // 추가 보스 장신구 [아이템, 확률%]
            if (rw.has("extras")) for (var x : rw.getAsJsonArray("extras")) {
                JsonArray a = x.getAsJsonArray();
                ex.add(new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
            }
            EXTRAS.put(key, ex);
            if (rw.has("treasure")) {
                List<TreasureRow> rows = new ArrayList<>();
                for (var x : rw.getAsJsonArray("treasure")) {
                    JsonArray a = x.getAsJsonArray();
                    rows.add(new TreasureRow(a.get(0).getAsString(), a.get(1).getAsDouble(), a.size() > 2 ? a.get(2).getAsInt() : 1));
                }
                for (String[] e : ex) if (e[0].endsWith("_treasure")) TREASURE.put(e[0], new TreasureTable(key, rows));
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

    /** 보스별 추가 장신구 드롭 */
    public static final Map<String, List<String[]>> EXTRAS = new HashMap<>();

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
        Vec3 jumpFrom; long jumpStart, jumpLand;   // 점프 포물선
        boolean back;          // 독니: 지금 타가 뒤쪽 (뒷다리 차기)
        Pattern swing;         // 독니: 지금 타에 쓰는 패턴 (앞 = 자기, 뒤 = 뒷다리 차기)
        int hitNo;             // 연속 패턴 몇 번째 (0 = 처음)
        UUID lastFar;          // 원거리 견제로 방금 노린 사람 (같은 사람 연속 X)
        long transUntil;       // 페이즈 전환 연출 (무적·패턴 없음)
        int seen = 1;          // 필드 보스: 싸움에 들어온 최대 인원 (체력 배수)
        int done;             // 끝낸 패턴 수 (정기 휴식용)
        Pattern cur;
        int stage;            // 0 추적 · 1 확정 · 2 돌진 중
        long stageEnd;
        int repeatLeft;
        UUID target;
        Vec3 pos, dir;
        final Map<UUID, Vec3> rocks = new LinkedHashMap<>();
        final Map<UUID, Double> dmg = new HashMap<>();
        final Map<UUID, Double> threat = new HashMap<>();   // 노릴 대상 점수: 피해량 (멀리서 준 피해는 rangedThreat 배)
        long pokeReady, gapReady;
        final java.util.Set<String> usedHp = new java.util.HashSet<>();   // HP 조건 패턴 (천장 숨기 등) 쓴 것
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
        final BossX.X x = new BossX.X();   // 여왕 등 추가 기믹 (알·고치·독 구역)
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(Mob mob, MobData m, Def def) {
        State st = STATES.computeIfAbsent(mob.getUUID(), k -> new State());
        if (!st.init) {
            st.init = true;
            var pd = mob.getPersistentData();
            st.center = pd.contains("rpgBossCX") ? new Vec3(pd.getDouble("rpgBossCX"), pd.getDouble("rpgBossCY"), pd.getDouble("rpgBossCZ")) : mob.position();
            st.radius = def.arena();   // 경기장 크기는 항상 mobs.json (예전에 등록한 지점도 바뀐 크기로)
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

    static State stateOf(Mob mob) { return STATES.get(mob.getUUID()); }

    private static String barName(MobData m, Def def) {
        return (def.bar().isEmpty() ? m.name : def.bar()) + " Lv." + m.level + " · 방어율 " + Math.round(m.defRate) + "%";
    }

    static boolean valid(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative() && !RpgData.get(p).isDown();
    }

    static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static List<ServerPlayer> arena(ServerLevel l, State st, double extra) {
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
        if (st.jumpFrom == null) MobAI.gravity(mob, true);   // 점프 도중 끊김·재접속 → 중력 복구

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

        // 필드 보스: 싸우는 사람이 늘면 1명당 체력 +70% (최대 4명, 레이드는 입장 때 따로)
        if (mob.getPersistentData().getString("rpgRaid").isEmpty() && fighters.size() > st.seen && st.seen < 4) {
            int n = Math.min(4, fighters.size());
            double base = m.maxHp / (1 + 0.7 * (st.seen - 1)), add = base * 0.7 * (n - st.seen);
            m.maxHp += add; m.hp += add; st.seen = n;
            Mobs.sync(mob);
            for (ServerPlayer x : arena(l, st, 16))
                x.displayClientMessage(Component.literal(m.name + " 체력 증가 (" + n + "명)").withStyle(ChatFormatting.GOLD), true);
        }

        // 2페이즈 (transSec 있으면: 하던 패턴 끝나고 전환 연출 — 그동안 HP 는 경계에서 멈춤 · 무적)
        boolean trans = def.g("transSec", 0) > 0;
        if (st.phase == 1 && m.hp <= m.maxHp * def.phase2() / 100.0 + (trans ? 1 : 0) && (!trans || st.cur == null)) {
            st.phase = 2;
            st.deck.clear();
            st.bar.setColor(BossEvent.BossBarColor.PURPLE);
            for (ServerPlayer p : arena(l, st, 16))
                p.sendSystemMessage(Component.literal("[" + m.name + "] 분노했어요! 패턴이 더 거세져요").withStyle(ChatFormatting.LIGHT_PURPLE));
            l.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.6f);
            if (trans) {
                st.transUntil = now + ticks(def.g("transSec", 3));
                st.nextAt = st.transUntil + 10;
                BossX.clearFloor(l, st);   // 독 늪 · 거미줄 장판 · 떨어지는 것 정리
                for (ServerPlayer p : arena(l, st, 16)) Net.alert(p, "◆ 둥지가 무너진다 — 2페이즈", ChatFormatting.LIGHT_PURPLE, 70);
                l.playSound(null, mob.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.5f, 0.6f);
            }
        }

        // 기둥 기믹 (HP pillarAt% 에서 1번)
        if (!st.pillarsDone && !def.pillarKey().isEmpty() && m.hp <= m.maxHp * def.g("pillarAt", 50) / 100.0) spawnPillars(l, mob, m, st, def);
        // 단단한 버섯: propEvery 초마다 하나씩 (최대 props 개)
        if (def.g("props", 0) > 0 && now >= st.propNext) {
            st.propNext = now + Math.round(def.g("propEvery", 20) * 20);
            if (st.props.size() < def.g("props", 0)) placeProp(l, st, def);
        }

        // 레이드가 끝났는데 남은 보스 (서버 재시작 등): 지움
        if (mob.tickCount % 20 == 0 && !mob.getPersistentData().getString("rpgRaid").isEmpty() && l.getServer() != null
                && !rpg.core.world.Raids.bossActive(l.getServer(), mob.getUUID())) { mob.discard(); return; }
        BossX.ambient(mob, m, st, def, l, now);
        if (now < st.transUntil) {   // 전환 연출: 제자리 · 무적 · 패턴 없음
            mob.getNavigation().stop();
            if (now % 4 == 0) l.sendParticles(ParticleTypes.DRAGON_BREATH, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 1.5, 0.8, 1.5, 0.02);
            if (now % 10 == 0) l.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState()), st.center.x, st.center.y + 4, st.center.z, 40, st.radius / 2, 2, st.radius / 2, 0);
            return;
        }
        if (st.cur != null && BossX.TYPES.contains(st.cur.type())) { BossX.run(mob, m, st, def, l, now); return; }
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
        // HP 조건 패턴 (hpAt%, 한 번): 하던 패턴 끝나면 바로
        for (Pattern hp : def.patterns()) {
            if (hp.x("hpAt", 0) <= 0 || st.usedHp.contains(hp.id()) || !inPhase(st, hp)) continue;
            if (m.hp <= m.maxHp * hp.x("hpAt", 0) / 100.0 + 1) {
                st.usedHp.add(hp.id());
                start(mob, st, def, hp, t, fighters, l, now);
                return;
            }
        }
        if (now < st.nextAt || now < m.stunUntil) {
            // 패턴 사이: 붙어 있으면 기본 근접 공격
            if (def.melee() != null && now >= st.meleeReady && now >= m.stunUntil
                    && flat(mob.position(), t.position()) <= def.melee().r() && Math.abs(t.getY() - mob.getY()) < HEIGHT) {
                start(mob, st, def, def.melee(), t, fighters, l, now);
                return;
            }
            // 멀리 있는 대상: 뛰어 붙기 (dist칸 넘게) → 원거리 평타 (평타 거리 밖)
            double td = flat(mob.position(), t.position());
            Pattern gap = GAP.get(def.key()), poke = POKE.get(def.key());
            if (gap != null && now >= st.gapReady && now >= m.stunUntil && td > gap.x("dist", 12)) {
                start(mob, st, def, gap, t, fighters, l, now);
                return;
            }
            if (poke != null && now >= st.pokeReady && now >= m.stunUntil && td > (def.melee() != null ? def.melee().r() + 1 : 6)) {
                start(mob, st, def, poke, t, fighters, l, now);
                return;
            }
            if (mob.tickCount % 10 == 0) {
                if (mob.distanceTo(t) > 5) mob.getNavigation().moveTo(t, 1.0);
                else mob.getNavigation().stop();
            }
            mob.getLookControl().setLookAt(t, 30f, 30f);
            return;
        }
        // 원거리 견제: 10칸 넘게 떨어진 사람이 있으면 원거리 패턴(ranged) 먼저, far 패턴은 가장 먼 사람을 노림
        // (방금 노린 사람은 건너뜀 · 고치에 갇힌 사람 제외)
        double farD = 0;
        for (ServerPlayer f : fighters) if (free(st, f)) farD = Math.max(farD, flat(mob.position(), f.position()));
        Pattern p = draw(st, def, farD > def.g("farDist", 10));
        if (p == null) return;
        ServerPlayer far = p.x("far", 0) > 0 ? farPick(mob, l, st, st.lastFar) : null;
        if (far != null) st.lastFar = far.getUUID();
        start(mob, st, def, p, far != null ? far : t, fighters, l, now);
    }

    /** 지금 페이즈에 나올 수 있는 패턴 */
    static boolean inPhase(State st, Pattern p) {
        int only = (int) p.x("only", 0);
        return only > 0 ? only == st.phase : p.phase() <= st.phase;
    }

    /** 기본 공격 (패턴 아님): 근접 평타 · 원거리 평타 · 뛰어 붙기 */
    static boolean basic(Def def, Pattern p) {
        return p != null && (p == def.melee() || p == POKE.get(def.key()) || p == GAP.get(def.key()));
    }

    /** 고치에 안 갇힘 (패턴 대상이 될 수 있음) */
    static boolean free(State st, Player p) { return !st.x.cocoons.containsKey(p.getUUID()); }

    /** 원거리 견제 대상: 가장 먼 사람 (skip 이면 그다음, 고치 제외, 다 갇혔으면 아무나) */
    static ServerPlayer farPick(Mob mob, ServerLevel l, State st, UUID skip) {
        List<ServerPlayer> pool = new ArrayList<>();
        for (ServerPlayer x : arena(l, st, 0)) if (valid(x) && free(st, x)) pool.add(x);
        if (pool.isEmpty()) for (ServerPlayer x : arena(l, st, 0)) if (valid(x)) pool.add(x);
        if (pool.isEmpty()) return null;
        pool.sort((a, b) -> Double.compare(flat(mob.position(), b.position()), flat(mob.position(), a.position())));
        if (pool.size() > 1 && skip != null && pool.get(0).getUUID().equals(skip)) return pool.get(1);
        return pool.get(0);
    }

    /** 대상: 도발 > 위협 점수 (피해량, 멀리서 준 피해는 1.5배) 가장 높은 사람 > 가까운 사람 */
    private static ServerPlayer pickTarget(Mob mob, MobData m, State st, List<ServerPlayer> fighters, long now) {
        Player tt = MobAI.taunter(mob, m, now);
        if (tt instanceof ServerPlayer sp && fighters.contains(sp)) return sp;
        ServerPlayer best = null;
        double bd = -1;
        List<ServerPlayer> freeF = new ArrayList<>();
        for (ServerPlayer p : fighters) if (free(st, p)) freeF.add(p);
        if (!freeF.isEmpty()) fighters = freeF;   // 고치에 갇힌 사람은 안 노림
        for (ServerPlayer p : fighters) {
            double v = st.threat.getOrDefault(p.getUUID(), 0.0);
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
    private static Pattern draw(State st, Def def, boolean preferRanged) {
        if (st.deck.isEmpty()) {
            for (Pattern p : def.patterns())   // only = 그 페이즈에서만 (여왕: 2페이즈는 패턴 묶음이 바뀜), hpAt 패턴은 덱에 안 넣음
                if (p.x("hpAt", 0) <= 0 && inPhase(st, p)) st.deck.add(p.id());
            Collections.shuffle(st.deck);
            if (st.deck.size() > 1 && st.deck.get(0).equals(st.last)) Collections.swap(st.deck, 0, st.deck.size() - 1);
        }
        if (st.deck.isEmpty()) return null;
        String id = null;
        if (preferRanged) for (int i = 0; i < st.deck.size() && id == null; i++) {   // 멀리 있는 사람 견제: 덱에 남은 원거리 패턴 먼저
            String c = st.deck.get(i);
            if (c.equals(st.last)) continue;
            for (Pattern q : def.patterns()) if (q.id().equals(c) && q.x("ranged", 0) > 0) { id = st.deck.remove(i); break; }
        }
        if (id == null) id = st.deck.remove(0);
        st.last = id;
        for (Pattern p : def.patterns()) if (p.id().equals(id)) return p;
        return null;
    }

    // ───────── 패턴 ─────────
    static int ticks(double sec) { return (int) Math.max(4, Math.round(sec * 20)); }

    /** 돌진 이동 시간 (틱) */
    private static final int DASH_TICKS = 10;

    private static void start(Mob mob, State st, Def def, Pattern p, ServerPlayer t, List<ServerPlayer> fighters, ServerLevel l, long now) {
        st.cur = p;
        st.stage = 1;
        st.alt = false;
        st.back = false;
        st.swing = p;
        st.hitNo = 0;
        mob.getNavigation().stop();
        Vec3 to = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
        st.dir = to.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, mob.getYRot()) : to.normalize();
        st.pos = mob.position();
        int dl = ticks(p.delay());
        // 큰 패턴 (예고 2초 이상): 화면 위 가운데에 이름 (알림이 따로 있는 패턴 빼고)
        if (!basic(def, p) && p.delay() >= 2.0 && !BossX.TYPES.contains(p.type()) && !java.util.Set.of("share", "mark", "counter").contains(p.type()))
            for (ServerPlayer x : arena(l, st, 16)) Net.alert(x, "◆ " + p.name(), ChatFormatting.YELLOW, 50);
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
            case "web", "share" -> {   // 거미줄 발사 (먼 사람 여럿) · 먹잇감 (한 명, 보라)
                st.repeatLeft = 1;
                st.rocks.clear();
                int n = p.type().equals("web") && st.phase >= 2 ? (int) p.x("targets2", p.x("targets", 1)) : (int) p.x("targets", 1);
                List<ServerPlayer> pool = new ArrayList<>();
                for (ServerPlayer f : fighters) if (free(st, f)) pool.add(f);   // 고치에 갇힌 사람 제외
                if (pool.isEmpty()) pool.addAll(fighters);
                Collections.shuffle(pool);
                if (p.x("center", 0) > 0) {   // 모두의 가운데에 가장 가까운 사람 (모이기 쉽게)
                    double cx = 0, cz = 0;
                    for (ServerPlayer f : pool) { cx += f.getX(); cz += f.getZ(); }
                    Vec3 mid = new Vec3(cx / pool.size(), 0, cz / pool.size());
                    pool.sort((a, b) -> Double.compare(flat(a.position(), mid), flat(b.position(), mid)));
                } else if (p.x("far", 0) > 0) {   // 먼 사람부터 (방금 노린 사람은 뒤로)
                    pool.sort((a, b) -> Double.compare(flat(mob.position(), b.position()), flat(mob.position(), a.position())));
                    if (pool.size() > n && st.lastFar != null && pool.get(0).getUUID().equals(st.lastFar)) pool.add(pool.remove(0));
                    if (!pool.isEmpty()) st.lastFar = pool.get(0).getUUID();
                } else if (pool.contains(t)) { pool.remove(t); pool.add(0, t); }
                for (int i = 0; i < Math.min(n, pool.size()); i++) st.rocks.put(pool.get(i).getUUID(), pool.get(i).position());
                beginTrack(mob, st, p, now);
                if (p.type().equals("share")) {
                    for (ServerPlayer x : arena(l, st, 16))
                        Net.alert(x, "◆ " + p.name() + " — 보라 원에 모이세요!" + (p.x("outPct", 0) > 0 ? " (원 밖 = 최대 HP " + (int) p.x("outPct", 0) + "%)" : ""), ChatFormatting.LIGHT_PURPLE, 70);
                    l.playSound(null, mob.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2f, 0.7f);
                }
                return;
            }
            case "mark" -> {   // 독 표식: 모두 (고치에 갇힌 사람 제외)
                st.rocks.clear();
                for (ServerPlayer f : fighters) if (free(st, f)) st.rocks.put(f.getUUID(), f.position());
                beginTrack(mob, st, p, now);
                for (ServerPlayer x : arena(l, st, 16))
                    Net.alert(x, "◆ " + p.name() + " — 흩어지세요! 겹치면 피해 2배", ChatFormatting.RED, 70);
                l.playSound(null, mob.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 2f, 0.6f);
                return;
            }
            case "fang" -> {   // 독니 찌르기 · 뒷다리 차기(back) · 앞뒤 연타(hits, 뒤 타 = 이 보스의 back 패턴)
                st.repeatLeft = (int) Math.max(1, p.x("hits", 1));
                st.back = p.x("back", 0) > 0;
                st.swing = p;
                if (st.back) st.dir = st.dir.scale(-1);
                Net.telegraph(l, st.pos, st.dir, MobAI.CONE, st.swing.r(), st.swing.angle(), dl);
                if (st.back) l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 2f, 0.4f);
                if (st.repeatLeft > 1) for (ServerPlayer x : arena(l, st, 16))
                    Net.alert(x, "◆ " + p.name() + " — 앞 · 뒤 번갈아 공격! 옆으로", ChatFormatting.YELLOW, 50);
            }
            case "eggs", "pull", "lanes", "absorb", "collapse", "frenzy", "barrage", "volley", "webrain", "burst" -> { BossX.start(mob, st, def, p, t, l, now); return; }
            case "delayed", "jump" -> {
                st.repeatLeft = p.type().equals("jump") && st.phase >= 2 ? Math.max(1, p.repeat2()) : 1;
                if (p.type().equals("jump") && !basic(def, p) && st.props.isEmpty() && def.g("props", 0) > 0) placeProp(l, st, def);
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
                    Net.alert(x, "◆ " + p.name() + " — " + (int) p.x("window", 2) + "초 안에 때려서 끊으세요!", ChatFormatting.AQUA, 60);
                l.playSound(null, mob.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 1.4f);
                st.stageEnd = now + w;
                return;
            }
            case "roar" -> { Net.telegraph(l, st.pos, st.dir, MobAI.CIRCLE, p.r(), 0, dl); standing(mob, true); }
            case "poke" -> {   // 원거리 평타: 대상 발밑 원 (추적 없음)
                st.rocks.clear();
                st.rocks.put(t.getUUID(), t.position());
                Net.telegraph(l, t.position(), st.dir, MobAI.CIRCLE, p.r(), 0, dl);
                l.playSound(null, mob.blockPosition(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.5f, 0.6f);
            }
            default -> { Net.telegraph(l, st.pos, st.dir, MobAI.CIRCLE, p.r(), 0, dl); standing(mob, true); }   // slam
        }
        st.stageEnd = now + dl + HIT_LAG;
        l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1f, 0.5f);
    }

    /** 판정을 화면보다 조금 늦게 (빨강이 다 찬 걸 보고 맞게) */
    static final int HIT_LAG = 2;
    /** 판정 여유: 몸 폭 0.3 + 0.3 */
    static final double PAD = 0.3, HEIGHT = 4;   // 판정 = 그려진 모양 + 몸 반 폭 (몸이 선에 걸치면 맞음)

    /** 직선 추적 (돌진·광선): 대상 쪽 노란 선 */
    private static void beginCharge(Mob mob, State st, Pattern p, ServerPlayer t, long now) {
        st.target = t == null ? st.target : t.getUUID();
        st.stage = 0;
        st.stageEnd = now + ticks(p.track());
        st.pos = mob.position();
        if (t != null) Net.track((ServerLevel) mob.level(), mob.getId(), t.getId(), MobAI.LINE, p.len(), p.w(), ticks(p.track()));
    }

    /** 원 추적 (바위·지연·점프): st.rocks 의 플레이어 발밑 노란 원 */
    static void beginTrack(Mob mob, State st, Pattern p, long now) {
        ServerLevel l = (ServerLevel) mob.level();
        st.stage = 0;
        st.stageEnd = now + ticks(p.track());
        for (UUID id : st.rocks.keySet()) {
            Player pl = l.getPlayerByUUID(id);
            if (pl != null) Net.track(l, mob.getId(), pl.getId(), p.type().equals("share") ? 3 : MobAI.CIRCLE, p.r(), 0, ticks(p.track()));
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

    static ServerPlayer randomFighter(ServerLevel l, State st) {
        List<ServerPlayer> fs = new ArrayList<>();
        for (ServerPlayer x : arena(l, st, 0)) if (valid(x)) fs.add(x);
        return fs.isEmpty() ? null : fs.get(ThreadLocalRandom.current().nextInt(fs.size()));
    }

    static ServerPlayer currentTarget(ServerLevel l, State st) {
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
            Vec3 fd = st.back ? st.dir.scale(-1) : st.dir;   // 뒷다리 차기: 앞을 본 채로 뒤를 참
            float yaw = (float) Math.toDegrees(Math.atan2(-fd.x, fd.z));
            mob.setYRot(yaw); mob.setYHeadRot(yaw); mob.setYBodyRot(yaw);
        }
        if (st.back && st.stage == 1 && now % 3 == 0)   // 뒷다리 차기 예고: 몸이 빨개짐
            l.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1f, 0.1f, 0.1f), 2.2f),
                    mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 10, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0);
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
                int dl = ticks(st.hitNo > 0 ? p.x("delay2", p.delay()) : p.delay());
                st.stage = 1;
                st.stageEnd = now + dl + HIT_LAG;
                st.pos = mob.position();
                Net.track(l, mob.getId(), -1, 0, 0, 0, 0);   // 노랑 지우기
                if (line) Net.telegraph(l, st.pos, st.dir, MobAI.LINE, p.len(), p.w(), dl);
                else for (Vec3 q : st.rocks.values()) Net.telegraph(l, q, st.dir, p.type().equals("share") ? 40 + MobAI.CIRCLE : MobAI.CIRCLE, p.r(), 0, dl);
                if (p.type().equals("jump")) {   // 포물선: 앞부분은 웅크리고, 마지막 1초 동안 날아가서 예고가 다 찰 때 착지
                    st.jumpFrom = mob.position();
                    st.jumpLand = now + dl;
                    st.jumpStart = st.jumpLand - Math.min(20, dl);
                    l.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1f, 1.4f);
                }
                l.playSound(null, mob.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1f, 0.5f);
            }
            return;
        }
        if (st.stage == 1 && p.type().equals("jump") && st.jumpFrom != null && !st.rocks.isEmpty() && now >= st.jumpStart && now < st.jumpLand) {
            Vec3 q = st.rocks.values().iterator().next();
            double f = Math.min(1, (now + 1 - st.jumpStart) / (double) Math.max(1, st.jumpLand - st.jumpStart));
            double h = 4 + flat(st.jumpFrom, q) * 0.12;
            Vec3 next = st.jumpFrom.lerp(q, f).add(0, 4 * h * f * (1 - f), 0);
            MobAI.gravity(mob, false);
            mob.setDeltaMovement(next.subtract(mob.position()));
            mob.hurtMarked = true;
            if (now == st.jumpStart) l.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.2, mob.getZ(), 12, 1, 0.1, 1, 0.05);
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
            case "fang" -> {
                if (--st.repeatLeft > 0) {   // 반대 방향 (앞 ↔ 뒤)
                    st.dir = st.dir.scale(-1);
                    st.back = !st.back;
                    st.swing = st.back ? backOf(def, p) : p;
                    st.hitNo++;
                    int d2 = ticks(p.x("delay2", p.delay()));
                    Net.telegraph(l, st.pos, st.dir, MobAI.CONE, st.swing.r(), st.swing.angle(), d2);
                    if (st.back) l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_HURT, SoundSource.HOSTILE, 2f, 0.4f);
                    st.stageEnd = now + d2 + HIT_LAG;
                    st.stage = 1;
                } else finish(mob, st, def, now);
            }
            case "beam" -> {
                ServerPlayer nt = --st.repeatLeft > 0 ? randomFighter(l, st) : null;
                if (nt != null) beginCharge(mob, st, p, nt, now);
                else finish(mob, st, def, now);
            }
            case "jump" -> {   // 연속 도약: far 면 그다음 먼 사람
                UUID prev = st.rocks.isEmpty() ? null : st.rocks.keySet().iterator().next();
                ServerPlayer nt = !stunned && --st.repeatLeft > 0 ? (p.x("far", 0) > 0 ? farPick(mob, l, st, prev) : currentTarget(l, st)) : null;
                if (nt != null) {
                    if (p.x("far", 0) > 0) st.lastFar = nt.getUUID();
                    st.hitNo++;
                    st.rocks.clear(); st.rocks.put(nt.getUUID(), nt.position()); beginTrack(mob, st, p, now);
                } else finish(mob, st, def, now);
            }
            default -> finish(mob, st, def, now);
        }
    }

    static void finish(Mob mob, State st, Def def, long now) {
        if (st.x.hidePos != null) BossX.hideEnd(mob, st, (ServerLevel) mob.level());   // 천장 숨기 도중 끝남 (아무도 없음 등)
        MobAI.gravity(mob, true);
        st.jumpFrom = null;
        st.back = false;
        if (st.cur != null && st.stage == 0) Net.track((ServerLevel) mob.level(), mob.getId(), -1, 0, 0, 0, 0);
        if (st.cur != null && basic(def, st.cur)) {   // 기본 공격 (근접·원거리·뛰어 붙기)은 패턴 대기 시간을 안 늘림
            if (st.cur == def.melee()) st.meleeReady = now + Math.round(def.meleeCd() * 20);
            else if (st.cur.id().equals("poke")) st.pokeReady = now + Math.round(st.cur.x("cd", 3) * 20);
            else st.gapReady = now + Math.round(st.cur.x("cd", 8) * 20);
            st.cur = null;
            st.rocks.clear();
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
        // 정기 휴식: restEvery 패턴마다 restSec초 동안 기본 공격만 (딜 타임, 메시지 없이 구름만)
        int every = (int) def.g("restEvery", 0);
        if (every > 0 && ++st.done % every == 0) {
            double sec = def.g("restSec", 4);
            st.nextAt += Math.round(sec * 20);
            ServerLevel l = (ServerLevel) mob.level();
            l.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 1.5, mob.getZ(), 12, 1.2, 0.6, 1.2, 0.01);
        }
    }

    static void hitCircle(Mob mob, MobData m, State st, ServerLevel l, Vec3 c, double r, Pattern p) {
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
            case "fang" -> BossX.cone(mob, m, st, l, st.swing != null ? st.swing : p, c, dir);
            case "web" -> {
                for (Vec3 q : st.rocks.values()) {
                    l.sendParticles(p.x("cocoon", 0) > 0 ? ParticleTypes.ITEM_SNOWBALL : ParticleTypes.SPORE_BLOSSOM_AIR, q.x, q.y + 0.6, q.z, 30, p.r() / 2, 0.3, p.r() / 2, 0.05);
                    for (ServerPlayer pl : arena(l, st, 4)) {
                        if (!valid(pl) || flat(pl.position(), q) > p.r() + PAD || Math.abs(pl.getY() - q.y) > HEIGHT) continue;
                        hit(mob, m, st, l, pl, p);
                        BossX.cocoon(mob, m, l, pl, p.x("cocoon", 4), now);
                    }
                }
                l.playSound(null, mob.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 2f, 0.6f);
            }
            case "share" -> {
                java.util.Set<ServerPlayer> inAll = new java.util.HashSet<>();
                for (Vec3 q : st.rocks.values()) {
                    List<ServerPlayer> in = new ArrayList<>();
                    for (ServerPlayer pl : arena(l, st, 4)) if (valid(pl) && flat(pl.position(), q) <= p.r() + PAD && Math.abs(pl.getY() - q.y) <= HEIGHT) in.add(pl);
                    if (in.isEmpty() && p.x("outPct", 0) <= 0)   // 대상이 원 밖으로 도망가면 혼자 다 맞음 (outPct 있으면 원 밖 규칙)
                        for (var re : st.rocks.entrySet()) if (re.getValue() == q && l.getPlayerByUUID(re.getKey()) instanceof ServerPlayer tp && valid(tp)) in.add(tp);
                    l.sendParticles(ParticleTypes.DRAGON_BREATH, q.x, q.y + 0.5, q.z, 60, p.r() / 2, 0.3, p.r() / 2, 0.02);
                    l.playSound(null, BlockPos.containing(q), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.7f);
                    int alone = 0;
                    for (ServerPlayer x : arena(l, st, 0)) if (valid(x)) alone++;
                    double total = alone <= 1 ? p.x("maxPctSolo", p.x("maxPct", 90)) : p.x("maxPct", 90);   // 혼자 싸우면 약하게
                    for (ServerPlayer pl : in) CombatEvents.maxHpHit(mob, pl, total / Math.max(1, in.size()));
                    inAll.addAll(in);
                    if (!in.isEmpty()) for (ServerPlayer x : arena(l, st, 16))
                        x.displayClientMessage(Component.literal(p.name() + ": " + in.size() + "명이 나눠 맞음").withStyle(ChatFormatting.LIGHT_PURPLE), true);
                }
                // 원 밖에 있던 사람: 최대 HP outPct% + 독 (혼자여도: 원 안 maxPctSolo · 밖 outPct)
                if (p.x("outPct", 0) > 0) for (ServerPlayer pl : arena(l, st, 0)) {
                    if (!valid(pl) || inAll.contains(pl)) continue;
                    hitAs(mob, m, st, l, pl, p.name() + " (원 밖)", 0, p.x("outPct", 0), p.x("outPoison", 0));
                }
            }
            case "mark" -> {
                List<Vec3> qs = new ArrayList<>(st.rocks.values());
                for (Vec3 q : qs) l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y + 0.3, q.z, 3, p.r() / 3, 0.1, p.r() / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.9f);
                for (ServerPlayer pl : arena(l, st, 4)) {
                    if (!valid(pl)) continue;
                    int n = 0;
                    for (Vec3 q : qs) if (flat(pl.position(), q) <= p.r() + PAD && Math.abs(pl.getY() - q.y) <= HEIGHT) n++;
                    if (n == 0) continue;
                    CombatEvents.mobSkillHit(mob, pl, p.pct() * (n >= 2 ? 2 : 1));
                    if (n >= 2) pl.displayClientMessage(Component.literal("폭발이 겹쳤어요! 피해 2배").withStyle(ChatFormatting.RED), true);
                    BossX.poison(pl, p.x("poison", 0), now);
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
            case "poke" -> {
                for (Vec3 q : st.rocks.values()) {
                    l.sendParticles(ParticleTypes.ITEM_SLIME, q.x, q.y + 0.4, q.z, 16, p.r() / 2, 0.2, p.r() / 2, 0.05);
                    l.sendParticles(ParticleTypes.CRIT, q.x, q.y + 0.4, q.z, 10, p.r() / 2, 0.2, p.r() / 2, 0.1);
                    l.playSound(null, BlockPos.containing(q), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.HOSTILE, 1.5f, 0.6f);
                    hitCircle(mob, m, st, l, q, p.r(), p);
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
                MobAI.gravity(mob, true);
                st.jumpFrom = null;
                mob.teleportTo(q.x, q.y, q.z);   // 거의 다 와 있음 (마지막 보정)
                mob.setDeltaMovement(Vec3.ZERO);
                l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y + 0.2, q.z, 6, p.r() / 3, 0.1, p.r() / 3, 0);
                l.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.2f, 0.6f);
                hitCircle(mob, m, st, l, q, p.r(), p);
                // 단단한 버섯이 범위 안에 있으면 부서지면서 보스 기절 (뛰어 붙기는 제외)
                if (!basic(def, p)) for (BlockPos bp : new ArrayList<>(st.props)) {
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
        BossX.clear(l, st);
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
    static void push(ServerPlayer p, Vec3 dir, double blocks) {
        double resist = Math.min(100, RpgData.get(p).bonus("kbResist"));
        double b = blocks * (1 - resist / 100.0);
        if (b <= 0.1) return;
        double sp = Math.min(2.5, b * 0.2);
        p.setDeltaMovement(dir.x * sp, 0.42, dir.z * sp);
        p.hurtMarked = true;
    }

    /** 이름·수치를 직접 주는 피해 (콤보 두 번째 타 등): pct = 공격력 %, maxPct > 0 이면 최대 HP % */
    static void hitAs(Mob mob, MobData m, State st, ServerLevel l, ServerPlayer p, String name, double pct, double maxPct, double poison) {
        p.displayClientMessage(Component.literal("◆ " + name + "에 맞음").withStyle(ChatFormatting.GRAY), true);
        if (maxPct > 0) CombatEvents.maxHpHit(mob, p, maxPct);
        else CombatEvents.mobSkillHit(mob, p, pct);
        if (poison > 0) BossX.poison(p, poison, l.getGameTime());
        PlayerData d = RpgData.get(p);
        if (!p.isAlive() || d.isDown() || d.hp <= 0)
            for (ServerPlayer x : arena(l, st, 16))
                x.sendSystemMessage(Component.literal(p.getGameProfile().getName() + "님이 " + m.name + "의 [" + name + "]에 쓰러졌어요").withStyle(ChatFormatting.RED));
    }

    /** 앞뒤 연타의 뒤쪽 타: 이 보스의 back 패턴 (뒷다리 차기), 없으면 자기 */
    static Pattern backOf(Def def, Pattern p) {
        if (p.x("back", 0) > 0) return p;
        for (Pattern q : def.patterns()) if (q.type().equals("fang") && q.x("back", 0) > 0) return q;
        return p;
    }

    /** 패턴 피해 + 쓰러지면 경기장에 알림 */
    static void hit(Mob mob, MobData m, State st, ServerLevel l, ServerPlayer p, Pattern pt) {
        if (pt != DEFS.get(m.key).melee()) p.displayClientMessage(Component.literal("◆ " + pt.name() + "에 맞음").withStyle(ChatFormatting.GRAY), true);   // 뭐에 맞았는지
        if (pt.x("maxPct", 0) > 0) CombatEvents.maxHpHit(mob, p, pt.x("maxPct", 0));   // 최대 HP 비율
        else CombatEvents.mobSkillHit(mob, p, pt.pct());
        if (pt.x("poison", 0) > 0) BossX.poison(p, pt.x("poison", 0), l.getGameTime());   // 독 (초)
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
        if (st.x.hidePos != null) BossX.hideEnd(mob, st, (ServerLevel) mob.level());
        clearGimmicks((ServerLevel) mob.level(), st);
        mob.setGlowingTag(false);
        if (st.seen > 1) { m.maxHp = m.maxHp / (1 + 0.7 * (st.seen - 1)); st.seen = 1; }   // 인원 배수 원래대로
        m.hp = m.maxHp;
        mob.setHealth(mob.getMaxHealth());
        st.phase = 1;
        st.deck.clear();
        st.cur = null;
        st.dmg.clear();
        st.threat.clear();
        st.usedHp.clear();
        st.transUntil = 0;
        st.lastFar = null;
        st.back = false;
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
        { var pd = RpgData.get(p); double dp = Combat.dmgPct(pd);   // 보스%: 데미지%와 더해짐 (스공에 이미 × (1+데미지%) 들어 있음)
          dmg *= (1 + (dp + pd.bonus("bossPct") + rpg.core.skill.PassiveEffects.extraBoss(pd)) / 100.0) / (1 + dp / 100.0); }
        int diff = m.level - RpgData.get(p).level;
        if (diff > 0) dmg *= Math.max(1 - def.maxPenalty() / 100.0, 1 - def.penaltyPerLevel() * diff / 100.0);
        dmg = Math.max(1, Math.round(dmg));
        State st = STATES.get(mob.getUUID());
        if (st != null) {
            ServerLevel l = (ServerLevel) mob.level();
            long now = l.getGameTime();
            if (now < st.transUntil) return 0;   // 페이즈 전환 연출 중 무적
            if (st.x.hidePos != null) return 0;   // 천장 숨기 중 무적
            if (!st.pillars.isEmpty() && pillarsAlive(l, st)) dmg = Math.max(1, Math.round(dmg * (1 - def.g("pillarReduce", 80) / 100.0)));   // 기둥
            if (now < st.vulnUntil) dmg = Math.round(dmg * (1 + def.g("counterVuln", 30) / 100.0));                                            // 카운터 성공
            if (st.phase == 1 && def.g("transSec", 0) > 0) {   // 전환 전엔 경계 HP 아래로 안 내려감
                double floor = m.maxHp * def.phase2() / 100.0;
                if (m.hp - dmg < floor) dmg = Math.max(0, Math.floor(m.hp - floor));
            }
            for (Pattern hp : def.patterns()) {   // HP 조건 패턴 (천장 숨기): 나오기 전엔 그 HP 아래로 안 내려감
                if (hp.x("hpAt", 0) <= 0 || st.usedHp.contains(hp.id()) || !inPhase(st, hp)) continue;
                double floor = m.maxHp * hp.x("hpAt", 0) / 100.0;
                if (m.hp > floor && m.hp - dmg < floor) dmg = Math.max(0, Math.floor(m.hp - floor));
            }
            if (st.cur != null && st.cur.type().equals("counter")) st.counterDmg += dmg;
            st.dmg.merge(p.getUUID(), Math.min(dmg, m.hp), Double::sum);
            double far = flat(mob.position(), p.position()) > def.g("rangedDist", 8) ? def.g("rangedThreat", 1.5) : 1;   // 멀리서 때리면 위협 더 큼
            st.threat.merge(p.getUUID(), dmg * far, Double::sum);
        }
        return dmg;
    }

    // ───────── 처치 ─────────
    /** 참여 기준 (보스 최대 체력 비율), 보물·골드 제한 레벨 차이 */
    public static final double PART_MIN = 0.05;
    public static final int OVER_LEVEL = 8;

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
            if (p instanceof ServerPlayer sp && flat(sp.position(), st.center) <= st.radius + 24 && e.getValue() >= m.maxHp * PART_MIN)
                part.put(sp, e.getValue());   // 보스 체력 5% 이상 딜한 사람만 참여자
        }
        // 레이드 보스: 방에 있는 레이드 인원 전부 참여자 (딜 5% 못 넣어도)
        if (!mob.getPersistentData().getString("rpgRaid").isEmpty() && l.getServer() != null)
            for (ServerPlayer rp : rpg.core.world.Raids.membersOfBoss(l.getServer(), mob.getUUID()))
                if (flat(rp.position(), st == null ? mob.position() : st.center) <= (st == null ? 40 : st.radius + 24))
                    part.putIfAbsent(rp, st == null ? 0.0 : st.dmg.getOrDefault(rp.getUUID(), 0.0));
        if (part.isEmpty() && killer != null) part.put(killer, 1.0);
        // 레이드 보스: 보상은 하루 1번 (오늘 이미 받은 사람은 클리어만 기록)
        String raid = mob.getPersistentData().getString("rpgRaid");
        if (!raid.isEmpty()) {
            part.keySet().removeIf(p -> {
                if (rpg.core.world.Raids.rewardToday(p, raid)) return false;
                p.sendSystemMessage(Component.literal("[레이드] 오늘은 이미 보상을 받았어요 (클리어만 기록, 오전 6시 초기화)").withStyle(ChatFormatting.GRAY));
                return true;
            });
            if (l.getServer() != null) rpg.core.world.Raids.bossDied(l.getServer(), mob.getUUID());
        }
        double total = 0;
        for (double v : part.values()) total += v;
        double bestDrop = 0;
        for (var e : part.entrySet()) {
            ServerPlayer p = e.getKey();
            PlayerData d = RpgData.get(p);
            bestDrop = Math.max(bestDrop, d.dropBonus());
            double share = e.getValue() / Math.max(1, total);
            int people = Math.max(1, mob.getPersistentData().getInt("rpgRaidPeople"));   // 레이드: 체력이 인원수 배라 경험치·골드도 인원수 배 (평균 1인분)
            double rewardShare = share * people;
            boolean over = d.level >= m.level + OVER_LEVEL;   // 보스보다 15레벨 이상: 골드 절반 · 보물 없음
            long exp = Math.max(1, Math.round(m.exp * rewardShare * CombatEvents.expLevelMul(m.level, d.level)));
            long gold = Math.max(1, Math.round(m.gold * rewardShare * (1 + d.goldBonus() / 100.0) * (over ? 0.5 : 1)));
            d.gold += gold;
            Progress.giveExp(p, exp);
            Net.reward(p, exp, gold);
            RpgData.sync(p);
            p.sendSystemMessage(Component.literal("[" + m.name + " 처치] 기여도 " + Math.round(share * 100) + "% · 경험치 " + String.format("%,d", exp)
                    + " · " + String.format("%,d", gold) + " G").withStyle(ChatFormatting.GOLD));
        }
        rpg.core.quest.QuestEvents.onKill(new ArrayList<>(part.keySet()), m);
        for (ServerPlayer p : part.keySet()) rpg.core.quest.Achievements.onBoss(p, m.key);   // 업적: 잡은 보스 종류
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
        for (ServerPlayer owner : part.keySet()) {
            List<ItemStack> out = new ArrayList<>();
            boolean over = RpgData.get(owner).level >= m.level + OVER_LEVEL;
            if (!def.sure().isEmpty()) out.add(rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + def.sure(), 1));
            for (Mats mt : def.mats()) rpg.core.loot.Drops.rollMats(mt.mob(), mt.times(), mul, out);
            if (def.fragments() > 0) out.add(new ItemStack(rpg.core.item.ModItems.SCROLL_FRAGMENT.get(), def.fragments()));
            if (r.nextDouble() * 100 < def.scrollChance() * mul) out.add(rpg.core.equip.Enhance.randomScroll(m.level, r));
            // 잠재능력: 수상한 큐브 · 에픽 부여 주문서
            if (r.nextDouble() * 100 < def.rx().getOrDefault("cubeChance", 0.0) * mul)
                out.add(new ItemStack(rpg.core.item.ModItems.CUBE.get(), (int) Math.max(1, def.rx().getOrDefault("cubes", 1.0))));
            if (r.nextDouble() * 100 < def.rx().getOrDefault("epicScrollChance", 0.0) * mul)
                out.add(new ItemStack(rpg.core.item.ModItems.EPIC_POTENTIAL_SCROLL.get()));
            // 환생의 불꽃
            if (r.nextDouble() * 100 < def.rx().getOrDefault("eternalFlameChance", 0.0) * mul)
                out.add(new ItemStack(rpg.core.item.ModItems.ETERNAL_FLAME.get()));
            if (r.nextDouble() * 100 < def.rx().getOrDefault("blackFlameChance", 0.0) * mul)
                out.add(new ItemStack(rpg.core.item.ModItems.BLACK_FLAME.get()));
            for (String[] ex : EXTRAS.getOrDefault(def.key(), List.of())) {
                if (r.nextDouble() * 100 >= Double.parseDouble(ex[1]) * mul) continue;
                if (over && ex[0].endsWith("_treasure")) continue;   // 고레벨: 보물 없음
                String[] pick = ex[0].split("\\|");   // a|b|c = 그중 무작위 하나
                ItemStack g = rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + pick[r.nextInt(pick.length)], 1);
                if (g.isEmpty()) continue;
                rpg.core.equip.Gear.roll(g);
                out.add(g);
                if (!ex[0].endsWith("_treasure")) for (ServerPlayer p : part.keySet())
                    p.sendSystemMessage(Component.literal("✦ " + g.getHoverName().getString() + " 이(가) 떨어졌어요! (" + owner.getGameProfile().getName() + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (!def.ring().isEmpty() && r.nextDouble() * 100 < def.ringChance() * mul) {
                ItemStack g = rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + def.ring(), 1);
                rpg.core.equip.Gear.roll(g);
                out.add(g);
                for (ServerPlayer p : part.keySet())
                    p.sendSystemMessage(Component.literal("✦ " + g.getHoverName().getString() + " 이(가) 떨어졌어요! (" + owner.getGameProfile().getName() + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            // 이 참여자 몫: 30초 동안 주인만 주움
            for (ItemStack s : out) {
                if (s.isEmpty()) continue;
                ItemEntity it = new ItemEntity(l, mob.getX() + r.nextDouble() * 2 - 1, mob.getY() + 0.5, mob.getZ() + r.nextDouble() * 2 - 1, s);
                it.setDeltaMovement(r.nextDouble() * 0.3 - 0.15, 0.3, r.nextDouble() * 0.3 - 0.15);
                it.setDefaultPickUpDelay();
                rpg.core.loot.Loot.claim(it, owner, 30);
                l.addFreshEntity(it);
            }
        }
        l.playSound(null, mob.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.HOSTILE, 1f, 1f);
    }

    /** 보스가 사라지면(청크 언로드·명령어 삭제) 보스바 정리 */
    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        State st = STATES.remove(e.getEntity().getUUID());
        if (st != null && st.bar != null) st.bar.removeAllPlayers();
        // 청크가 내려가는 도중(엔티티 추적 끝)에 블록·엔티티를 건드리면 청크 관리자가 꼬여서 게임이 터짐 → 다음 틱에 정리
        if (st != null && e.getLevel() instanceof ServerLevel sl) LATER.add(() -> clearGimmicks(sl, st));
    }

    private static final java.util.List<Runnable> LATER = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void later(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || LATER.isEmpty()) return;
        var run = new ArrayList<>(LATER); LATER.clear();
        for (Runnable r : run) { try { r.run(); } catch (Exception ex) { ex.printStackTrace(); } }
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
