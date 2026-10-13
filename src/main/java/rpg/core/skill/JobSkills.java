package rpg.core.skill;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rpg.core.balance.Balance;
import rpg.core.combat.Combat;
import rpg.core.combat.CombatEvents;
import rpg.core.data.PlayerData;

/**
 * 1차 전직 스킬 (skills.json, tier 1)
 *  전사  power_strike 강타 차지(누르고 떼기) · iron_body 아이언 바디
 *  궁수  double_shot 더블 샷 · retreat_shot 후퇴 샷
 *  마법사 magic_bolt 매직 볼트 · magic_guard 매직 가드
 *  도적  stealth 은신(첫 공격 추가 피해 + 출혈) · haste 헤이스트
 */
public final class JobSkills {
    private JobSkills() {}

    /** 스킬 효과 실행. false = 안 씀 (MP·쿨 안 깎음) */
    public static boolean use(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        return switch (s.id()) {
            case "iron_body" -> buff(p, d, s, lv, now, ParticleTypes.CRIT, SoundEvents.ANVIL_PLACE);
            case "magic_guard" -> buff(p, d, s, lv, now, ParticleTypes.ENCHANT, SoundEvents.ENCHANTMENT_TABLE_USE);
            case "haste" -> haste(p, s, lv, now);
            case "stealth" -> stealth(p, d, s, lv, now);
            case "double_shot" -> doubleShot(p, s, lv);
            case "retreat_shot" -> retreatShot(p, s, lv);
            case "magic_bolt" -> magicBolt(p, s, lv);
            default -> false;
        };
    }

