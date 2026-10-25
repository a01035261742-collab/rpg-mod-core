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
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = rpg.core.RpgCore.MODID)
public final class JobSkills {
    private JobSkills() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) onJump(sp);
    }

    /** 스킬 효과 실행. false = 안 씀 (MP·쿨 안 깎음) */
    public static boolean use(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        return switch (s.id()) {
            case "iron_body" -> {
                WarriorSkills.burst(p, ParticleTypes.CRIT, SoundEvents.ARMOR_EQUIP_IRON, 0.7f);
                yield buff(p, d, s, lv, now, ParticleTypes.CRIT, SoundEvents.ANVIL_PLACE);
            }
            case "magic_guard" -> {   // value2초 동안 (지속 연장 적용)
                d.buffEnd.put("magic_guard", now + MageSkills.dur(d, s.value2(lv)));
                d.buffVal.put("magic_guard", s.value(lv));
                WarriorSkills.burst(p, ParticleTypes.ENCHANT, SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f);
                MageSkills.anim(p, "magic_guard");
                yield true;
            }
            case "magic_claw" -> MageSkills.magicClaw(p, s, lv);
            case "haste" -> haste(p, s, lv, now);
            case "stealth" -> stealth(p, d, s, lv, now);
            case "double_shot" -> doubleShot(p, s, lv);
            case "retreat_shot" -> retreatShot(p, s, lv);
            case "booster" -> {   // 부스터: value2초 동안 활 당기는 속도 +value%
                d.buffEnd.put("booster", now + Math.round(s.value2(lv) * 20));
                d.buffVal.put("booster", s.value(lv));
                WarriorSkills.burst(p, ParticleTypes.CRIT, SoundEvents.BEACON_POWER_SELECT, 1.6f);
                ArcherSkills.anim(p, "booster", ArcherSkills.AIM);
                yield true;
            }
            case "magic_bolt" -> magicBolt(p, s, lv);
            default -> BranchSkills.use(p, d, s, lv, now);   // 2차 전직 스킬
        };
    }

    private static boolean buff(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now,
                                net.minecraft.core.particles.SimpleParticleType fx, net.minecraft.sounds.SoundEvent snd) {
        d.buffEnd.put(s.id(), now + MageSkills.dur(d, s.duration()));
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
        double pct = s.value(lv) * (1 + 0.5 * ratio);   // 최소 value%, 가득 차면 1.5배
        ServerLevel l = p.serverLevel();
        Vec3 look = flatAim(p);
        double range = 4.5;
        int hits = 0;
        for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range + 3))) {
            if (!m.isAlive() || !Combat.isRpgMob(m)) continue;
            Vec3 to = m.position().subtract(p.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.length() - m.getBbWidth() / 2 > range || (flat.length() > m.getBbWidth() / 2 + 0.5 && flat.normalize().dot(look) < 0.3)) continue;   // 몸통 가장자리 기준, 앞 약 145°
            CombatEvents.skillHit(p, m, pct);
            m.knockback(0.4 + ratio * 0.6, -look.x, -look.z);
            hits++;
        }
        Vec3 c = p.position().add(look.scale(1.8));
        l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, p.getY() + 1, c.z, 3 + (int) (ratio * 4), 0.8, 0.2, 0.8, 0);
        if (ratio >= 1) l.sendParticles(ParticleTypes.EXPLOSION, c.x, p.getY() + 0.5, c.z, 1, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), ratio >= 1 ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0f, 0.8f + (float) ratio * 0.3f);
        if (!rpg.core.compat.EpicFightCompat.playSkill(p, "power_strike", "GREATSWORD_AUTO1"))   // 대검 내려찍기
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
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
    static rpg.core.item.RpgArrow arrow(ServerPlayer p, double pct, float yawOff) {
        return arrow(p, pct, yawOff, 3.0f, null);
    }

    /** 스킬 화살 (setup 은 쏘기 전에 화살 설정: 관통·폭발 등) */
    static rpg.core.item.RpgArrow arrow(ServerPlayer p, double pct, float yawOff, float speed,
                                        java.util.function.Consumer<rpg.core.item.RpgArrow> setup) {
        ServerLevel l = p.serverLevel();
        rpg.core.item.RpgArrow a = new rpg.core.item.RpgArrow(l, p);
        Vec3 dir = turn(aimDir(p), yawOff);
        a.shoot(dir.x, dir.y, dir.z, speed, 0.3f);
        a.getPersistentData().putDouble("rpgSkillPct", pct);
        tuneArrow(p, a);
        if (setup != null) setup.accept(a);
        l.addFreshEntity(a);
        return a;
    }

    /** 바람 읽기: 화살 속도·사거리 (평타 활에도) */
    public static void tuneArrow(net.minecraft.world.entity.player.Player p, rpg.core.item.RpgArrow a) {
        if (!(p instanceof ServerPlayer sp)) return;
        PlayerData d = rpg.core.data.RpgData.get(sp);
        double spd = d.bonus("arrowSpeedPct");
        if (spd > 0) a.setDeltaMovement(a.getDeltaMovement().scale(1 + spd / 100.0));
        a.range = rpg.core.item.RpgArrow.MAX_DIST + d.bonus("arrowRange");
    }

    private static boolean doubleShot(ServerPlayer p, Defs.SkillDef s, int lv) {
        double pct = s.value(lv);
        arrow(p, pct, 0);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.3f);
        ArcherSkills.anim(p, "double_shot", ArcherSkills.SHOT);
        later(p, 5, () -> {   // 0.25초 뒤 두 번째 화살 (그때 바라보는 방향)
            arrow(p, pct, 0);
            ArcherSkills.anim(p, "double_shot", ArcherSkills.SHOT);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.5f);
        });
        return true;
    }

    private static boolean retreatShot(ServerPlayer p, Defs.SkillDef s, int lv) {
        Vec3 back = flatAim(p).scale(-1.1);
        p.setDeltaMovement(back.x, 0.35, back.z);
        p.hurtMarked = true;
        Arrow a = arrow(p, s.value(lv), 0);
        a.getPersistentData().putInt("rpgSlow", (int) Math.round(s.duration() * 20));
        a.setCritArrow(true);
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.3, 0.05, 0.3, 0.03);
        l.playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);
        ArcherSkills.anim(p, "retreat_shot", ArcherSkills.BACKFLIP);   // 뒤로 공중제비
        return true;
    }

    // ───────── 마법사: 매직 볼트 (바라보는 몹에 즉시 명중) ─────────
    private static boolean magicBolt(ServerPlayer p, Defs.SkillDef s, int lv) {
        Vec3 eye = p.getEyePosition().add(0, -0.15, 0);
        BOLTS.add(new Bolt(p, eye, aimDir(p), s.value(lv), 0));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.6f, 1.6f);
        if (!rpg.core.compat.EpicFightCompat.playAnim(p, "SPEAR_TWOHAND_AUTO1"))   // 지팡이 휘두르기 (보이기만)
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ───────── 지연 실행·마법탄 (매 틱) ─────────
    private record Later(ServerPlayer p, long at, Runnable r) {}
    private static final List<Later> LATER = new java.util.ArrayList<>();

    static void later(ServerPlayer p, int ticks, Runnable r) {
        LATER.add(new Later(p, p.level().getGameTime() + ticks, r));
    }

    /** 마법탄: 눈에 보이는 투사체 (입자), 1틱에 1.2칸, 최대 24칸 */
    private static final class Bolt {
        final ServerPlayer owner; Vec3 pos; final Vec3 dir; final double pct; double travelled;
        Bolt(ServerPlayer o, Vec3 pos, Vec3 dir, double pct, double t) { owner = o; this.pos = pos; this.dir = dir.normalize(); this.pct = pct; travelled = t; }
    }
    private static final List<Bolt> BOLTS = new java.util.ArrayList<>();
    private static final double BOLT_SPEED = 1.2, BOLT_RANGE = 24;

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void serverTick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!LATER.isEmpty()) {
            List<Later> run = new java.util.ArrayList<>();
            LATER.removeIf(x -> {
                if (!x.p().isAlive()) return true;
                if (x.p().level().getGameTime() >= x.at()) { run.add(x); return true; }
                return false;
            });
            run.forEach(x -> x.r().run());
        }
        BOLTS.removeIf(JobSkills::stepBolt);
    }

    /** true = 끝 (맞았거나 막혔거나 너무 멀리 감) */
    private static boolean stepBolt(Bolt b) {
        if (!b.owner.isAlive()) return true;
        ServerLevel l = b.owner.serverLevel();
        Vec3 next = b.pos.add(b.dir.scale(BOLT_SPEED));
        var bh = l.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.owner));
        Vec3 end = bh.getType() != HitResult.Type.MISS ? bh.getLocation() : next;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(l, b.owner, b.pos, end, new AABB(b.pos, end).inflate(0.6),
                x -> x instanceof Mob && x.isAlive() && !x.isSpectator());
        for (int i = 0; i < 3; i++) {
            Vec3 q = b.pos.add(end.subtract(b.pos).scale(i / 3.0));
            l.sendParticles(ParticleTypes.WITCH, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
            l.sendParticles(ParticleTypes.ENCHANTED_HIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        if (hit != null && hit.getEntity() instanceof Mob t) {
            CombatEvents.skillHit(b.owner, t, b.pct);
            l.sendParticles(ParticleTypes.WITCH, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 12, 0.25, 0.25, 0.25, 0);
            l.playSound(null, t.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 1.4f);
            return true;
        }
        if (bh.getType() != HitResult.Type.MISS) {
            l.sendParticles(ParticleTypes.ENCHANT, end.x, end.y, end.z, 8, 0.1, 0.1, 0.1, 0.3);
            return true;
        }
        b.pos = next;
        b.travelled += BOLT_SPEED;
        return b.travelled >= BOLT_RANGE;
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
            td.buffVal2.put("haste", s.value2(lv));  // 점프력 %
            t.serverLevel().sendParticles(ParticleTypes.EFFECT, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0.05);
            rpg.core.data.RpgData.sync(t);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    /** 헤이스트 점프력 */
    public static void onJump(ServerPlayer p) {
        PlayerData d = rpg.core.data.RpgData.get(p);
        double j = d.buffV2("haste", p.level().getGameTime());
        if (j <= 0) return;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x, v.y * (1 + j / 100.0), v.z);
        p.hurtMarked = true;
    }

    /** 은신·그림자 발걸음 중이면 몹이 못 노림 */
    public static boolean hidden(LivingEntity e) {
        if (!(e instanceof ServerPlayer sp)) return false;
        PlayerData d = rpg.core.data.RpgData.get(sp);
        long now = sp.level().getGameTime();
        return d.buff("stealth", now) || d.buff("shadow_step", now);
    }

    // ───────── 조준 ─────────
    /** 스킬 방향: 조준점(클라 화면 가운데가 가리키는 곳, 3초 안에 받은 것)으로. 없으면 바라보는 방향 */
    public static Vec3 aimDir(ServerPlayer p) {
        PlayerData d = rpg.core.data.RpgData.get(p);
        if (d.aim != null && p.level().getGameTime() - d.aimTick <= 60) {
            Vec3 to = d.aim.subtract(p.getEyePosition());
            if (to.lengthSqr() > 1.0) return to.normalize();
        }
        return p.getLookAngle();
    }

    /** 조준 방향의 수평 성분 (근접 범위·돌진용) */
    public static Vec3 flatAim(ServerPlayer p) {
        Vec3 a = aimDir(p);
        Vec3 f = new Vec3(a.x, 0, a.z);
        return f.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : f.normalize();
    }

    /** 좌우로 deg 도 돌리기 (부채꼴 발사용) */
    public static Vec3 turn(Vec3 v, float deg) {
        return deg == 0 ? v : v.yRot((float) Math.toRadians(-deg));
    }

    // ───────── 2차 전직 쪽으로 넘기기 ─────────
    public static void chargedShot(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long held) { BranchSkills.chargedShot(p, d, s, lv, held); }
    public static void heal(ServerPlayer caster, PlayerData cd, ServerPlayer target, PlayerData td, double amount) { BranchSkills.heal(caster, cd, target, td, amount); }
    public static void onHit(ServerPlayer p, PlayerData d, long now) { BranchSkills.onHit(p, d, now); }
    public static void onKill(ServerPlayer p, PlayerData d) { BranchSkills.onKill(p, d); PassiveEffects.onKill(p, d); }

    /** 그림자 발걸음 끝내기 (시간 끝 / 공격) */
    public static void breakShadow(ServerPlayer p, PlayerData d) {
        d.buffEnd.remove("shadow_step");
        if (!d.buff("stealth", p.level().getGameTime())) p.removeEffect(MobEffects.INVISIBILITY);
    }
}
