package rpg.core.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/**
 * 레이드 입장 시스템 (보스·보상은 나중): 입구 우클릭 → 입장 창 → 준비 확인 (30초) → 10초 뒤 비어 있는 방으로.
 * 방 안 제한 시간 15분 (보스바), 전멸·시간 초과·전원 이탈 = 실패 → 입구로.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Raids {
    private Raids() {}

    public static final int LIMIT_TICKS = 15 * 60 * 20, READY_TICKS = 30 * 20, COUNT_TICKS = 10 * 20, NEAR = 16, MAX_PARTY = 4;

    // ───────── 저장: 레이드 정의 ─────────
    public static final class Def {
        public String name; public BlockPos gate; public final List<BlockPos> rooms = new ArrayList<>(); public int minLevel;
        /** 보스 키 (mobs.json). 있으면 방 위치 = 경기장 중앙, 들어가면 소환 */
        public String boss = "";
    }

    public static final class Data extends SavedData {
        public final Map<String, Def> raids = new LinkedHashMap<>();
        @Override public CompoundTag save(CompoundTag t) {
            CompoundTag all = new CompoundTag();
            for (Def d : raids.values()) {
                CompoundTag r = new CompoundTag();
                if (d.gate != null) r.putLong("gate", d.gate.asLong());
                ListTag rooms = new ListTag();
                for (BlockPos b : d.rooms) rooms.add(LongTag.valueOf(b.asLong()));
                r.put("rooms", rooms);
                r.putInt("minLevel", d.minLevel);
                r.putString("boss", d.boss);
                all.put(d.name, r);
            }
            t.put("raids", all);
            return t;
        }
        static Data load(CompoundTag t) {
            Data d = new Data();
            CompoundTag all = t.getCompound("raids");
            for (String k : all.getAllKeys()) {
                CompoundTag r = all.getCompound(k);
                Def def = new Def(); def.name = k;
                if (r.contains("gate")) def.gate = BlockPos.of(r.getLong("gate"));
                for (var x : r.getList("rooms", 4)) def.rooms.add(BlockPos.of(((LongTag) x).getAsLong()));
                def.minLevel = r.getInt("minLevel");
                def.boss = r.getString("boss");
                d.raids.put(k, def);
            }
            return d;
        }
    }

    public static Data data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "rpgcore_raids");
    }

    public static Def def(MinecraftServer s, String name, boolean create) {
        Data d = data(s);
        Def r = d.raids.get(name);
        if (r == null && create) { r = new Def(); r.name = name; d.raids.put(name, r); d.setDirty(); }
        return r;
    }

    // ───────── 진행 중 ─────────
    private static final class Ready {
        String raid; UUID leader; final Set<UUID> members = new HashSet<>(), ready = new HashSet<>(); long until; long countAt = -1;
    }
    private static final class Run {
        String raid; int room; final Set<UUID> members = new HashSet<>(); long end; ServerBossEvent bar;
        UUID boss; long clearedAt = -1;
    }
    private static final Map<UUID, Ready> READY = new HashMap<>();   // 파티장 → 준비 확인
    private static final List<Run> RUNS = new ArrayList<>();

    private static List<ServerPlayer> group(ServerPlayer p) {
        List<ServerPlayer> out = new ArrayList<>();
        var party = rpg.core.party.Parties.of(p);
        if (party == null) { out.add(p); return out; }
        for (UUID id : party.members) { ServerPlayer m = p.getServer().getPlayerList().getPlayer(id); if (m != null) out.add(m); }
        return out;
    }

    private static boolean leader(ServerPlayer p) {
        var party = rpg.core.party.Parties.of(p);
        return party == null || party.leader.equals(p.getUUID());
    }

    public static Run runOf(UUID id) { for (Run r : RUNS) if (r.members.contains(id)) return r; return null; }
    public static boolean inRaid(ServerPlayer p) { return runOf(p.getUUID()) != null; }

    /** 입구 블록 우클릭 */
    public static void openGate(ServerPlayer p, BlockPos pos) {
        Def def = null;
        for (Def d : data(p.getServer()).raids.values()) if (d.gate != null && d.gate.distSqr(pos) <= 9) { def = d; break; }
        if (def == null) { p.sendSystemMessage(Component.literal("연결된 레이드가 없어요 (OP: /rpg 레이드 입구 <이름>)").withStyle(ChatFormatting.GRAY)); return; }
        sendInfo(p, def);
    }

    public static String setGate(ServerPlayer p, String name) {
        Def def = def(p.getServer(), name, true);
        BlockPos at = p.blockPosition().relative(p.getDirection());
        if (def.gate != null && p.serverLevel().getBlockState(def.gate).is(ModBlocks.RAID_GATE.get()))
            p.serverLevel().setBlockAndUpdate(def.gate, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        p.serverLevel().setBlockAndUpdate(at, ModBlocks.RAID_GATE.get().defaultBlockState());
        def.gate = at.immutable();
        data(p.getServer()).setDirty();
        return "레이드 [" + name + "] 입구를 앞에 놓았어요 (" + at.toShortString() + ")";
    }

    public static String setBoss(MinecraftServer s, String name, String boss) {
        Def def = def(s, name, true);
        if (!boss.equals("없음") && !boss.equals("none") && !rpg.core.combat.Boss.DEFS.containsKey(boss)) return "없는 보스: " + boss;
        def.boss = boss.equals("없음") || boss.equals("none") ? "" : boss;
        data(s).setDirty();
        return def.boss.isEmpty() ? "레이드 [" + name + "] 보스 없음" : "레이드 [" + name + "] 보스 = " + boss + " (방 위치 = 경기장 중앙)";
    }

    public static String addRoom(ServerPlayer p, String name) {
        Def def = def(p.getServer(), name, true);
        def.rooms.add(p.blockPosition().immutable());
        data(p.getServer()).setDirty();
        return "레이드 [" + name + "] 방 " + def.rooms.size() + "번 시작 위치 = 여기";
    }

    public static Component listText(net.minecraft.commands.CommandSourceStack src) {
        var out = Component.literal("[레이드 목록]").withStyle(ChatFormatting.LIGHT_PURPLE);
        CompoundTag clears = src.getEntity() instanceof ServerPlayer p
                ? p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("rpgRaidClears") : new CompoundTag();
        for (Def d : data(src.getServer()).raids.values()) {
            long busy = RUNS.stream().filter(r -> r.raid.equals(d.name)).count();
            out.append(Component.literal("\n- " + d.name + " · Lv." + d.minLevel + " · 방 " + d.rooms.size() + "개 (진행 " + busy + ")"
                    + (d.gate == null ? " · 입구 없음" : "") + " · 내 클리어 " + clears.getInt(d.name) + "회").withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    public static void refresh(ServerPlayer p, String name) {
        Def def = def(p.getServer(), name, false);
        if (def != null) sendInfo(p, def);
    }

    static void sendInfo(ServerPlayer p, Def def) {
        List<String> rows = new ArrayList<>();
        boolean all = true;
        for (ServerPlayer m : group(p)) {
            var d = RpgData.get(m);
            boolean lv = d.level >= def.minLevel, near = def.gate != null && m.level() == p.level() && m.blockPosition().distSqr(def.gate) <= NEAR * NEAR;
            all &= lv && near;
            rows.add(m.getGameProfile().getName() + "|" + d.level + "|" + (lv ? 1 : 0) + "|" + (near ? 1 : 0));
        }
        String busy = busyText(p.getServer(), def);
        if (busy.isEmpty() && rows.size() > MAX_PARTY) busy = "레이드는 최대 " + MAX_PARTY + "명이에요 (지금 " + rows.size() + "명)";
        Net.raidInfo(p, def.name, def.minLevel, rows, all && leader(p), busy);
    }

    private static String busyText(MinecraftServer s, Def def) {
        if (def.rooms.isEmpty()) return "방이 없어요 (OP: /rpg 레이드 방 " + def.name + ")";
        if (freeRoom(def) >= 0) return "";
        long left = Long.MAX_VALUE;
        for (Run r : RUNS) if (r.raid.equals(def.name)) left = Math.min(left, r.end - s.overworld().getGameTime());
        return "다른 파티 진행 중 (남은 시간 " + Math.max(1, left / 1200) + "분)";
    }

    private static int freeRoom(Def def) {
        for (int i = 0; i < def.rooms.size(); i++) {
            final int idx = i;
            if (RUNS.stream().noneMatch(r -> r.raid.equals(def.name) && r.room == idx)) return i;
        }
        return -1;
    }

    /** 입장 요청 (파티장) */
    public static void request(ServerPlayer p, String name) {
        Def def = def(p.getServer(), name, false);
        if (def == null || !leader(p) || inRaid(p)) return;
        if (!busyText(p.getServer(), def).isEmpty() || group(p).size() > MAX_PARTY) { sendInfo(p, def); return; }
        Ready r = new Ready();
        r.raid = name; r.leader = p.getUUID(); r.until = p.level().getGameTime() + READY_TICKS;
        for (ServerPlayer m : group(p)) {
            var d = RpgData.get(m);
            if (d.level < def.minLevel || def.gate == null || m.blockPosition().distSqr(def.gate) > NEAR * NEAR) { sendInfo(p, def); return; }
            r.members.add(m.getUUID());
        }
        r.ready.add(p.getUUID());
        READY.put(p.getUUID(), r);
        for (ServerPlayer m : group(p)) if (m != p) Net.raidReady(m, name, p.getGameProfile().getName());
        if (r.ready.containsAll(r.members)) startCount(p.getServer(), r);
        else p.sendSystemMessage(Component.literal("[레이드] 파티원 준비를 기다려요 (30초)").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** 준비 / 거절 (파티원) */
    public static void answer(ServerPlayer p, boolean ok) {
        for (Ready r : READY.values()) {
            if (!r.members.contains(p.getUUID()) || r.countAt >= 0) continue;
            if (!ok) { cancel(p.getServer(), r, p.getGameProfile().getName() + "님이 거절했어요"); return; }
            r.ready.add(p.getUUID());
            say(p.getServer(), r.members, p.getGameProfile().getName() + " 준비 완료 (" + r.ready.size() + "/" + r.members.size() + ")");
            if (r.ready.containsAll(r.members)) startCount(p.getServer(), r);
            return;
        }
    }

    private static void startCount(MinecraftServer s, Ready r) {
        r.countAt = s.overworld().getGameTime() + COUNT_TICKS;
        say(s, r.members, "모두 준비 완료! 10초 뒤 레이드 [" + r.raid + "] 로 들어가요");
    }

    private static void cancel(MinecraftServer s, Ready r, String why) {
        READY.remove(r.leader);
        say(s, r.members, "입장 취소: " + why);
    }

    private static void say(MinecraftServer s, Set<UUID> ids, String msg) {
        for (UUID id : ids) { ServerPlayer m = s.getPlayerList().getPlayer(id); if (m != null) m.sendSystemMessage(Component.literal("[레이드] " + msg).withStyle(ChatFormatting.LIGHT_PURPLE)); }
    }

    private static void enter(MinecraftServer s, Ready r) {
        READY.remove(r.leader);
        Def def = def(s, r.raid, false);
        int room = def == null ? -1 : freeRoom(def);
        if (room < 0) { say(s, r.members, "빈 방이 없어서 취소됐어요"); return; }
        ServerLevel rl = RpgWorld.rpgLevel(s);
        if (rl == null) return;
        Run run = new Run();
        run.raid = r.raid; run.room = room; run.end = s.overworld().getGameTime() + LIMIT_TICKS;
        run.bar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        BlockPos center = def.rooms.get(room);
        var bdef = def.boss.isEmpty() ? null : rpg.core.combat.Boss.DEFS.get(def.boss);
        // 보스가 있으면 방 위치 = 경기장 중앙, 플레이어는 중앙에서 반경 60% 남쪽에서 시작
        BlockPos at = bdef == null ? center : RpgWorld.ground(rl, center.offset(0, 0, (int) Math.round(bdef.arena() * 0.6)));
        ServerPlayer lead = s.getPlayerList().getPlayer(r.leader);
        java.util.Set<UUID> still = new HashSet<>();
        if (lead != null) for (ServerPlayer g : group(lead)) still.add(g.getUUID());
        for (UUID id : r.members) {
            ServerPlayer m = s.getPlayerList().getPlayer(id);
            if (m == null) continue;
            // 카운트다운 중에 파티를 나갔거나 입구에서 멀어진 사람은 안 데려감
            if (!still.contains(id) || !RpgWorld.in(m) || def.gate == null || m.blockPosition().distSqr(def.gate) > NEAR * NEAR) {
                m.sendSystemMessage(Component.literal("[레이드] 입구에서 멀어져서(또는 파티를 나가서) 같이 들어가지 못했어요").withStyle(ChatFormatting.GRAY));
                continue;
            }
            m.teleportTo(rl, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, m.getYRot(), m.getXRot());
            run.members.add(id);
            run.bar.addPlayer(m);
            setReturn(m, def.gate);
        }
        if (run.members.isEmpty()) { run.bar.removeAllPlayers(); return; }
        if (bdef != null) {   // 보스 소환: 인원수만큼 체력 배수
            var mob = rpg.core.combat.BossPoints.spawnAt(rl, def.boss, center, bdef.arena(), null);
            if (mob != null) {
                int people = run.members.size();
                mob.getPersistentData().putInt("rpgRaidPeople", people);
                mob.getPersistentData().putString("rpgRaid", def.name);
                var md = rpg.core.combat.Combat.mob(mob);
                if (md != null) { md.maxHp *= people; md.hp = md.maxHp; rpg.core.combat.Mobs.sync(mob); }
                run.boss = mob.getUUID();
            }
        }
        RUNS.add(run);
        say(s, run.members, "레이드 시작! 제한 시간 15분" + (bdef != null ? " (보스 체력 ×" + run.members.size() + ")" : ""));
    }

    /** 끝 (실패·나가기·나중엔 클리어) */
    private static void finish(MinecraftServer s, Run run, String msg, boolean cleared) {
        RUNS.remove(run);
        if (run.bar != null) run.bar.removeAllPlayers();
        if (run.boss != null) {   // 보스·기믹 정리
            ServerLevel rl = RpgWorld.rpgLevel(s);
            var e = rl == null ? null : rl.getEntity(run.boss);
            if (e != null && e.isAlive()) e.discard();
        }
        Def def = def(s, run.raid, false);
        for (UUID id : run.members) {
            ServerPlayer m = s.getPlayerList().getPlayer(id);
            if (m == null) continue;
            if (cleared) addClear(m, run.raid);
            toGate(m, def);
            m.sendSystemMessage(Component.literal("[레이드] " + msg).withStyle(cleared ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
    }

    /** 이 보스가 지금 진행 중인 레이드 보스인지 */
    public static boolean bossActive(MinecraftServer s, java.util.UUID bossId) {
        for (Run r : RUNS) if (bossId.equals(r.boss)) return true;
        return false;
    }

    /** 이 보스 레이드의 인원 (접속 중) */
    public static List<ServerPlayer> membersOfBoss(MinecraftServer s, java.util.UUID bossId) {
        List<ServerPlayer> out = new ArrayList<>();
        for (Run r : RUNS) if (bossId.equals(r.boss))
            for (UUID id : r.members) { ServerPlayer m = s.getPlayerList().getPlayer(id); if (m != null) out.add(m); }
        return out;
    }

    /** 레이드 보스가 죽으면 (Boss.onDeath): 30초 동안 드롭 줍고 입구로 */
    public static void bossDied(MinecraftServer s, java.util.UUID bossId) {
        for (Run r : RUNS) if (bossId.equals(r.boss)) {
            r.boss = null;
            r.clearedAt = s.overworld().getGameTime();
            say(s, r.members, "레이드 클리어! 30초 뒤 입구로 돌아가요 (드롭 주우세요)");
            return;
        }
    }

    /** 레이드 보상: 하루 1번 (오전 6시 초기화). 처음이면 기록하고 true */
    public static boolean rewardToday(ServerPlayer p, String raid) {
        String today = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusHours(6).toLocalDate().toString();
        CompoundTag k = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag d = k.getCompound("rpgRaidDay");
        if (today.equals(d.getString(raid))) return false;
        d.putString(raid, today);
        k.put("rpgRaidDay", d);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
        return true;
    }

    public static void leave(ServerPlayer p) {
        Run r = runOf(p.getUUID());
        if (r == null) { p.sendSystemMessage(Component.literal("레이드 중이 아니에요").withStyle(ChatFormatting.GRAY)); return; }
        r.members.remove(p.getUUID());
        if (r.bar != null) r.bar.removePlayer(p);
        toGate(p, def(p.getServer(), r.raid, false));
        p.sendSystemMessage(Component.literal("[레이드] 나왔어요").withStyle(ChatFormatting.GRAY));
        if (r.members.isEmpty()) finish(p.getServer(), r, "", false);
    }

    private static void toGate(ServerPlayer m, Def def) {
        clearReturn(m);
        ServerLevel rl = RpgWorld.rpgLevel(m.getServer());
        if (rl == null || def == null || def.gate == null) return;
        m.teleportTo(rl, def.gate.getX() + 0.5, def.gate.getY(), def.gate.getZ() + 1.5, m.getYRot(), m.getXRot());
    }

    // 접속 끊기면 다시 들어올 때 입구로
    private static void setReturn(ServerPlayer m, BlockPos gate) {
        CompoundTag k = m.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (gate != null) k.putLong("rpgRaidReturn", gate.asLong());
        m.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
    }
    private static void clearReturn(ServerPlayer m) {
        CompoundTag k = m.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        k.remove("rpgRaidReturn");
        m.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
    }
    private static void addClear(ServerPlayer m, String raid) {
        CompoundTag k = m.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag c = k.getCompound("rpgRaidClears");
        c.putInt(raid, c.getInt(raid) + 1);
        k.put("rpgRaidClears", c);
        m.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag k = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!k.contains("rpgRaidReturn") || inRaid(p)) return;
        BlockPos g = BlockPos.of(k.getLong("rpgRaidReturn"));
        clearReturn(p);
        ServerLevel rl = RpgWorld.rpgLevel(p.getServer());
        if (rl != null) p.teleportTo(rl, g.getX() + 0.5, g.getY(), g.getZ() + 1.5, p.getYRot(), p.getXRot());
    }

    /** 쓰러진 뒤 부활 못 하고 죽으면 레이드에서 빠지고 입구로 */
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Run r = runOf(p.getUUID());
        if (r == null) return;
        r.members.remove(p.getUUID());
        if (r.bar != null) r.bar.removePlayer(p);
        toGate(p, def(p.getServer(), r.raid, false));
        p.sendSystemMessage(Component.literal("[레이드] 쓰러져서 입구로 돌아왔어요").withStyle(ChatFormatting.RED));
        if (r.members.isEmpty()) finish(p.getServer(), r, "전멸했어요. 레이드 실패", false);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Run r = runOf(p.getUUID());
        if (r != null) { r.members.remove(p.getUUID()); if (r.bar != null) r.bar.removePlayer(p); }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = e.getServer();
        long now = s.overworld().getGameTime();
        // 준비 확인 시간 초과 · 카운트다운
        for (Ready r : List.copyOf(READY.values())) {
            if (r.countAt >= 0) {
                long left = r.countAt - now;
                if (left > 0 && left % 20 == 0 && left <= 100) say(s, r.members, (left / 20) + "...");
                if (left <= 0) enter(s, r);
            } else if (now > r.until) cancel(s, r, "30초 안에 준비하지 않은 사람이 있어요");
        }
        if (now % 10 != 0) return;
        for (Run run : List.copyOf(RUNS)) {
            // 아직 방(RPG 대륙)에 살아 있는 사람
            int alive = 0;
            for (UUID id : List.copyOf(run.members)) {
                ServerPlayer m = s.getPlayerList().getPlayer(id);
                if (m == null || !RpgWorld.in(m)) { run.members.remove(id); if (m != null && run.bar != null) run.bar.removePlayer(m); continue; }
                if (m.isAlive() && !RpgData.get(m).isDown()) alive++;
            }
            long left = run.end - now;
            if (run.clearedAt >= 0) {   // 클리어 뒤 30초
                long rest = run.clearedAt + 600 - now;
                if (rest <= 0 || run.members.isEmpty()) { finish(s, run, "레이드 클리어!", true); continue; }
                run.bar.setName(Component.literal("레이드 [" + run.raid + "] 클리어! · " + (rest / 20) + "초 뒤 입구로"));
                run.bar.setProgress(Math.max(0f, rest / 600f));
                continue;
            }
            if (run.members.isEmpty()) { finish(s, run, "", false); continue; }
            if (alive == 0) { finish(s, run, "전멸했어요. 레이드 실패", false); continue; }
            if (left <= 0) { finish(s, run, "시간 초과. 레이드 실패", false); continue; }
            run.bar.setName(Component.literal("레이드 [" + run.raid + "] · 남은 시간 " + String.format("%d:%02d", left / 1200, (left / 20) % 60)));
            run.bar.setProgress(Math.max(0f, Math.min(1f, left / (float) LIMIT_TICKS)));
        }
    }
}