    private static boolean buff(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now,
                                net.minecraft.core.particles.SimpleParticleType fx, net.minecraft.sounds.SoundEvent snd) {
        d.buffEnd.put(s.id(), now + Math.round(s.duration() * 20));
        d.buffVal.put(s.id(), s.value(lv));
        d.buffVal2.put(s.id(), s.value2(lv));
        ServerLevel l = p.serverLevel();
        l.sendParticles(fx, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
        l.playSound(null, p.blockPosition(), snd, SoundSource.PLAYERS, 0.6f, 1.2f);
        return true;
    }

    // ───────── 전사: 강타 차지 ─────────
    /** 떼는 순간 (차지 비율 0~1) */
    public static void releaseCharge(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long held) {
        double minT = 6, maxT = 30;   // 0.3초 ~ 1.5초
        double ratio = Math.max(0, Math.min(1, (held - minT) / (maxT - minT)));
        double pct = s.value(lv) * (1 + 1.5 * ratio);   // 최소 value%, 가득 차면 2.5배
        ServerLevel l = p.serverLevel();
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        double range = 3.5 + ratio;
        int hits = 0;
        for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range))) {
            if (!m.isAlive() || !Combat.isRpgMob(m)) continue;
            Vec3 to = m.position().subtract(p.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.length() > range || (flat.lengthSqr() > 0.5 && flat.normalize().dot(look) < 0.45)) continue;
            CombatEvents.skillHit(p, m, pct);
            m.knockback(0.4 + ratio * 0.6, -look.x, -look.z);
            hits++;
        }
        Vec3 c = p.position().add(look.scale(1.8));
        l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, p.getY() + 1, c.z, 3 + (int) (ratio * 4), 0.8, 0.2, 0.8, 0);
        if (ratio >= 1) l.sendParticles(ParticleTypes.EXPLOSION, c.x, p.getY() + 0.5, c.z, 1, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), ratio >= 1 ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0f, 0.8f + (float) ratio * 0.3f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        if (hits == 0) p.displayClientMessage(Component.literal("빗나감").withStyle(ChatFormatting.GRAY), true);
    }

    /** 차지 중 표시 */
    public static void chargeTick(ServerPlayer p, long held) {
        if (held % 4 != 0) return;
        ServerLevel l = p.serverLevel();
        boolean full = held >= 30;
        l.sendParticles(full ? ParticleTypes.FLAME : ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), full ? 4 : 2, 0.4, 0.5, 0.4, 0.01);
        if (held == 30) l.playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.6f);
    }

    // ───────── 궁수 ─────────
    private static Arrow arrow(ServerPlayer p, double pct, float yawOff) {
        ServerLevel l = p.serverLevel();
        Arrow a = new Arrow(l, p);
        a.shootFromRotation(p, p.getXRot(), p.getYRot() + yawOff, 0, 3.0f, 0.3f);
        a.setBaseDamage(0.5);
        a.pickup = AbstractArrow.Pickup.DISALLOWED;
        a.getPersistentData().putBoolean("rpgArrow", true);
        a.getPersistentData().putDouble("rpgSkillPct", pct);
        l.addFreshEntity(a);
        return a;
    }

    private static boolean doubleShot(ServerPlayer p, Defs.SkillDef s, int lv) {
        double pct = s.value(lv);
        arrow(p, pct, -1.5f);
        arrow(p, pct, 1.5f);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.3f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static boolean retreatShot(ServerPlayer p, Defs.SkillDef s, int lv) {
        Vec3 back = Vec3.directionFromRotation(0, p.getYRot()).scale(-1.1);
        p.setDeltaMovement(back.x, 0.35, back.z);
        p.hurtMarked = true;
        Arrow a = arrow(p, s.value(lv), 0);
        a.getPersistentData().putInt("rpgSlow", (int) Math.round(s.duration() * 20));
        a.setCritArrow(true);
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.3, 0.05, 0.3, 0.03);
        l.playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);
        return true;
    }

    // ───────── 마법사: 매직 볼트 (바라보는 몹에 즉시 명중) ─────────
    private static boolean magicBolt(ServerPlayer p, Defs.SkillDef s, int lv) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(Balance.rangedRange));
        var bh = l.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
        AABB box = p.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.2);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(l, p, eye, end, box,
                e -> e instanceof Mob && e.isAlive() && !e.isSpectator());
        Vec3 to = hit != null ? hit.getLocation() : end;
        Vec3 step = to.subtract(eye);
        int n = (int) Math.max(2, step.length() * 2.5);
        for (int i = 1; i <= n; i++) {
            Vec3 q = eye.add(step.scale(i / (double) n)).add(0, -0.15, 0);
            l.sendParticles(ParticleTypes.ENCHANTED_HIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.WITCH, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        l.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.6f, 1.6f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        if (hit != null && hit.getEntity() instanceof Mob t) {
            CombatEvents.skillHit(p, t, s.value(lv));
            l.sendParticles(ParticleTypes.WITCH, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 10, 0.25, 0.25, 0.25, 0);
        }
        return true;
    }

    // ───────── 도적 ─────────
    private static boolean stealth(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        int dur = (int) Math.round(s.duration() * 20);
        d.buffEnd.put("stealth", now + dur);
        d.buffVal.put("stealth", s.value(lv));    // 첫 공격 추가 피해 %
        d.buffVal2.put("stealth", s.value2(lv));  // 출혈: 초당 스공의 %
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, dur, 0, false, false, true));
        // 나를 노리던 몹들이 놓침
        for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32))) {
            if (m.getTarget() == p) m.setTarget(null);
        }
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 25, 0.4, 0.6, 0.4, 0.02);
        l.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.7f, 1.2f);
        return true;
    }

    /** 은신 끝내기 (공격하면) */
    public static void breakStealth(ServerPlayer p, PlayerData d) {
        d.buffEnd.remove("stealth");
        p.removeEffect(MobEffects.INVISIBILITY);
    }

    private static boolean haste(ServerPlayer p, Defs.SkillDef s, int lv, long now) {
        List<ServerPlayer> targets = rpg.core.party.Parties.nearby(p);
        for (ServerPlayer t : targets) {
            PlayerData td = rpg.core.data.RpgData.get(t);
            td.buffEnd.put("haste", now + Math.round(s.duration() * 20));
            td.buffVal.put("haste", s.value(lv));    // 이동속도 %
            td.buffVal2.put("haste", s.value2(lv));  // 공격 속도 %
            t.serverLevel().sendParticles(ParticleTypes.EFFECT, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0.05);
            rpg.core.data.RpgData.sync(t);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    /** 은신 중이면 몹이 못 노림 */
    public static boolean hidden(LivingEntity e) {
        if (!(e instanceof ServerPlayer sp)) return false;
        return rpg.core.data.RpgData.get(sp).buff("stealth", sp.level().getGameTime());
    }
}
