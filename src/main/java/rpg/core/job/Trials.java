package rpg.core.job;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.quest.QuestPlaces;

/**
 * 교관의 시련 (2차 전직 시험): 시련장에 "시련의 허수아비"(mobs.json trial_golem)가 나오고 3분 안에 쓰러뜨리면 통과.
 * 시련 받은 파티원(24칸 안)이 같이 들어감. 죽거나(다운) 시련장에서 24칸 넘게 벗어나면 그 사람은 실패, 다 실패하거나 시간이 끝나면 몹이 사라짐.
 * 시련장은 한 번에 한 무리만. 시련장 위치: /rpg trial point add
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Trials {
    private Trials() {}

    public static final String MOB_KEY = "trial_golem", TAG = "rpgTrial";
    public static final int MOB_LEVEL = 30, LIMIT_TICKS = 3 * 60 * 20;
    public static final double LEAVE = 24;

    private static final class Run {
        final int point; final ServerLevel level; final BlockPos pos; final Mob mob; final long end;
        final List<UUID> members = new ArrayList<>();
        Run(int point, ServerLevel level, BlockPos pos, Mob mob, long end) {
            this.point = point; this.level = level; this.pos = pos; this.mob = mob; this.end = end;
        }
    }
    private static final List<Run> RUNS = new ArrayList<>();

    public static boolean isTrialMob(Entity e) {
        return e.getPersistentData().getBoolean(TAG);
    }

    public static boolean inRun(ServerPlayer p) {
        for (Run r : RUNS) if (r.members.contains(p.getUUID())) return true;
        return false;
    }

    /** 시련을 받을 수 있는 상태 (갈래 고름, 아직 통과 안 함) */
    private static boolean holds(PlayerData d) {
        return d.job != 0 && d.branch == 0 && d.pendingBranch > 0 && !d.trialCleared;
    }

    /** 시련 시작. 실패하면 이유 */
    public static String start(ServerPlayer p, int family) {
        PlayerData d = RpgData.get(p);
        if (d.job != family || d.branch > 0) return "이 교관의 시련을 받을 수 없어요";
        if (d.trialCleared) return "이미 시련을 통과했어요. 전직을 완료하세요";
        if (d.pendingBranch == 0) return "먼저 갈래를 골라 주세요";
        if (!Jobs.nearInstructor(p, family)) return "교관 가까이에서 시작할 수 있어요";
        if (inRun(p)) return "이미 시련 중이에요";
        MinecraftServer server = p.getServer();
        if (server == null) return "서버 오류";
        QuestPlaces pl = QuestPlaces.get(server);
        if (pl.trials.isEmpty()) return "시련장이 아직 없어요 (관리자: /rpg trial point add)";
        // 비어 있는 시련장 중 가장 가까운 곳
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < pl.trials.size(); i++) {
            int idx = i;
            if (RUNS.stream().anyMatch(r -> r.point == idx)) continue;
            QuestPlaces.Place pc = pl.trials.get(i);
            boolean same = pc.dim().equals(p.level().dimension().location().toString());
            double dist = (same ? 0 : 1e9) + pc.pos().distSqr(p.blockPosition());
            if (dist < bd) { bd = dist; best = i; }
        }
        if (best < 0) return "시련장이 모두 사용 중이에요. 잠시 뒤에 다시 와 주세요";
        QuestPlaces.Place pc = pl.trials.get(best);
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(pc.dim())));
        if (level == null) return "시련장 월드를 찾을 수 없어요";
        Mob mob = rpg.core.combat.Mobs.spawn(level, MOB_KEY, MOB_LEVEL, pc.pos(), "", false);
        if (mob == null) return "시련 몹을 만들 수 없어요 (mobs.json " + MOB_KEY + ")";
        mob.getPersistentData().putBoolean(TAG, true);
        Run run = new Run(best, level, pc.pos(), mob, level.getGameTime() + LIMIT_TICKS);
        // 같이 들어갈 사람: 24칸 안 파티원 중 시련을 받은 사람 (나 포함)
        for (ServerPlayer m : rpg.core.party.Parties.nearby(p)) {
            if (m != p && (m.distanceToSqr(p) > LEAVE * LEAVE || !holds(RpgData.get(m)) || inRun(m))) continue;
            run.members.add(m.getUUID());
        }
        RUNS.add(run);
        int i = 0;
        for (UUID id : run.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(id);
            if (m == null) continue;
            double a = i++ * 0.7;   // 몹에서 7칸 떨어진 곳에 둥글게
            double x = pc.pos().getX() + 0.5 + Math.cos(a) * 7, z = pc.pos().getZ() + 0.5 + Math.sin(a) * 7;
            float yaw = (float) Math.toDegrees(Math.atan2(-(pc.pos().getX() + 0.5 - x), pc.pos().getZ() + 0.5 - z));
            m.teleportTo(level, x, pc.pos().getY(), z, yaw, 0);
            m.sendSystemMessage(Component.literal("[교관의 시련] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("3분 안에 시련의 허수아비를 쓰러뜨리세요! (쓰러지거나 24칸 넘게 벗어나면 실패)").withStyle(ChatFormatting.YELLOW)));
        }
        level.playSound(null, pc.pos(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
        return null;
    }

    private static void fail(ServerPlayer p, String why) {
        if (p == null) return;
        p.sendSystemMessage(Component.literal("[교관의 시련] 실패: " + why + " — 교관에게 가서 바로 다시 도전할 수 있어요").withStyle(ChatFormatting.RED));
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || RUNS.isEmpty()) return;
        MinecraftServer server = e.getServer();
        for (Run r : List.copyOf(RUNS)) {
            long now = r.level.getGameTime();
            if (now % 10 != 0) continue;
            // 통과: 몹이 쓰러짐
            if (!r.mob.isAlive()) {
                for (UUID id : r.members) {
                    ServerPlayer m = server.getPlayerList().getPlayer(id);
                    if (m == null) continue;
                    PlayerData d = RpgData.get(m);
                    d.trialCleared = true;
                    RpgData.sync(m);
                    m.sendSystemMessage(Component.literal("[교관의 시련] 통과! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("교관에게 돌아가 2차 전직을 완료하세요").withStyle(ChatFormatting.YELLOW)));
                    m.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, m.getX(), m.getY() + 1, m.getZ(), 40, 0.5, 1, 0.5, 0.3);
                    m.level().playSound(null, m.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
                }
                RUNS.remove(r);
                continue;
            }
            boolean timeUp = now >= r.end;
            r.members.removeIf(id -> {
                ServerPlayer m = server.getPlayerList().getPlayer(id);
                if (m == null) return true;
                String why = timeUp ? "시간이 다 됐어요"
                        : !m.isAlive() || RpgData.get(m).isDown() ? "쓰러졌어요"
                        : m.level() != r.level || m.blockPosition().distSqr(r.pos) > LEAVE * LEAVE ? "시련장을 벗어났어요" : null;
                if (why == null) return false;
                fail(m, why);
                return true;
            });
            if (r.members.isEmpty()) {
                r.mob.discard();
                RUNS.remove(r);
                continue;
            }
            if (now % 20 == 0) {
                long left = Math.max(0, (r.end - now) / 20);
                Component bar = Component.literal(String.format("교관의 시련  %d:%02d", left / 60, left % 60))
                        .withStyle(left <= 30 ? ChatFormatting.RED : ChatFormatting.GOLD);
                for (UUID id : r.members) {
                    ServerPlayer m = server.getPlayerList().getPlayer(id);
                    if (m != null) m.displayClientMessage(bar, true);
                }
            }
        }
    }

    /** 서버를 껐다 켜서 남은 시련 몹은 지움 (진행 중인 시련은 저장 안 함) */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !isTrialMob(e.getEntity())) return;
        for (Run r : RUNS) if (r.mob == e.getEntity()) return;
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        RUNS.clear();
    }

    // ───────── 시련장 위치 (/rpg trial point) ─────────
    public static void addPoint(ServerPlayer p) {
        QuestPlaces pl = QuestPlaces.get(p.server);
        pl.trials.add(new QuestPlaces.Place(p.level().dimension().location().toString(), p.blockPosition()));
        pl.setDirty();
    }

    /** 8칸 안 가장 가까운 시련장 지우기 */
    public static boolean removePoint(ServerPlayer p) {
        QuestPlaces pl = QuestPlaces.get(p.server);
        String dim = p.level().dimension().location().toString();
        QuestPlaces.Place best = null;
        double bd = 64;
        for (QuestPlaces.Place pc : pl.trials) {
            if (!pc.dim().equals(dim)) continue;
            double dd = pc.pos().distSqr(p.blockPosition());
            if (dd <= bd) { bd = dd; best = pc; }
        }
        if (best == null) return false;
        pl.trials.remove(best);
        pl.setDirty();
        return true;
    }

    public static List<String> points(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        var list = QuestPlaces.get(server).trials;
        for (int i = 0; i < list.size(); i++) {
            int idx = i;
            boolean busy = RUNS.stream().anyMatch(r -> r.point == idx);
            out.add((i + 1) + ". " + list.get(i).text() + (busy ? " (사용 중)" : ""));
        }
        return out;
    }
}
