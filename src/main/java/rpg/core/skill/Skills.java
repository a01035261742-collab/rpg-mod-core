package rpg.core.skill;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 스킬 사용·배우기·패시브 찍기·구르기 (서버) */
public final class Skills {
    private Skills() {}

    private static final UUID SPEED_ID = UUID.fromString("6f1f0d2e-7a51-4c1e-9b0a-52d0c1a1e001");

    /** 이 플레이어가 지금 쓸 수 있는 스킬인가 (초보자 스킬은 2차 전직 전까지) */
    public static boolean available(PlayerData d, Defs.SkillDef s) {
        if (s.tier() == 0) return d.jobTier() < 2;
        return s.tier() <= d.jobTier();
    }

    // ───────── 스킬 배우기 ─────────
    public static void learn(ServerPlayer p, String id) {
        PlayerData d = RpgData.get(p);
        Defs.SkillDef s = Defs.SKILLS.get(id);
        if (s == null || !available(d, s)) return;
        int lv = d.skills.getOrDefault(id, 0);
        if (lv >= s.maxLevel() || d.skillPoints <= 0) return;
        d.skillPoints--;
        d.skills.put(id, lv + 1);
        if (lv == 0) autoSlot(d, id);
        RpgData.sync(p);
    }

    /** 처음 배운 스킬은 빈 키(Q E R F Z X 순)에 자동으로 */
    private static void autoSlot(PlayerData d, String id) {
        for (String s : d.slots) if (id.equals(s)) return;
        for (int i = 0; i < PlayerData.SLOTS; i++) {
            if (d.slots[i] == null) { d.slots[i] = id; return; }
        }
    }

    // ───────── 패시브 찍기 ─────────
    public static void passiveUp(ServerPlayer p, String nodeId) {
        PlayerData d = RpgData.get(p);
        Defs.Node n = Defs.NODES.get(nodeId);
        if (n == null || n.maxRank() <= 0 || d.passivePoints <= 0) return;
        int rank = d.passives.getOrDefault(nodeId, 0);
        if (rank >= n.maxRank()) return;
        for (String req : n.requires()) {
            Defs.Node r = Defs.NODES.get(req);
            if (r != null && r.maxRank() > 0 && d.passives.getOrDefault(req, 0) <= 0) return;
        }
        d.passivePoints--;
        d.passives.put(nodeId, rank + 1);
        d.recalc();
        RpgData.sync(p);
    }

    // ───────── 스킬 쓰기 ─────────
    public static void use(ServerPlayer p, int slot) {
        if (slot < 0 || slot >= PlayerData.SLOTS || !p.isAlive()) return;
        PlayerData d = RpgData.get(p);
        if (d.isDown()) return;
        String id = d.slots[slot];
        if (id == null) return;
        Defs.SkillDef s = Defs.SKILLS.get(id);
        int lv = d.skills.getOrDefault(id, 0);
        if (s == null || lv <= 0 || !available(d, s)) return;
        long now = p.level().getGameTime();
        if (now < d.cdEnd.getOrDefault(id, 0L)) return;
        if (d.mp < s.mp()) {
            p.displayClientMessage(Component.literal("MP가 부족해요").withStyle(ChatFormatting.BLUE), true);
            return;
        }
        boolean ok = switch (id) {
            case "sprint" -> sprint(p, d, s, lv, now);
            case "first_aid" -> firstAid(p, d, s, lv);
            default -> false;
        };
        if (!ok) return;
        d.mp -= s.mp();
        int cd = (int) Math.round(s.cooldown() * 20);
        d.cdEnd.put(id, now + cd);
        d.cdLen.put(id, cd);
        RpgData.sync(p);
    }

    private static boolean sprint(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        d.sprintEnd = now + Math.round(s.duration() * 20);
        d.sprintPct = s.value(lv);
        ServerLevel lv2 = p.serverLevel();
        lv2.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.05, 0.3, 0.05);
        lv2.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    private static boolean firstAid(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        if (d.hp >= d.maxHp) {
            p.displayClientMessage(Component.literal("HP가 가득 차 있어요").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        double heal = d.maxHp * s.value(lv) / 100.0 * (1 + d.healBonus() / 100.0);
        d.hp = Math.min(d.maxHp, d.hp + heal);
        ServerLevel lv2 = p.serverLevel();
        lv2.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.4, 0.4, 0.4, 0);
        lv2.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.8f);
        return true;
    }

    // ───────── 구르기 ─────────
    public static void dodge(ServerPlayer p, float dx, float dz) {
        if (!p.isAlive() || p.isPassenger()) return;
        PlayerData d = RpgData.get(p);
        if (d.isDown()) return;
        long now = p.level().getGameTime();
        if (now < d.dodgeEnd) return;
        Vec3 dir = new Vec3(dx, 0, dz);
        if (dir.lengthSqr() < 0.01) dir = Vec3.directionFromRotation(0, p.getYRot());
        dir = dir.normalize().scale(Balance.dodgePower);
        p.setDeltaMovement(dir.x, p.onGround() ? 0.2 : p.getDeltaMovement().y, dir.z);
        p.hurtMarked = true;
        p.invulnerableTime = Math.max(p.invulnerableTime, Balance.dodgeIframes);
        int cd = (int) Math.round(Balance.dodgeCooldown * 20 * Math.max(0.1, 1 - d.bonus("dodgeCd") / 100.0));
        d.dodgeEnd = now + cd;
        d.dodgeLen = cd;
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.3, p.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7f, 1.4f);
        RpgData.sync(p);
    }

    // ───────── 매 틱: 이동속도 (패시브 + 질주) ─────────
    public static void tick(ServerPlayer p, PlayerData d) {
        long now = p.level().getGameTime();
        double pct = d.bonus("speedPct") + (now < d.sprintEnd ? d.sprintPct : 0);
        AttributeInstance inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(SPEED_ID);
        double want = pct / 100.0;
        if (cur != null && Math.abs(cur.getAmount() - want) < 1e-6) return;
        if (cur != null) inst.removeModifier(SPEED_ID);
        if (want != 0) inst.addTransientModifier(new AttributeModifier(SPEED_ID, "rpg speed", want, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** 쿨타임 도는 중인 게 있으면 (화면 갱신용) */
    public static boolean anyCooldown(ServerPlayer p, PlayerData d) {
        long now = p.level().getGameTime();
        if (now < d.dodgeEnd + 20 || now < d.sprintEnd + 20) return true;
        for (long end : d.cdEnd.values()) if (now < end + 20) return true;
        return false;
    }
}
