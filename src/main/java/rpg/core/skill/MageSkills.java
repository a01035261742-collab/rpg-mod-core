package rpg.core.skill;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import rpg.core.combat.Combat;
import rpg.core.combat.CombatEvents;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 마법사 개편 스킬: 매직 클로 · 체인 라이트닝 · 원소 증폭 · 힐 · 블레스 */
public final class MageSkills {
    private MageSkills() {}

    public static final String ANIM_CAST = "SPEAR_TWOHAND_AUTO1";
    private static final DustParticleOptions CLAW = new DustParticleOptions(new Vector3f(0.7f, 0.35f, 1f), 0.9f);

    static void anim(ServerPlayer p, String skill) {
        if (!rpg.core.compat.EpicFightCompat.playSkill(p, skill, ANIM_CAST)) p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    /** 조준점 기준 앞 30°, range칸 안에서 조준선에 제일 가까운 몹 (벽 너머 제외) */
    static Mob aimTarget(ServerPlayer p, double range) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition(), dir = JobSkills.aimDir(p);
        Mob best = null;
        double bestScore = 1e9;
        for (Mob m : BranchSkills.mobsAround(l, p.position(), range)) {
            Vec3 c = m.position().add(0, m.getBbHeight() * 0.5, 0);
            Vec3 to = c.subtract(eye);
            double dist = to.length();
            if (dist < 0.01 || dist > range + m.getBbWidth() / 2) continue;
            double cos = to.normalize().dot(dir);
            if (cos < Math.cos(Math.toRadians(30)) && dist > m.getBbWidth() / 2 + 1) continue;
            if (l.clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) continue;
            double score = (1 - cos) * 10 + dist * 0.05;   // 조준선 가까운 게 우선
            if (score < bestScore) { bestScore = score; best = m; }
        }
        return best;
    }

    // ───────── 매직 클로: 즉시 명중 2타 ─────────
    static boolean magicClaw(ServerPlayer p, Defs.SkillDef s, int lv) {
        Mob t = aimTarget(p, 12);
        if (t == null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("대상이 없어요").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return false;
        }
        double pct = s.value(lv);
        anim(p, "magic_claw");
        for (int i = 0; i < 2; i++) {
            final int k = i;
            Runnable hit = () -> {
                if (!t.isAlive()) return;
                CombatEvents.skillHit(p, t, pct);
                ServerLevel l = p.serverLevel();
                Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
                double side = k == 0 ? 1 : -1;
                for (int line = -1; line <= 1; line++)   // 대각선 발톱 3줄
                    for (int j = -3; j <= 3; j++) {
                        double a = j * 0.15;
                        l.sendParticles(CLAW, c.x + a * side + line * 0.18, c.y - a, c.z + line * 0.1, 1, 0, 0, 0, 0);
                    }
                l.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.6f + k * 0.2f);
            };
            if (i == 0) hit.run(); else JobSkills.later(p, 3, hit);
        }
        return true;
    }

    // ───────── 체인 라이트닝: 최대 5마리, 튈 때마다 -10% ─────────
    static boolean chainLightning(ServerPlayer p, Defs.SkillDef s, int lv) {
        Mob first = aimTarget(p, 12);
        if (first == null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("대상이 없어요").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return false;
        }
        ServerLevel l = p.serverLevel();
        List<Mob> hit = new ArrayList<>();
        Mob cur = first;
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        double pct = s.value(lv);
        for (int i = 0; i < 5 && cur != null; i++) {
            hit.add(cur);
            Vec3 to = cur.position().add(0, cur.getBbHeight() * 0.6, 0);
            bolt(l, from, to);
            CombatEvents.skillHit(p, cur, pct * (1 - 0.1 * i));
            from = to;
            Mob next = null;
            double best = 25;
            for (Mob m : BranchSkills.mobsAround(l, cur.position(), 5)) {
                if (hit.contains(m)) continue;
                double d2 = m.distanceToSqr(cur);
                if (d2 < best) { best = d2; next = m; }
            }
            cur = next;
        }
        l.playSound(null, first.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.6f);
        anim(p, "chain_lightning");
        return true;
    }

    private static void bolt(ServerLevel l, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.max(4, d.length() * 3);
        var r = l.random;
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n)).add((r.nextDouble() - 0.5) * 0.25, (r.nextDouble() - 0.5) * 0.25, (r.nextDouble() - 0.5) * 0.25);
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    // ───────── 힐: 최대 HP v% + 스공 v2%, 빛의 사도면 회복량 일부를 몹에게 ─────────
    static boolean heal(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        double total = 0;
        double sa = Combat.statAttack(p, d);
        long now = p.level().getGameTime();
        for (ServerPlayer t : rpg.core.party.Parties.nearby(p)) {
            if (t.distanceToSqr(p) > 8 * 8) continue;
            PlayerData td = RpgData.get(t);
            if (td.isDown()) continue;
            double amount = Math.round((td.maxHp * s.value(lv) / 100.0 + sa * s.value2(lv) / 100.0) * (1 + d.healBonus() / 100.0));
            total += amount;
            BranchSkills.heal(p, d, t, td, amount);
            if (d.bonus("healBuff") > 0) {   // 축복받은 손
                td.buffEnd.put("blessed_hand", now + 100);
                td.buffVal.put("blessed_hand", d.bonus("healBuff"));
                if (t != p) RpgData.sync(t);
            }
            t.serverLevel().sendParticles(ParticleTypes.HEART, t.getX(), t.getY() + 1.2, t.getZ(), 5, 0.4, 0.4, 0.4, 0);
            t.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + 1, t.getZ(), 10, 0.4, 0.6, 0.4, 0);
        }
        if (d.bonus("healDmg") > 0 && total > 0) {   // 빛의 사도
            double dmg = total * d.bonus("healDmg") / 100.0;
            ServerLevel l = p.serverLevel();
            for (Mob m : BranchSkills.mobsAround(l, p.position(), 4)) {
                CombatEvents.fixedHit(p, m, dmg);
                l.sendParticles(ParticleTypes.END_ROD, m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
            }
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        anim(p, "heal");
        return true;
    }

    // ───────── 블레스: 8칸 파티원 데미지 +v%, v2초 ─────────
    static boolean bless(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        long now = p.level().getGameTime();
        long end = now + Math.round(s.value2(lv) * 20 * (1 + d.bonus("buffDur") / 100.0));
        for (ServerPlayer t : rpg.core.party.Parties.nearby(p)) {
            if (t.distanceToSqr(p) > 8 * 8) continue;
            PlayerData td = RpgData.get(t);
            if (td.buffV("bless", now) > s.value(lv)) continue;   // 더 센 블레스가 걸려 있으면 그대로
            td.buffEnd.put("bless", end);
            td.buffVal.put("bless", s.value(lv));
            t.serverLevel().sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0.05);
            if (t != p) RpgData.sync(t);
        }
        WarriorSkills.burst(p, ParticleTypes.END_ROD, SoundEvents.BEACON_ACTIVATE, 1.5f);
        anim(p, "bless");
        return true;
    }

    /** 버프 길이 (지속 연장 적용) */
    static long dur(PlayerData d, double seconds) {
        return Math.round(seconds * 20 * (1 + d.bonus("buffDur") / 100.0));
    }
}
