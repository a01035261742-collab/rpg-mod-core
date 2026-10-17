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

    /** 이 플레이어가 지금 쓸 수 있는 스킬인가 (초보자 스킬은 계속, 2차는 고른 갈래만) */
    public static boolean available(PlayerData d, Defs.SkillDef s) {
        if (s.job() != 0 && s.job() != d.job) return false;
        if (s.tier() > d.jobTier()) return false;
        return s.branch() == 0 || s.branch() == d.branch;
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
        if (s.passive()) d.recalc();
        RpgData.sync(p);
    }

    /** 스킬 창에서 스킬을 키 칸에 넣기. 이미 그 칸에 있으면 빼기, 다른 칸에 있으면 옮기기 */
    public static void setSlot(ServerPlayer p, int slot, String id) {
        PlayerData d = RpgData.get(p);
        if (slot < 0 || slot >= PlayerData.SLOTS) return;
        if (id.isEmpty()) { d.slots[slot] = null; RpgData.sync(p); return; }
        Defs.SkillDef s = Defs.SKILLS.get(id);
        if (s == null || s.passive() || !available(d, s) || d.skills.getOrDefault(id, 0) <= 0) return;
        if (id.equals(d.slots[slot])) { d.slots[slot] = null; RpgData.sync(p); return; }
        for (int i = 0; i < PlayerData.SLOTS; i++) if (id.equals(d.slots[i])) d.slots[i] = null;
        d.slots[slot] = id;
        RpgData.sync(p);
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
        if (s == null || lv <= 0 || s.passive() || !available(d, s)) return;
        long now = p.level().getGameTime();
        if (now < d.cdEnd.getOrDefault(id, 0L)) return;
        if (d.mp < s.mp(lv)) {
            p.displayClientMessage(Component.literal("MP가 부족해요").withStyle(ChatFormatting.BLUE), true);
            return;
        }
        if (s.charge()) {   // 누르고 있는 동안 모으기, 떼면 발동
            if (d.chargeSkill != null) return;
            d.chargeSkill = id; d.chargeStart = now; d.chargeSlot = slot;
            RpgData.sync(p);
            return;
        }
        boolean ok = switch (id) {
            case "sprint" -> sprint(p, d, s, lv, now);
            case "first_aid" -> firstAid(p, d, s, lv);
            default -> JobSkills.use(p, d, s, lv, now);
        };
        if (!ok) return;
        spend(d, s, lv, now);
        RpgData.sync(p);
    }

    private static void spend(PlayerData d, Defs.SkillDef s, int lv, long now) {
        d.mp -= s.mp(lv);
        // 빠른 영창: 쿨타임 감소
        int cd = (int) Math.round(s.cooldown(lv) * 20 * Math.max(0.1, 1 - d.bonus("cdPct") / 100.0));
        d.cdEnd.put(s.id(), now + cd);
        d.cdLen.put(s.id(), cd);
    }

    /** 차지 스킬 키를 뗐을 때 (또는 최대 시간이 지나면 자동) */
    public static void release(ServerPlayer p) {
        PlayerData d = RpgData.get(p);
        if (d.chargeSkill == null) return;
        String id = d.chargeSkill;
        long now = p.level().getGameTime();
        long held = now - d.chargeStart;
        d.chargeSkill = null; d.chargeSlot = -1;
        Defs.SkillDef s = Defs.SKILLS.get(id);
        int lv = d.skills.getOrDefault(id, 0);
        if (s == null || lv <= 0 || d.isDown() || !p.isAlive() || d.mp < s.mp(lv)) { RpgData.sync(p); return; }
        if ("power_strike".equals(id)) JobSkills.releaseCharge(p, d, s, lv, held);
        else if ("charged_shot".equals(id)) JobSkills.chargedShot(p, d, s, lv, held);
        spend(d, s, lv, now);
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
        double heal = d.maxHp * s.value(lv) / 100.0 * (1 + d.healBonus() / 100.0) * (1 + d.bonus("healPct") / 100.0);
        JobSkills.heal(p, d, p, d, heal);
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
        if (d.bonus("dodgeSpeed") > 0) {   // 회피 본능: 구른 뒤 2초 이동속도↑
            d.buffEnd.put("evasion_instinct", now + 40);
            d.buffVal.put("evasion_instinct", d.bonus("dodgeSpeed"));
        }
        p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.3, p.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7f, 1.4f);
        RpgData.sync(p);
    }

    /** Epic Fight 구르기를 함 (클라가 키 눌림을 알려줌): 회피 본능 */
    public static void rolled(ServerPlayer p) {
        PlayerData d = RpgData.get(p);
        long now = p.level().getGameTime();
        if (d.bonus("dodgeSpeed") <= 0 || d.isDown() || now - d.lastRoll < 10) return;
        d.lastRoll = now;
        d.buffEnd.put("evasion_instinct", now + 40);
        d.buffVal.put("evasion_instinct", d.bonus("dodgeSpeed"));
        RpgData.sync(p);
    }

    private static final UUID ATK_SPEED_ID = UUID.fromString("6f1f0d2e-7a51-4c1e-9b0a-52d0c1a1e004");

    // ───────── 매 틱: 이동속도 (패시브 + 질주 + 헤이스트 - 차지), 공격 속도 (헤이스트), 차지 ─────────
    public static void tick(ServerPlayer p, PlayerData d) {
        long now = p.level().getGameTime();
        if (d.chargeSkill != null) {
            long held = now - d.chargeStart;
            JobSkills.chargeTick(p, held);
            if (held >= 40) release(p);   // 2초 넘게 누르면 자동 발동
        }
        // 은신: 끝나면 투명 해제
        if (!d.buff("stealth", now) && d.buffEnd.containsKey("stealth")) JobSkills.breakStealth(p, d);
        if (!d.buff("shadow_step", now) && d.buffEnd.containsKey("shadow_step")) JobSkills.breakShadow(p, d);
        if (d.shield > 0 && (now >= d.shieldEnd || d.isDown() || !p.isAlive())) d.shield = 0;
        AttributeInstance as = p.getAttribute(Attributes.ATTACK_SPEED);
        if (as != null) {
            double wantA = 0;   // (헤이스트는 이제 점프력, 예전 공격 속도 보정 제거)
            AttributeModifier ca = as.getModifier(ATK_SPEED_ID);
            if (ca == null || Math.abs(ca.getAmount() - wantA) > 1e-6) {
                if (ca != null) as.removeModifier(ATK_SPEED_ID);
                if (wantA != 0) as.addTransientModifier(new AttributeModifier(ATK_SPEED_ID, "rpg haste", wantA, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        double pct = d.bonus("speedPct") + (now < d.sprintEnd ? d.sprintPct : 0) + d.buffV("haste", now)
                + d.buffV("evasion_instinct", now) + d.buffV("battle_thrill", now)
                - (d.chargeSkill != null ? 50 : 0);
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
        if (now < d.dodgeEnd + 20 || now < d.sprintEnd + 20 || d.chargeSkill != null) return true;
        for (long end : d.buffEnd.values()) if (now < end + 20) return true;
        for (long end : d.cdEnd.values()) if (now < end + 20) return true;
        return false;
    }
}
