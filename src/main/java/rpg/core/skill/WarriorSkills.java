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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.combat.Boss;
import rpg.core.combat.Combat;
import rpg.core.combat.CombatEvents;
import rpg.core.combat.MobData;
import rpg.core.compat.EpicFightCompat;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 전사 개편 스킬: 돌진(몰이) · 피의 회전 · 연속 베기 + 버프 유지 이펙트.
 * 모션은 Epic Fight 기본 모션 (후보를 앞에서부터, /rpg anim 으로 바꿔 끼워 보기 가능).
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class WarriorSkills {
    private WarriorSkills() {}

    /** 스킬별 기본 모션 후보 */
    public static final String ANIM_RUSH = "GREATSWORD_DASH|LONGSWORD_DASH|SWORD_DASH";
    public static final String ANIM_SPIN = "DANCING_EDGE|STEEL_WHIRLWIND|GREATSWORD_AUTO2";   // 쌍검 회전 연타 (빠름)
    public static final String[] ANIM_COMBO = {"GREATSWORD_AUTO1", "GREATSWORD_AUTO2", "GREATSWORD_AIR_SLASH|GREATSWORD_DASH|GREATSWORD_AUTO1"};

    private static void anim(ServerPlayer p, String skill, String defaults) {
        if (!EpicFightCompat.playSkill(p, skill, defaults)) p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    // ───────── 돌진: 피해 없음, 지나간 길의 몹을 끝 지점 앞으로 몰기 ─────────
    static boolean rush(ServerPlayer p, Defs.SkillDef s, int lv) {
        ServerLevel l = p.serverLevel();
        Vec3 dir = BranchSkills.flatLook(p);
        Vec3 from = p.position().add(0, 0.6, 0);
        var bh = l.clip(new ClipContext(from, from.add(dir.scale(6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double dist = bh.getType() == HitResult.Type.MISS ? 6 : Math.max(0, bh.getLocation().distanceTo(from) - 0.5);
        double width = s.value(lv);
        List<Mob> caught = new ArrayList<>();
        for (Mob m : BranchSkills.mobsAround(l, p.position().add(dir.scale(dist / 2)), dist / 2 + width + 1)) {
            MobData md = Combat.mob(m);
            if (md == null || Boss.isBoss(md) || m.getPersistentData().getBoolean("rpgPillar")) continue;   // 보스·기둥 제외
            Vec3 to = new Vec3(m.getX() - p.getX(), 0, m.getZ() - p.getZ());
            double along = to.dot(dir), side = Math.abs(to.x * dir.z - to.z * dir.x), half = m.getBbWidth() / 2;
            if (along >= -0.5 && along <= dist + 1 + half && side <= width + half) caught.add(m);
        }
        Vec3 end = p.position().add(dir.scale(dist));
        Vec3 pile = end.add(dir.scale(1.5));   // 끝 지점 앞 1.5칸
        // 몹마다 모일 자리: 한 점에 겹치면 서로 밀어서 흩어지니 작은 원으로 나눠 세움
        List<Vec3> spot = new ArrayList<>();
        for (int i = 0; i < caught.size(); i++) {
            Mob m = caught.get(i);
            double r = i == 0 ? 0 : 0.5 + m.getBbWidth() * 0.6 + 0.15 * (i / 6);
            double a = i * 2.39996;   // 황금각: 고르게 퍼짐
            spot.add(pile.add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        long now0 = l.getGameTime();
        double step = dist / 5.0;
        BranchSkills.task(p, t -> {
            // 잡힌 몹: 매 틱 자기 자리로 끌려감, 도착하면 멈춤
            for (int i = 0; i < caught.size(); i++) {
                Mob m = caught.get(i);
                if (!m.isAlive()) continue;
                Vec3 v = spot.get(i).subtract(m.position());
                double len = Math.sqrt(v.x * v.x + v.z * v.z);
                if (len > 0.15) m.setDeltaMovement(v.x / len * Math.min(len, 1.4), m.getDeltaMovement().y, v.z / len * Math.min(len, 1.4));
                else m.setDeltaMovement(0, m.getDeltaMovement().y, 0);
                m.getNavigation().stop();
                m.hurtMarked = true;
            }
            if (t >= 6) {
                // 끝: 그 자리에 1초 멈춰 세움 (몹 AI·밀치기로 다시 흩어지지 않게)
                for (Mob m : caught) {
                    if (!m.isAlive()) continue;
                    m.setDeltaMovement(0, m.getDeltaMovement().y, 0);
                    m.hurtMarked = true;
                    MobData md = Combat.mob(m);
                    if (md != null) md.stunUntil = Math.max(md.stunUntil, now0 + 6 + 20);
                }
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
                p.hurtMarked = true;
                l.sendParticles(ParticleTypes.CLOUD, pile.x, pile.y + 0.3, pile.z, 12, 0.8, 0.1, 0.8, 0.02);
                return true;
            }
            if (t < 5) {
                p.setDeltaMovement(dir.x * step, 0, dir.z * step);
                p.hurtMarked = true;
                p.fallDistance = 0;
                l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.2, 0.05, 0.2, 0);
            }
            return false;
        });
        l.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1f, 1.2f);
        anim(p, "charge_rush", ANIM_RUSH);
        return true;
    }

    /** 피의 회전 타 간격 (틱): 5틱 × 5타 = 약 1초, 모션 한 번에 맞춤 */
    public static int SPIN_GAP = 3, SPIN_FIRST = 1;

    // ───────── 피의 회전: 약 1초 동안 5타 (이동 가능) ─────────
    static boolean bloodSpin(ServerPlayer p, Defs.SkillDef s, int lv) {
        double pct = s.value(lv);
        int hits = s.hits(lv);
        anim(p, "blood_spin", ANIM_SPIN);
        for (int i = 0; i < hits; i++) {
            final int k = i;
            Runnable hit = () -> {
                if (!p.isAlive()) return;
                ServerLevel l = p.serverLevel();
                for (Mob m : BranchSkills.mobsAround(l, p.position(), 3.5)) CombatEvents.skillHit(p, m, pct);
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 1, p.getZ(), 6, 1.6, 0.2, 1.6, 0);
                l.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1, p.getZ(), 5, 1.2, 0.3, 1.2, 0.1);
                l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 0.6f + k * 0.08f);
            };
            JobSkills.later(p, SPIN_FIRST + i * SPIN_GAP, hit);   // 모션은 처음 한 번, 타는 모션 도는 동안 고르게
        }
        return true;
    }

    // ───────── 연속 베기: 3단 콤보 ─────────
    static boolean combo(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        long now = p.level().getGameTime();
        int step = now <= d.comboUntil ? d.comboStep : 0;
        double pct = step == 0 ? s.value(lv) : step == 1 ? s.value2(lv) : Defs.value3(s, lv);
        double range = step == 2 ? 4.5 : 3.5, dot = step == 2 ? 0.2 : 0.4;
        ServerLevel l = p.serverLevel();
        Vec3 look = BranchSkills.flatLook(p);
        for (Mob m : BranchSkills.mobsAround(l, p.position(), range)) {
            Vec3 to = new Vec3(m.getX() - p.getX(), 0, m.getZ() - p.getZ());
            if (to.length() > m.getBbWidth() / 2 + 0.5 && to.normalize().dot(look) < dot) continue;
            CombatEvents.skillHit(p, m, pct);
        }
        Vec3 c = p.position().add(look.scale(1.8));
        l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, p.getY() + 1, c.z, step == 2 ? 5 : 2, step == 2 ? 1.2 : 0.6, 0.2, step == 2 ? 1.2 : 0.6, 0);
        if (step == 2) l.sendParticles(ParticleTypes.CRIT, c.x, p.getY() + 1, c.z, 20, 1.2, 0.4, 1.2, 0.3);
        l.playSound(null, p.blockPosition(), step == 2 ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.9f + step * 0.1f);
        anim(p, "combo_slash_" + (step + 1), ANIM_COMBO[step]);
        d.comboStep = step == 2 ? 0 : step + 1;   // 3타 끝나면 0 → Skills.spend 가 3초 쿨
        d.comboUntil = now + 40;                   // 2초 안에 다시 누르면 다음 타
        return true;
    }

    // ───────── 버프 유지 이펙트 (처음 소리·번쩍은 스킬 쓸 때, 유지 중엔 이펙트만) ─────────
    @SubscribeEvent
    public static void auras(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 5 != 0) return;
        PlayerData d = RpgData.get(p);
        long now = p.level().getGameTime();
        ServerLevel l = p.serverLevel();
        if (d.buff("iron_body", now))   // 아이언 바디: 발밑 회색 반짝
            l.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.4, 0.05, 0.4, 0);
        if (d.buff("iron_wall", now))   // 철벽: 몸 주변 흰 방패 반짝
            l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 3, 0.5, 0.6, 0.5, 0);
        if (d.buff("berserk", now))     // 광폭화: 붉은 기운
            l.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.9f, 0.1f, 0.05f), 1.2f),
                    p.getX(), p.getY() + 1, p.getZ(), 4, 0.35, 0.6, 0.35, 0);
    }

    /** 버프 시작 연출: 소리 + 한 번 번쩍 */
    static void burst(ServerPlayer p, net.minecraft.core.particles.ParticleOptions fx, net.minecraft.sounds.SoundEvent snd, float pitch) {
        ServerLevel l = p.serverLevel();
        l.sendParticles(fx, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.15);
        l.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), snd, SoundSource.PLAYERS, 0.9f, pitch);
    }
}
