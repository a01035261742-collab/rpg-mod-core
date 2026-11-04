package rpg.core.town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/**
 * 마을 간 이동 (0.56.0): 이동의 돌(블록, 골드) · 마을 이동 주문서(아이템, 3초 시전).
 * 마을 = /rpg town add 로 등록한 곳. 그 자리에서 townDiscover 칸 안에 들어가면 발견 (캐릭터마다, PERSISTED rpgTowns).
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Travel {
    private Travel() {}

    public static final String KEY = "rpgTowns", LABEL_TAG = "rpgWaystoneLabel";
    public static final int CAST_TICKS = 60;
    public static final int STONE = 0, SCROLL = 1;

    private static final Map<UUID, BlockPos> OPENED = new HashMap<>();   // 마지막으로 연 이동의 돌
    private record Cast(String town, Vec3 from, long end, double hp) {}
    private static final Map<UUID, Cast> CASTS = new HashMap<>();

    // ───────── 발견 기록 ─────────
    private static CompoundTag kept(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static String key(ServerLevel l, String town) {
        return l.dimension().location() + "|" + town;
    }

    public static Set<String> known(ServerPlayer p) {
        Set<String> out = new LinkedHashSet<>();
        ListTag list = kept(p).getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    private static void learn(ServerPlayer p, String k) {
        CompoundTag t = kept(p);
        ListTag list = t.getList(KEY, Tag.TAG_STRING);
        list.add(StringTag.valueOf(k));
        t.put(KEY, list);
    }

    /** 1초마다: 마을 근처면 발견 */
    @SubscribeEvent
    public static void discover(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 20 != 7) return;
        if (!rpg.core.world.RpgWorld.in(p)) return;
        Towns t = Towns.get(p.serverLevel());
        if (t.towns.isEmpty()) return;
        Set<String> have = null;
        double r2 = Balance.townDiscover * Balance.townDiscover;
        for (var en : t.towns.entrySet()) {
            if (en.getValue().distSqr(p.blockPosition()) > r2) continue;
            if (have == null) have = known(p);
            String k = key(p.serverLevel(), en.getKey());
            if (have.contains(k)) continue;
            learn(p, k);
            have.add(k);
            if (have.size() > 1) {   // 첫 마을(시작 마을)은 조용히
                p.sendSystemMessage(Component.literal("[마을] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("새 마을 발견: " + en.getKey() + " — 이동의 돌이나 마을 이동 주문서로 언제든 올 수 있어요").withStyle(ChatFormatting.YELLOW)));
                p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
        }
    }

    // ───────── 창 열기 ─────────
    public static long cost(ServerPlayer p) {
        return RpgData.get(p).job == 0 ? Balance.travelBeginnerCost : Balance.travelCost;
    }

    /** 이동의 돌 우클릭 */
    public static void openStone(ServerPlayer p, BlockPos pos) {
        OPENED.put(p.getUUID(), pos.immutable());
        send(p, STONE);
    }

    /** 마을 이동 주문서 우클릭 */
    public static void openScroll(ServerPlayer p) {
        String why = blocked(p);
        if (why != null) { msg(p, why, ChatFormatting.RED); return; }
        send(p, SCROLL);
    }

    private static String blocked(ServerPlayer p) {
        if (rpg.core.world.Raids.inRaid(p)) return "레이드 중에는 마을로 이동할 수 없어요";
        if (rpg.core.combat.Boss.inArena(p)) return "보스 경기장에서는 마을로 이동할 수 없어요";
        if (rpg.core.job.Trials.inRun(p)) return "교관의 시련 중에는 마을로 이동할 수 없어요";
        return null;
    }

    /** 행: 이름|발견1/0|여기1/0 */
    private static void send(ServerPlayer p, int mode) {
        Towns t = Towns.get(p.serverLevel());
        if (t.towns.isEmpty()) { msg(p, "이 세계에 등록된 마을이 없어요", ChatFormatting.RED); return; }
        Set<String> have = known(p);
        double r2 = Balance.townDiscover * Balance.townDiscover;
        List<String> rows = new ArrayList<>();
        for (var en : t.towns.entrySet()) {
            boolean k = have.contains(key(p.serverLevel(), en.getKey()));
            boolean here = en.getValue().distSqr(p.blockPosition()) <= r2;
            rows.add(en.getKey() + "|" + (k ? 1 : 0) + "|" + (here ? 1 : 0));
        }
        Net.travelInfo(p, mode, rows, mode == STONE ? cost(p) : 0, RpgData.get(p).gold);
    }

    // ───────── 이동 ─────────
    public static void go(ServerPlayer p, int mode, String town) {
        ServerLevel l = p.serverLevel();
        BlockPos dest = Towns.get(l).towns.get(town);
        if (dest == null) { msg(p, "없는 마을이에요", ChatFormatting.RED); return; }
        if (!known(p).contains(key(l, town))) { msg(p, "아직 가 보지 않은 마을이에요", ChatFormatting.RED); return; }
        if (dest.distSqr(p.blockPosition()) <= Balance.townDiscover * Balance.townDiscover) { msg(p, "이미 " + town + "에 있어요", ChatFormatting.GRAY); return; }
        if (mode == STONE) {
            BlockPos s = OPENED.get(p.getUUID());
            if (s == null || s.distSqr(p.blockPosition()) > 36 || !l.getBlockState(s).is(rpg.core.world.ModBlocks.WAYSTONE.get())) {
                msg(p, "이동의 돌 가까이에서 쓸 수 있어요", ChatFormatting.RED);
                return;
            }
            PlayerData d = RpgData.get(p);
            long c = cost(p);
            if (d.gold < c) { msg(p, "골드가 부족해요 (" + String.format("%,d", c) + " G 필요)", ChatFormatting.RED); return; }
            d.gold -= c;
            RpgData.sync(p);
            arrive(p, town, dest);
            return;
        }
        String why = blocked(p);
        if (why != null) { msg(p, why, ChatFormatting.RED); return; }
        if (scroll(p).isEmpty()) { msg(p, "마을 이동 주문서가 없어요", ChatFormatting.RED); return; }
        if (CASTS.containsKey(p.getUUID())) return;
        CASTS.put(p.getUUID(), new Cast(town, p.position(), l.getGameTime() + CAST_TICKS, RpgData.get(p).hp));
        msg(p, town + "(으)로 이동 중… (3초, 움직이면 취소)", ChatFormatting.AQUA);
        l.playSound(null, p.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.3f);
    }

    private static ItemStack scroll(ServerPlayer p) {
        for (var h : net.minecraft.world.InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.getItem() instanceof rpg.core.item.TownScroll) return s;
        }
        for (ItemStack s : p.getInventory().items) if (s.getItem() instanceof rpg.core.item.TownScroll) return s;
        return ItemStack.EMPTY;
    }

    private static void arrive(ServerPlayer p, String town, BlockPos dest) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.4);
        p.teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        p.fallDistance = 0;
        l.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.1f);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        msg(p, town + "(으)로 이동했어요", ChatFormatting.GREEN);
    }

    /** 주문서 시전 (귀환 주문서와 같음: 움직이거나 맞으면 취소) */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CASTS.isEmpty()) return;
        var server = e.getServer();
        CASTS.entrySet().removeIf(en -> {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            Cast c = en.getValue();
            if (p == null || !p.isAlive()) return true;
            if (p.position().distanceToSqr(c.from()) > 0.6 * 0.6) { msg(p, "움직여서 이동이 취소됐어요", ChatFormatting.GRAY); return true; }
            if (RpgData.get(p).hp < c.hp() - 0.01) { msg(p, "공격받아서 이동이 취소됐어요", ChatFormatting.GRAY); return true; }
            p.serverLevel().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.3);
            if (p.level().getGameTime() < c.end()) return false;
            String why = blocked(p);
            if (why != null) { msg(p, why, ChatFormatting.RED); return true; }
            BlockPos dest = Towns.get(p.serverLevel()).towns.get(c.town());
            ItemStack s = scroll(p);
            if (dest == null) return true;
            if (s.isEmpty()) { msg(p, "마을 이동 주문서가 없어요", ChatFormatting.RED); return true; }
            if (!p.getAbilities().instabuild) s.shrink(1);
            arrive(p, c.town(), dest);
            return true;
        });
    }

    // ───────── 이동의 돌 블록 ─────────
    /** 놓으면 위에 글자 (가장 가까운 마을 이름) */
    public static void placed(ServerLevel l, BlockPos pos, Entity placer) {
        var near = Towns.get(l).nearest(pos);
        String town = near != null && near.getValue().distSqr(pos) <= 96 * 96 ? near.getKey() : "";
        label(l, pos, town);
        if (placer instanceof ServerPlayer sp)
            sp.sendSystemMessage(Component.literal("[마을] 이동의 돌 설치 (" + pos.toShortString() + ")" + (town.isEmpty() ? " · 근처에 등록된 마을이 없어요 (/rpg town add <이름>)" : " · " + town))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static void removed(ServerLevel l, BlockPos pos) {
        unlabel(l, pos);
    }

    private static void label(ServerLevel l, BlockPos pos, String town) {
        unlabel(l, pos);
        CompoundTag t = new CompoundTag();
        t.putString("id", "minecraft:text_display");
        Component txt = Component.literal("이동의 돌" + (town.isEmpty() ? "" : " · " + town) + "\n").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("우클릭: 마을 이동").withStyle(ChatFormatting.GRAY));
        t.putString("text", Component.Serializer.toJson(txt));
        t.putString("billboard", "center");
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(LABEL_TAG));
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

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }
}
