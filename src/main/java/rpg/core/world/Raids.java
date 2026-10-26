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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/**
 * 레이드: 보스마다 하나 (키 = mobs.json 보스 키).
 * 단말기 (레이드 단말기 블록, 아이템에 보스 키) 를 놓으면 그 자리가 입구 (여러 개 가능, 위에 이름 글자).
 * 경기장 = /rpg 레이드 경기장 <보스> 로 중앙 위치 추가 (여러 개 = 파티 동시 입장).
 * 단말기 우클릭 → 입장 창 → 준비 확인 (30초) → 10초 뒤 빈 경기장으로, 보스 소환 (체력 × 인원).
 * 제한 15분 (보스바), 전멸·시간 초과·전원 이탈 = 실패 → 들어온 단말기로.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Raids {
    private Raids() {}

    public static final int LIMIT_TICKS = 15 * 60 * 20, READY_TICKS = 30 * 20, COUNT_TICKS = 10 * 20, NEAR = 16, MAX_PARTY = 4;
    public static final String ITEM_TAG = "rpgRaidBoss", LABEL_TAG = "rpgRaidLabel";

    // ───────── 저장: 보스별 단말기·경기장 ─────────
    public static final class Def {
        public String name;   // 보스 키
        public final List<BlockPos> gates = new ArrayList<>(), rooms = new ArrayList<>();
        public int minLevel;  // 0 = 보스 레벨 - 5
        public int minLevel() { return minLevel > 0 ? minLevel : Math.max(1, rpg.core.combat.Boss.level(name) - 5); }
        public String title() {
            var md = rpg.core.balance.Balance.MOBS.get(name);
            return md == null ? name : md.name();
        }
    }

    public static final class Data extends SavedData {
        public final Map<String, Def> raids = new LinkedHashMap<>();
        @Override public CompoundTag save(CompoundTag t) {
            CompoundTag all = new CompoundTag();
            for (Def d : raids.values()) {
                CompoundTag r = new CompoundTag();
                r.put("gates", longs(d.gates));
                r.put("rooms", longs(d.rooms));
                r.putInt("minLevel", d.minLevel);
                all.put(d.name, r);
            }
            t.put("raids", all);
            return t;
        }
        private static ListTag longs(List<BlockPos> l) {
            ListTag out = new ListTag();
            for (BlockPos b : l) out.add(LongTag.valueOf(b.asLong()));
            return out;
        }
        static Data load(CompoundTag t) {
            Data d = new Data();
            CompoundTag all = t.getCompound("raids");
            for (String k : all.getAllKeys()) {
                CompoundTag r = all.getCompound(k);
                Def def = new Def(); def.name = k;
                for (var x : r.getList("gates", 4)) def.gates.add(BlockPos.of(((LongTag) x).getAsLong()));
                for (var x : r.getList("rooms", 4)) def.rooms.add(BlockPos.of(((LongTag) x).getAsLong()));
                def.minLevel = r.getInt("minLevel");
                d.raids.put(k, def);
            }
            return d;
        }
    }

    /** 예전 이름 레이드 (rpgcore_raids) 는 버림: 보스별 단말기로 바뀜 */
    public static Data data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "rpgcore_raids2");
    }

    public static Def def(MinecraftServer s, String boss, boolean create) {
        Data d = data(s);
        Def r = d.raids.get(boss);
        if (r == null && create) { r = new Def(); r.name = boss; d.raids.put(boss, r); d.setDirty(); }
        return r;
    }

    public static boolean isBoss(String key) { return rpg.core.combat.Boss.DEFS.containsKey(key); }

    // ───────── 진행 중 ─────────
    private static final class Ready {
        String raid; BlockPos gate; UUID leader; final Set<UUID> members = new HashSet<>(), ready = new HashSet<>(); long until; long countAt = -1;
    }
    private static final class Run {
        String raid; BlockPos gate; int room; final Set<UUID> members = new HashSet<>(); long end; ServerBossEvent bar;
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

    // ───────── 단말기 ─────────
    /** 단말기 아이템 (보스 키를 NBT 에) */
    public static ItemStack terminalItem(String boss) {
        ItemStack s = new ItemStack(ModBlocks.RAID_GATE_ITEM.get());
        s.getOrCreateTag().putString(ITEM_TAG, boss);
        Def tmp = new Def(); tmp.name = boss;
        s.setHoverName(Component.literal(tmp.title() + " 레이드 단말기").withStyle(st -> st.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)));
        return s;
    }

    /** 단말기를 놓음 (블록 setPlacedBy) */
    public static void placed(ServerLevel l, BlockPos pos, ItemStack stack, Entity placer) {
        String boss = stack.hasTag() ? stack.getTag().getString(ITEM_TAG) : "";
        if (!RpgWorld.is(l) || boss.isEmpty() || !isBoss(boss)) {
            if (placer instanceof ServerPlayer sp)
                sp.sendSystemMessage(Component.literal("[레이드] 보스가 정해진 단말기만 RPG 대륙에서 쓸 수 있어요 (/rpg 레이드 단말기 <보스>)").withStyle(ChatFormatting.RED));
            return;
        }
        Def def = def(l.getServer(), boss, true);
        def.gates.removeIf(g -> g.equals(pos));
        def.gates.add(pos.immutable());
        data(l.getServer()).setDirty();
        label(l, pos, def);
        if (placer instanceof ServerPlayer sp)
            sp.sendSystemMessage(Component.literal("[레이드] " + def.title() + " 레이드 단말기 설치 (" + pos.toShortString() + ")"
                    + (def.rooms.isEmpty() ? " · 경기장 중앙에서 /rpg 레이드 경기장 " + boss : " · 경기장 " + def.rooms.size() + "개")).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** 단말기가 없어짐 (블록 onRemove) */
    public static void removed(ServerLevel l, BlockPos pos) {
        unlabel(l, pos);
        if (l.getServer() == null) return;
        Data d = data(l.getServer());
        boolean any = false;
        for (Def def : d.raids.values()) any |= def.gates.removeIf(g -> g.equals(pos));
        if (any) d.setDirty();
    }

    /** 단말기 위 글자 (텍스트 디스플레이) */
    private static void label(ServerLevel l, BlockPos pos, Def def) {
        unlabel(l, pos);
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:text_display");
        String json = Component.Serializer.toJson(Component.literal(def.title() + " 레이드\n").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("Lv." + def.minLevel() + " 이상 · 우클릭").withStyle(ChatFormatting.GRAY)));
        t.putString("text", json);
        t.putString("billboard", "center");
        ListTag tags = new ListTag();
        tags.add(net.minecraft.nbt.StringTag.valueOf(LABEL_TAG));
        t.put("Tags", tags);
        Entity e = net.minecraft.world.entity.EntityType.loadEntityRecursive(t, l, x -> {
            x.moveTo(pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5, 0, 0);
            return x;
        });
        if (e != null) l.addFreshEntity(e);
    }

    private static void unlabel(ServerLevel l, BlockPos pos) {
        AABB box = new AABB(pos).inflate(0.5, 2, 0.5);
        for (Entity e : l.getEntities((Entity) null, box, x -> x.getTags().contains(LABEL_TAG))) e.discard();
    }

    /** 단말기 우클릭 */
    public static void openGate(ServerPlayer p, BlockPos pos) {
        Def def = null;
        for (Def d : data(p.getServer()).raids.values()) if (d.gates.contains(pos)) { def = d; break; }
        if (def == null || !isBoss(def.name)) {
            p.sendSystemMessage(Component.literal("연결된 레이드가 없어요 (OP: 부수고 /rpg 레이드 단말기 <보스> 로 받은 단말기를 놓으세요)").withStyle(ChatFormatting.GRAY));
            return;
        }
        sendInfo(p, def, pos);
    }

    /** 경기장 추가: 발밑 = 경기장 중앙 */
    public static String addRoom(ServerPlayer p, String boss) {
        if (!isBoss(boss)) return "없는 보스: " + boss;
        Def def = def(p.getServer(), boss, true);
        def.rooms.add(p.blockPosition().immutable());
        data(p.getServer()).setDirty();
        return def.title() + " 레이드 경기장 " + def.rooms.size() + "번 = 여기 (경기장 중앙, 반지름 " + (int) rpg.core.combat.Boss.DEFS.get(boss).arena() + ")";
    }

    /** 경기장 지우기: 가장 가까운 것 하나 */
    public static String removeRoom(ServerPlayer p, String boss) {
        Def def = def(p.getServer(), boss, false);
        if (def == null || def.rooms.isEmpty()) return "경기장이 없어요: " + boss;
        BlockPos best = null;
        for (BlockPos b : def.rooms) if (best == null || b.distSqr(p.blockPosition()) < best.distSqr(p.blockPosition())) best = b;
        final BlockPos rm = best;
        if (RUNS.stream().anyMatch(r -> r.raid.equals(boss) && def.rooms.indexOf(rm) == r.room)) return "그 경기장은 지금 진행 중이에요";
        def.rooms.remove(rm);
        data(p.getServer()).setDirty();
        return def.title() + " 경기장 지움 (" + rm.toShortString() + ") · 남은 경기장 " + def.rooms.size() + "개";
    }

    public static Component listText(net.minecraft.commands.CommandSourceStack src) {
        var out = Component.literal("[레이드 목록]").withStyle(ChatFormatting.LIGHT_PURPLE);
        CompoundTag clears = src.getEntity() instanceof ServerPlayer p
                ? p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("rpgRaidClears") : new CompoundTag();
        var all = data(src.getServer()).raids.values();
        if (all.isEmpty()) out.append(Component.literal("\n(없음) OP: /rpg 레이드 단말기 <보스> → 놓기 → 경기장 중앙에서 /rpg 레이드 경기장 <보스>").withStyle(ChatFormatting.GRAY));
        for (Def d : all) {
            long busy = RUNS.stream().filter(r -> r.raid.equals(d.name)).count();
            out.append(Component.literal("\n- " + d.title() + " (" + d.name + ") · Lv." + d.minLevel() + " · 단말기 " + d.gates.size() + "개 · 경기장 "
                    + d.rooms.size() + "개 (진행 " + busy + ") · 내 클리어 " + clears.getInt(d.name) + "회").withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static BlockPos nearestGate(Def def, ServerPlayer p) {
        BlockPos best = null;
        for (BlockPos g : def.gates) if (best == null || g.distSqr(p.blockPosition()) < best.distSqr(p.blockPosition())) best = g;
        return best != null && RpgWorld.in(p) && best.distSqr(p.blockPosition()) <= NEAR * NEAR ? best : null;
    }

    public static void refresh(ServerPlayer p, String boss) {
        Def def = def(p.getServer(), boss, false);
        if (def == null) return;
        BlockPos g = nearestGate(def, p);
        if (g != null) sendInfo(p, def, g);
    }

    /** 오늘 이 레이드 보상을 받을 수 있나 (기록은 안 남김) */
    public static boolean rewardAvailable(ServerPlayer p, String raid) {
        CompoundTag d = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("rpgRaidDay");
        return !today().equals(d.getString(raid));
    }

    private static String today() {
        return java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusHours(6).toLocalDate().toString();
    }

    static void sendInfo(ServerPlayer p, Def def, BlockPos gate) {
        List<String> rows = new ArrayList<>();
        boolean all = true;
        int min = def.minLevel();
        for (ServerPlayer m : group(p)) {
            var d = RpgData.get(m);
            boolean lv = d.level >= min, near = m.level() == p.level() && m.blockPosition().distSqr(gate) <= NEAR * NEAR;
            all &= lv && near;
            rows.add(m.getGameProfile().getName() + "|" + d.level + "|" + (lv ? 1 : 0) + "|" + (near ? 1 : 0) + "|" + (rewardAvailable(m, def.name) ? 1 : 0));
        }
        String busy = busyText(p.getServer(), def);
        if (busy.isEmpty() && rows.size() > MAX_PARTY) busy = "레이드는 최대 " + MAX_PARTY + "명이에요 (지금 " + rows.size() + "명)";
        int free = 0;
        for (int i = 0; i < def.rooms.size(); i++) { final int idx = i; if (RUNS.stream().noneMatch(r -> r.raid.equals(def.name) && r.room == idx)) free++; }
        String title = def.title() + "|" + rpg.core.combat.Boss.level(def.name) + "|" + free + "|" + def.rooms.size();
        Net.raidInfo(p, def.name, title, min, rows, all && leader(p), busy);
    }

    private static String busyText(MinecraftServer s, Def def) {
        if (def.rooms.isEmpty()) return "경기장이 없어요 (OP: 경기장 중앙에서 /rpg 레이드 경기장 " + def.name + ")";
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
    public static void request(ServerPlayer p, String boss) {
        Def def = def(p.getServer(), boss, false);
        if (def == null || !leader(p) || inRaid(p)) return;
        BlockPos gate = nearestGate(def, p);
        if (gate == null) return;
        if (!busyText(p.getServer(), def).isEmpty() || group(p).size() > MAX_PARTY) { sendInfo(p, def, gate); return; }
        Ready r = new Ready();
        r.raid = boss; r.gate = gate; r.leader = p.getUUID(); r.until = p.level().getGameTime() + READY_TICKS;
        for (ServerPlayer m : group(p)) {
            var d = RpgData.get(m);
            if (d.level < def.minLevel() || m.level() != p.level() || m.blockPosition().distSqr(gate) > NEAR * NEAR) { sendInfo(p, def, gate); return; }
            r.members.add(m.getUUID());
        }
        r.ready.add(p.getUUID());
        READY.put(p.getUUID(), r);
        for (ServerPlayer m : group(p)) if (m != p) Net.raidReady(m, def.title(), p.getGameProfile().getName());
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
        Def def = def(s, r.raid, false);
        say(s, r.members, "모두 준비 완료! 10초 뒤 " + (def == null ? r.raid : def.title()) + " 레이드로 들어가요");
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
        var bdef = rpg.core.combat.Boss.DEFS.get(r.raid);
        int room = def == null ? -1 : freeRoom(def);
        if (room < 0 || bdef == null) { say(s, r.members, "빈 경기장이 없어서 취소됐어요"); return; }
        ServerLevel rl = RpgWorld.rpgLevel(s);
        if (rl == null) return;
        Run run = new Run();
        run.raid = r.raid; run.gate = r.gate; run.room = room; run.end = s.overworld().getGameTime() + LIMIT_TICKS;
        run.bar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
        BlockPos center = def.rooms.get(room);
        // 플레이어는 중앙에서 반지름 60% 남쪽에서 시작
        BlockPos at = RpgWorld.ground(rl, center.offset(0, 0, (int) Math.round(bdef.arena() * 0.6)));
        ServerPlayer lead = s.getPlayerList().getPlayer(r.leader);
        Set<UUID> still = new HashSet<>();
        if (lead != null) for (ServerPlayer g : group(lead)) still.add(g.getUUID());
        for (UUID id : r.members) {
            ServerPlayer m = s.getPlayerList().getPlayer(id);
            if (m == null) continue;
            // 카운트다운 중에 파티를 나갔거나 단말기에서 멀어진 사람은 안 데려감
            if (!still.contains(id) || !RpgWorld.in(m) || m.blockPosition().distSqr(r.gate) > NEAR * NEAR) {
                m.sendSystemMessage(Component.literal("[레이드] 단말기에서 멀어져서(또는 파티를 나가서) 같이 들어가지 못했어요").withStyle(ChatFormatting.GRAY));
                continue;
            }
            m.teleportTo(rl, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, m.getYRot(), m.getXRot());
            run.members.add(id);
            run.bar.addPlayer(m);
            setReturn(m, r.gate);
        }
        if (run.members.isEmpty()) { run.bar.removeAllPlayers(); return; }
        // 보스 소환: 인원수만큼 체력 배수
        var mob = rpg.core.combat.BossPoints.spawnAt(rl, r.raid, center, bdef.arena(), null);
        if (mob != null) {
            int people = run.members.size();
            mob.getPersistentData().putInt("rpgRaidPeople", people);
            mob.getPersistentData().putString("rpgRaid", r.raid);
            var md = rpg.core.combat.Combat.mob(mob);
            if (md != null) { md.maxHp *= people; md.hp = md.maxHp; rpg.core.combat.Mobs.sync(mob); }
            run.boss = mob.getUUID();
        }
        RUNS.add(run);
        say(s, run.members, def.title() + " 레이드 시작! 제한 시간 15분 (보스 체력 ×" + run.members.size() + ")");
    }

    /** 끝 (실패·나가기·클리어) */
    private static void finish(MinecraftServer s, Run run, String msg, boolean cleared) {
        RUNS.remove(run);
        if (run.bar != null) run.bar.removeAllPlayers();
        if (run.boss != null) {   // 보스·기믹 정리
            ServerLevel rl = RpgWorld.rpgLevel(s);
            var e = rl == null ? null : rl.getEntity(run.boss);
            if (e != null && e.isAlive()) e.discard();
        }
        for (UUID id : run.members) {
            ServerPlayer m = s.getPlayerList().getPlayer(id);
            if (m == null) continue;
            if (cleared) addClear(m, run.raid);
            toGate(m, run.gate);
            if (!msg.isEmpty()) m.sendSystemMessage(Component.literal("[레이드] " + msg).withStyle(cleared ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
    }

    /** 이 보스가 지금 진행 중인 레이드 보스인지 */
    public static boolean bossActive(MinecraftServer s, UUID bossId) {
        for (Run r : RUNS) if (bossId.equals(r.boss)) return true;
        return false;
    }

    /** 이 보스 레이드의 인원 (접속 중) */
    public static List<ServerPlayer> membersOfBoss(MinecraftServer s, UUID bossId) {
        List<ServerPlayer> out = new ArrayList<>();
        for (Run r : RUNS) if (bossId.equals(r.boss))
            for (UUID id : r.members) { ServerPlayer m = s.getPlayerList().getPlayer(id); if (m != null) out.add(m); }
        return out;
    }

    /** 레이드 보스가 죽으면 (Boss.onDeath): 30초 동안 드롭 줍고 단말기로 */
    public static void bossDied(MinecraftServer s, UUID bossId) {
        for (Run r : RUNS) if (bossId.equals(r.boss)) {
            r.boss = null;
            r.clearedAt = s.overworld().getGameTime();
            say(s, r.members, "레이드 클리어! 30초 뒤 단말기로 돌아가요 (드롭 주우세요)");
            Component btn = Component.literal("[레이드] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("[지금 나가기]").withStyle(st -> st.withColor(ChatFormatting.GREEN).withUnderlined(true)
                            .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/rpg 레이드 나가기"))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("다 주웠으면 바로 단말기로")))))
                    .append(Component.literal("  또는 /rpg 레이드 나가기").withStyle(ChatFormatting.GRAY));
            for (UUID id : r.members) { ServerPlayer m = s.getPlayerList().getPlayer(id); if (m != null) m.sendSystemMessage(btn); }
            return;
        }
    }

    /** 레이드 보상: 하루 1번 (오전 6시 초기화). 처음이면 기록하고 true */
    public static boolean rewardToday(ServerPlayer p, String raid) {
        String today = today();
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
        boolean cleared = r.clearedAt >= 0;
        if (cleared) addClear(p, r.raid);   // 클리어 뒤 카운트 중에 먼저 나가도 클리어 기록
        toGate(p, r.gate);
        p.sendSystemMessage(Component.literal(cleared ? "[레이드] 클리어! 단말기로 나왔어요" : "[레이드] 나왔어요").withStyle(cleared ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        if (r.members.isEmpty()) finish(p.getServer(), r, "", false);
    }

    private static void toGate(ServerPlayer m, BlockPos gate) {
        clearReturn(m);
        ServerLevel rl = RpgWorld.rpgLevel(m.getServer());
        if (rl == null || gate == null) return;
        m.teleportTo(rl, gate.getX() + 0.5, gate.getY(), gate.getZ() + 1.5, m.getYRot(), m.getXRot());
    }

    // 접속 끊기면 다시 들어올 때 단말기로
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
        toGate(p, g);
    }

    /** 쓰러진 뒤 부활 못 하고 죽으면 레이드에서 빠지고 단말기로 */
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Run r = runOf(p.getUUID());
        if (r == null) return;
        r.members.remove(p.getUUID());
        if (r.bar != null) r.bar.removePlayer(p);
        toGate(p, r.gate);
        p.sendSystemMessage(Component.literal("[레이드] 쓰러져서 단말기로 돌아왔어요").withStyle(ChatFormatting.RED));
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
            String title = def(s, run.raid, false) == null ? run.raid : def(s, run.raid, false).title();
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
                run.bar.setName(Component.literal(title + " 레이드 클리어! · " + (rest / 20) + "초 뒤 단말기로"));
                run.bar.setProgress(Math.max(0f, rest / 600f));
                continue;
            }
            if (run.members.isEmpty()) { finish(s, run, "", false); continue; }
            if (alive == 0) { finish(s, run, "전멸했어요. 레이드 실패", false); continue; }
            if (left <= 0) { finish(s, run, "시간 초과. 레이드 실패", false); continue; }
            run.bar.setName(Component.literal(title + " 레이드 · 남은 시간 " + String.format("%d:%02d", left / 1200, (left / 20) % 60)));
            run.bar.setProgress(Math.max(0f, Math.min(1f, left / (float) LIMIT_TICKS)));
        }
    }
}
