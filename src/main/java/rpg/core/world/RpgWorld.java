package rpg.core.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.RpgData;

/**
 * 월드 2개: 야생 (오버월드·네더·엔드) / RPG 대륙 (rpgcore:rpg_world, 평지).
 * RPG 시스템은 RPG 대륙에서만. 월드 묶음이 바뀌면 인벤·갑옷·엔더상자·경험치·체력·배고픔·효과를 바꿔 낌.
 * 플레이어 저장: PERSISTED rpgWorlds {loaded: "wild"|"rpg", wild:{...}, rpg:{...}, chosen}
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class RpgWorld {
    private RpgWorld() {}

    /** RPG 대륙 평지 바로 위 높이 (dimension/rpg_world.json 층과 맞춤) */
    static final int FLAT_TOP = -10;   // 기반암 1 · 돌 50 · 흙 2 · 풀 1 → 위 Y=-10
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, RpgCore.id("rpg_world"));
    static final String WILD = "wild", RPG = "rpg";

    // ───────── 판정 ─────────
    public static boolean is(Level l) { return l != null && l.dimension() == KEY; }
    public static boolean in(Entity e) { return e != null && is(e.level()); }
    static String group(Level l) { return is(l) ? RPG : WILD; }

    // ───────── 저장 데이터 (월드 전체: RPG 스폰, 포탈 설치 여부) ─────────
    public static final class Data extends SavedData {
        public BlockPos rpgSpawn = new BlockPos(0, FLAT_TOP, 0);
        public boolean portalsBuilt;
        @Override public CompoundTag save(CompoundTag t) {
            t.putLong("rpgSpawn", rpgSpawn.asLong()); t.putBoolean("portalsBuilt", portalsBuilt); return t;
        }
        static Data load(CompoundTag t) {
            Data d = new Data();
            if (t.contains("rpgSpawn")) d.rpgSpawn = BlockPos.of(t.getLong("rpgSpawn"));
            d.portalsBuilt = t.getBoolean("portalsBuilt");
            return d;
        }
    }

    public static Data data(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, "rpgcore_worlds");
    }

    public static ServerLevel rpgLevel(MinecraftServer s) { return s.getLevel(KEY); }

    // ───────── 플레이어별 묶음 데이터 ─────────
    private static CompoundTag keep(ServerPlayer p) { return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG); }
    private static CompoundTag worlds(ServerPlayer p) { return keep(p).getCompound("rpgWorlds"); }
    private static void putWorlds(ServerPlayer p, CompoundTag w) {
        CompoundTag k = keep(p); k.put("rpgWorlds", w); p.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
    }

    /** 지금 들고 있는 것을 묶음 g 에 저장 */
    private static CompoundTag snapshot(ServerPlayer p, String g) {
        CompoundTag t = new CompoundTag();
        t.put("inv", p.getInventory().save(new ListTag()));
        t.put("ender", p.getEnderChestInventory().createTag());
        t.putInt("xpLv", p.experienceLevel); t.putFloat("xpP", p.experienceProgress); t.putInt("xpT", p.totalExperience);
        t.putFloat("hp", p.getHealth());
        t.putInt("food", p.getFoodData().getFoodLevel()); t.putFloat("sat", p.getFoodData().getSaturationLevel());
        ListTag fx = new ListTag();
        for (MobEffectInstance m : p.getActiveEffects()) fx.add(m.save(new CompoundTag()));
        t.put("fx", fx);
        if (WILD.equals(g) && !is(p.level())) { t.putString("dim", p.level().dimension().location().toString()); t.putDouble("x", p.getX()); t.putDouble("y", p.getY()); t.putDouble("z", p.getZ()); }
        return t;
    }

    /** 묶음 g 에 저장된 걸 꺼내 낌 (없으면 빈손) */
    private static void restore(ServerPlayer p, CompoundTag t) {
        p.getInventory().clearContent();
        p.getEnderChestInventory().clearContent();
        p.removeAllEffects();
        if (t.isEmpty()) {
            p.experienceLevel = 0; p.experienceProgress = 0; p.totalExperience = 0;
            p.getFoodData().setFoodLevel(20); p.getFoodData().setSaturation(5f);
            p.setHealth(p.getMaxHealth());
        } else {
            p.getInventory().load(t.getList("inv", 10));
            p.getEnderChestInventory().fromTag(t.getList("ender", 10));
            p.experienceLevel = t.getInt("xpLv"); p.experienceProgress = t.getFloat("xpP"); p.totalExperience = t.getInt("xpT");
            p.getFoodData().setFoodLevel(t.getInt("food")); p.getFoodData().setSaturation(t.getFloat("sat"));
            p.setHealth(Math.max(1f, Math.min(p.getMaxHealth(), t.getFloat("hp"))));
            for (var x : t.getList("fx", 10)) { MobEffectInstance m = MobEffectInstance.load((CompoundTag) x); if (m != null) p.addEffect(m); }
        }
        p.inventoryMenu.broadcastChanges();
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(p.experienceProgress, p.totalExperience, p.experienceLevel));
    }

    /** 지금 있는 월드 묶음이랑 들고 있는 묶음이 다르면 바꿔 낌 */
    static void sync(ServerPlayer p) {
        CompoundTag w = worlds(p);
        String loaded = w.getString("loaded"), now = group(p.level());
        if (loaded.isEmpty()) { w.putString("loaded", now); putWorlds(p, w); return; }
        if (loaded.equals(now)) return;
        // 들고 있는 것(마우스·제작칸·열린 창)을 먼저 인벤으로 → 다른 월드로 몰래 안 넘어가게
        if (p.containerMenu != p.inventoryMenu) p.doCloseContainer();
        p.inventoryMenu.removed(p);
        w.put(loaded, snapshot(p, loaded));
        restore(p, w.getCompound(now));
        w.putString("loaded", now);
        putWorlds(p, w);
        RpgData.get(p).recalc();
        RpgData.sync(p);
    }

    // ───────── 이동 ─────────
    /** 반대 월드로 (RPG → 야생: 마지막 야생 위치, 야생 → RPG: RPG 스폰) */
    public static void travel(ServerPlayer p) {
        MinecraftServer s = p.getServer();
        if (s == null) return;
        if (is(p.level())) {
            CompoundTag wild = worlds(p).getCompound("wildPos");
            if (!wild.contains("dim")) wild = worlds(p).getCompound(WILD);
            ServerLevel to = s.overworld();
            Vec3 pos = null;
            if (wild.contains("dim") && !wild.getString("dim").equals(KEY.location().toString())) {   // 예전 버그로 RPG 위치가 저장된 건 무시
                var lvl = s.getLevel(ResourceKey.create(Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(wild.getString("dim"))));
                if (lvl != null) { to = lvl; pos = Vec3.atBottomCenterOf(ground(lvl, BlockPos.containing(wild.getDouble("x"), wild.getDouble("y"), wild.getDouble("z")))); }
            }
            if (pos == null) {
                BlockPos sp = p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition() : s.overworld().getSharedSpawnPos();
                pos = Vec3.atBottomCenterOf(surface(to, sp.getX(), sp.getZ()));
            }
            p.teleportTo(to, pos.x, pos.y, pos.z, p.getYRot(), p.getXRot());
            if (inPortal(p)) ARRIVED.add(p.getUUID());   // 문 위치로 돌아왔으면 나갈 때까지 대기
        } else {
            ServerLevel to = rpgLevel(s);
            if (to == null) { p.sendSystemMessage(Component.literal("RPG 대륙을 찾을 수 없어요").withStyle(ChatFormatting.RED)); return; }
            // 떠나기 전에 야생 위치 기억 (다음에 야생으로 돌아올 자리)
            CompoundTag w = worlds(p), wp = new CompoundTag();
            wp.putString("dim", p.level().dimension().location().toString());
            wp.putDouble("x", p.getX()); wp.putDouble("y", p.getY()); wp.putDouble("z", p.getZ());
            w.put("wildPos", wp);
            putWorlds(p, w);
            BlockPos sp = rpgArrive(to, data(s).rpgSpawn);
            p.teleportTo(to, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYRot(), p.getXRot());
        }
        p.fallDistance = 0;
        sync(p);
        p.sendSystemMessage(Component.literal((is(p.level()) ? "RPG 대륙으로 왔어요" : "야생으로 왔어요")
                + (p.hasPermissions(2) ? String.format(" (%d, %d, %d)", p.getBlockX(), p.getBlockY(), p.getBlockZ()) : "")).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // 명령어 이동: 3초 가만히 (움직이거나 맞으면 취소)
    private record Channel(Vec3 at, long until, float hp) {}
    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();
    private static final Map<UUID, Long> PORTAL_CD = new HashMap<>();

    public static void command(ServerPlayer p) {
        if (Raids.inRaid(p)) { p.sendSystemMessage(Component.literal("레이드 중에는 이동할 수 없어요 (/rpg 레이드 나가기)").withStyle(ChatFormatting.RED)); return; }
        if (rpg.core.combat.Boss.inArena(p)) { p.sendSystemMessage(Component.literal("보스방에서는 이동할 수 없어요").withStyle(ChatFormatting.RED)); return; }
        long now = p.level().getGameTime();
        var d = RpgData.get(p);
        if (now - d.lastAttack < 200 || p.getLastHurtByMobTimestamp() > 0 && p.tickCount - p.getLastHurtByMobTimestamp() < 200) {
            p.sendSystemMessage(Component.literal("전투 중에는 이동할 수 없어요 (10초 뒤에)").withStyle(ChatFormatting.RED)); return;
        }
        CHANNELS.put(p.getUUID(), new Channel(p.position(), now + 60, p.getHealth()));
        p.displayClientMessage(Component.literal("3초 뒤 " + (is(p.level()) ? "야생" : "RPG 대륙") + "으로 이동해요 (움직이면 취소)").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** 포탈 막 넘어와서 아직 문 안에 서 있는 사람 (문 밖으로 나갈 때까지 다시 안 넘어감) */
    private static final java.util.Set<UUID> ARRIVED = new java.util.HashSet<>();

    public static void portal(ServerPlayer p) {
        long now = p.level().getGameTime();
        if (ARRIVED.contains(p.getUUID()) || now < PORTAL_CD.getOrDefault(p.getUUID(), 0L)) return;
        PORTAL_CD.put(p.getUUID(), now + 40);
        travel(p);
        ARRIVED.add(p.getUUID());
        PORTAL_CD.put(p.getUUID(), p.level().getGameTime() + 40);
    }

    static boolean inPortal(ServerPlayer p) {
        var b = ModBlocks.WORLD_PORTAL.get();
        return p.level().getBlockState(p.blockPosition()).is(b) || p.level().getBlockState(BlockPos.containing(p.getEyePosition())).is(b);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        // 차원 문: 서 있으면 이동 (넘어온 직후엔 문 밖으로 나갈 때까지 대기)
        if (p.tickCount % 2 == 0) {
            boolean inside = inPortal(p);
            if (!inside) ARRIVED.remove(p.getUUID());
            else if (!ARRIVED.contains(p.getUUID())) portal(p);
        }
        // Epic Fight: 야생에서도 그대로 씀 (0.43.2 롤백). 예전에 걸어둔 전투 모드 잠금만 처음 몇 초 동안 풀어줌
        if (p.tickCount < 200 && p.tickCount % 20 == 0) rpg.core.compat.EpicFightCompat.forceMode(p, true);
        if (CHANNELS.isEmpty()) return;
        Channel c = CHANNELS.get(p.getUUID());
        if (c == null) return;
        if (p.position().distanceToSqr(c.at()) > 0.25 || p.getHealth() < c.hp() || !p.isAlive()) {
            CHANNELS.remove(p.getUUID());
            p.displayClientMessage(Component.literal("이동 취소").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.tickCount % 4 == 0)
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 10, 0.4, 0.6, 0.4, 0.3);
        if (p.level().getGameTime() >= c.until()) { CHANNELS.remove(p.getUUID()); travel(p); }
    }

    // ───────── 이벤트 ─────────
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag w = worlds(p);
        if (!w.contains("loaded")) {
            // 업데이트 전부터 하던 사람: 지금 가진 건 RPG 묶음으로, 야생은 빈손 (새로 온 사람은 그대로 야생에서 시작)
            var rd = RpgData.get(p);
            boolean veteran = rd.level > 1 || rd.job > 0 || rd.gold > 0;
            if (veteran && !is(p.level())) {
                w.put(RPG, snapshot(p, RPG));
                restore(p, new CompoundTag());
            }
            w.putString("loaded", group(p.level()));
            putWorlds(p, w);
        } else sync(p);
        if (!w.getBoolean("chosen")) {   // 처음 접속: 고르기 창 없이 야생에서 시작 (0.43.5)
            w = worlds(p);
            w.putBoolean("chosen", true);
            putWorlds(p, w);
            p.sendSystemMessage(Component.literal("[RPG] 야생에서 시작해요. RPG 대륙은 /rpg 이동 (또는 스폰 옆 차원 문)으로 갈 수 있어요").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** 첫 접속 고르기 결과 */
    public static void choose(ServerPlayer p, boolean rpg) {
        CompoundTag w = worlds(p);
        if (w.getBoolean("chosen")) return;
        w.putBoolean("chosen", true);
        putWorlds(p, w);
        if (rpg != is(p.level())) travel(p);
    }

    @SubscribeEvent
    public static void changed(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sync(p);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.isEndConquered()) return;
        // RPG 묶음을 들고 죽었으면 RPG 스폰에서 살아남
        if (RPG.equals(worlds(p).getString("loaded")) && !is(p.level()) && p.getServer() != null) {
            ServerLevel to = rpgLevel(p.getServer());
            if (to != null) { BlockPos sp = rpgArrive(to, data(p.getServer()).rpgSpawn); p.teleportTo(to, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYRot(), p.getXRot()); }
        }
        sync(p);
    }

    /** RPG 대륙: 바닐라 몹 자연 스폰 없음 (RPG 몹은 모드가 직접 소환) */
    @SubscribeEvent
    public static void spawn(MobSpawnEvent.FinalizeSpawn e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !is(l)) return;
        MobSpawnType t = e.getSpawnType();
        if (t == MobSpawnType.NATURAL || t == MobSpawnType.CHUNK_GENERATION || t == MobSpawnType.PATROL || t == MobSpawnType.STRUCTURE)
            e.setSpawnCancelled(true);
    }

    /** 서버 켜질 때 한 번: 양쪽 스폰에 차원 문 + 공유 창고 설치 */
    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        MinecraftServer s = e.getServer();
        Data d = data(s);
        ServerLevel rpg = rpgLevel(s);
        if (rpg == null) return;
        // 예전 높이로 생긴 경우: 스폰·문을 지금 평지 높이로 다시
        boolean buried = d.portalsBuilt && d.rpgSpawn.getY() != FLAT_TOP && d.rpgSpawn.getX() == 0 && d.rpgSpawn.getZ() == 0;
        if (buried) {
            d.rpgSpawn = new BlockPos(0, FLAT_TOP, 0);
            BlockPos b = d.rpgSpawn;
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) rpg.setBlockAndUpdate(b.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
            placeGate(rpg, b.offset(0, 0, -3));
            d.setDirty();
        }
        // 한 번 설치한 뒤에는 다시 안 놓음 (스폰을 옮기거나 문을 옮겨도 맵을 덮어쓰지 않게). 문이 필요하면 OP: /rpg 포탈
        if (d.portalsBuilt) return;
        // RPG 대륙: 스폰 (0, 평지 위, 0) 에 9×9 돌바닥 + 문. 평지 높이는 고정 (기반암 -64 · 흙 2 · 풀 1 → 위 -60)
        BlockPos base = new BlockPos(0, FLAT_TOP, 0);
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) rpg.setBlockAndUpdate(base.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState());
        placeGate(rpg, base.offset(0, 0, -3));
        d.rpgSpawn = base;
        // 야생: 월드 스폰 옆
        ServerLevel ow = s.overworld();
        BlockPos ws = ow.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ow.getSharedSpawnPos().offset(3, 0, 0));
        placeGate(ow, ws);
        d.portalsBuilt = true;
        d.setDirty();
    }

    /** 그 x,z 의 땅 위 (청크를 먼저 만들어야 함: 안 만들어진 청크는 높이가 맨 아래(-64)로 나와서 땅속에 낌) */
    public static BlockPos surface(ServerLevel l, int x, int z) {
        l.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** RPG 스폰 도착 자리: 저장된 스폰 기준으로 바닥 찾고, 그래도 발밑이 비면 땅 위 */
    public static BlockPos rpgArrive(ServerLevel l, BlockPos spawn) {
        BlockPos b = ground(l, spawn);
        if (l.getBlockState(b.below()).isAir()) b = surface(l, spawn.getX(), spawn.getZ());
        return b;
    }

    /** 서 있을 수 있는 높이: 발밑이 비어 있으면 아래로, 막혀 있으면 위로 (공중·땅속 스폰 막기) */
    public static BlockPos ground(ServerLevel l, BlockPos at) {
        l.getChunk(at.getX() >> 4, at.getZ() >> 4);
        BlockPos b = at;
        if (l.getBlockState(b).isAir() && l.getBlockState(b.below()).isAir()) {
            for (int i = 0; i < 128 && b.getY() > l.getMinBuildHeight() + 1; i++) { b = b.below(); if (!l.getBlockState(b.below()).isAir()) return b; }
            return at;
        }
        for (int i = 0; i < 128 && (!l.getBlockState(b).isAir() || !l.getBlockState(b.above()).isAir()); i++) b = b.above();
        return b;
    }

    /** 문 2칸 (세로) + 왼쪽에 공유 창고 */
    static void placeGate(ServerLevel l, BlockPos at) {
        l.setBlockAndUpdate(at, ModBlocks.WORLD_PORTAL.get().defaultBlockState());
        l.setBlockAndUpdate(at.above(), ModBlocks.WORLD_PORTAL.get().defaultBlockState());
        l.setBlockAndUpdate(at.west(2), ModBlocks.SHARED_STASH.get().defaultBlockState());
    }

    /** OP: 지금 자리를 RPG 스폰으로 */
    public static void setSpawn(ServerPlayer p) {
        Data d = data(p.getServer());
        d.rpgSpawn = p.blockPosition();
        d.setDirty();
    }
}
