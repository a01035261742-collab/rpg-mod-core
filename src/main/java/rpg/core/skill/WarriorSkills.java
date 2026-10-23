package rpg.core.skill;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import rpg.core.combat.Boss;
import rpg.core.combat.Combat;
import rpg.core.combat.CombatEvents;
import rpg.core.combat.MobData;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 전사 개편 스킬 (1차: 돌진 베기 · 회전 베기 / 가디언: 방패 강타 · 수호의 외침 / 버서커: 피의 회전 · 처형).
 * 판정은 몸통 가장자리 기준 (큰 보스에도 맞게).
 */
final class WarriorSkills {
    private WarriorSkills() {}

    private static void swing(ServerPlayer p, String anim) {
        if (!rpg.core.compat.EpicFightCompat.playAnim(p, anim)) p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    /** 돌진 베기: 앞으로 6칸 (벽 앞에서 멈춤), 지나가는 적 전부 */
    static boolean dashSlash(ServerPlayer p, Defs.SkillDef s, int lv) {
        ServerLevel l = p.serverLevel();
        Vec3 dir = BranchSkills.flatLook(p);
        Vec3 from = p.position().add(0, 0.6, 0);
        var bh = l.clip(new ClipContext(from, from.add(dir.scale(6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double dist = bh.getType() == HitResult.Type.MISS ? 6 : Math.max(0, bh.getLocation().distanceTo(from) - 0.5);
        for (Mob m : BranchSkills.mobsAround(l, p.position().add(dir.scale(dist / 2)), dist / 2 + 1.5)) {
            Vec3 to = new Vec3(m.getX() - p.getX(), 0, m.getZ() - p.getZ());
            double along = to.dot(dir), side = Math.abs(to.x * dir.z - to.z * dir.x), half = m.getBbWidth() / 2;
            if (along >= -0.5 && along <= dist + 0.8 + half && side <= 1.4 + half) CombatEvents.skillHit(p, m, s.value(lv));
        }
        double step = dist / 5.0;
        BranchSkills.task(p, t -> {
            if (t >= 5) {
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
                p.hurtMarked = true;
                return true;
            }
            p.setDeltaMovement(dir.x * step, 0, dir.z * step);
            p.hurtMarked = true;
            p.fallDistance = 0;
            l.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 1, 0.2, 0.1, 0.2, 0);
            return false;
        });
        l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.7f);
        swing(p, "LONGSWORD_DASH");
        return true;
    }

    /** 회전 베기 / 피의 회전: 주변 3.5칸 hits 타. interval = 타 사이 틱 (0 이면 5틱). 도는 동안 움직일 수 있음 */
    static boolean whirl(ServerPlayer p, Defs.SkillDef s, int lv, int hits, int interval) {
        int gap = interval <= 0 ? 5 : interval;
        double pct = s.value(lv);
        boolean blood = hits >= 5;
        for (int i = 0; i < hits; i++) {
            Runnable hit = () -> {
                if (!p.isAlive()) return;
                ServerLevel l = p.serverLevel();
                for (Mob m : BranchSkills.mobsAround(l, p.position(), 3.5)) CombatEvents.skillHit(p, m, pct);
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 6, 1.6, 0.2, 1.6, 0);
                if (blood) l.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1, p.getZ(), 6, 1.2, 0.3, 1.2, 0.1);
                l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, blood ? 0.6f : 1.0f);
                swing(p, "GREATSWORD_AUTO2");
            };
            if (i == 0) hit.run(); else JobSkills.later(p, i * gap, hit);
        }
        return true;
    }

    /** 앞쪽 range 칸 안 (앞 약 120°), 가장 가까운 몹 순 */
    private static List<Mob> front(ServerPlayer p, double range) {
        Vec3 look = BranchSkills.flatLook(p);
        List<Mob> out = new ArrayList<>();
        for (Mob m : BranchSkills.mobsAround(p.serverLevel(), p.position(), range)) {
            Vec3 to = new Vec3(m.getX() - p.getX(), 0, m.getZ() - p.getZ());
            if (to.length() > m.getBbWidth() / 2 + 0.5 && to.normalize().dot(look) < 0.5) continue;
            out.add(m);
        }
        out.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        return out;
    }

    /** 방패 강타: 앞 3칸 + 일반 몹 1초 기절 */
    static boolean shieldBash(ServerPlayer p, Defs.SkillDef s, int lv) {
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime();
        for (Mob m : front(p, 3)) {
            CombatEvents.skillHit(p, m, s.value(lv));
            MobData md = Combat.mob(m);
            if (md != null && !Boss.isBoss(md)) {
                md.stunUntil = Math.max(md.stunUntil, now + 20);
                l.sendParticles(ParticleTypes.CRIT, m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 8, 0.3, 0.1, 0.3, 0.1);
            }
        }
        Vec3 c = p.position().add(BranchSkills.flatLook(p).scale(1.5));
        l.sendParticles(ParticleTypes.EXPLOSION, c.x, p.getY() + 1, c.z, 1, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1f, 0.6f);
        swing(p, "GREATSWORD_AUTO1");
        return true;
    }

    /** 수호의 외침: 주변 8칸 파티원 (나 포함) 보호막 = 각자 최대 HP × v% */
    static boolean guardianShout(ServerPlayer p, Defs.SkillDef s, int lv) {
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime();
        List<ServerPlayer> who = new ArrayList<>();
        who.add(p);
        for (ServerPlayer m : rpg.core.party.Parties.nearby(p)) if (m != p && m.distanceToSqr(p) <= 64) who.add(m);
        for (ServerPlayer m : who) {
            PlayerData md = RpgData.get(m);
            md.shield = Math.max(now < md.shieldEnd ? md.shield : 0, md.maxHp * s.value(lv) / 100.0);
            md.shieldEnd = now + Math.round(s.duration() * 20);
            l.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, m.getX(), m.getY() + 1, m.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
            RpgData.sync(m);
        }
        l.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    /** 처형: 앞 3칸 가장 가까운 한 명. HP 30% 이하면 ×2 (보스 ×1.3) */
    static boolean execute(ServerPlayer p, Defs.SkillDef s, int lv) {
        List<Mob> f = front(p, 3);
        if (f.isEmpty()) return false;   // 대상 없으면 안 씀 (MP·쿨 안 깎음)
        Mob m = f.get(0);
        MobData md = Combat.mob(m);
        double pct = s.value(lv);
        if (md != null && md.hp <= md.maxHp * 0.3) pct *= Boss.isBoss(md) ? 1.3 : 2.0;
        CombatEvents.skillHit(p, m, pct);
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.DAMAGE_INDICATOR, m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 12, 0.4, 0.4, 0.4, 0.2);
        l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 0.5f);
        swing(p, "GREATSWORD_AUTO1");
        return true;
    }
}
