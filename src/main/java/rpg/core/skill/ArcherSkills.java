package rpg.core.skill;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import rpg.core.data.PlayerData;

/** 궁수 개편 스킬: 스나이핑 (조준 후 한 발) · 표식 사격 */
public final class ArcherSkills {
    private ArcherSkills() {}

    /** 궁수 모션 (Epic Fight). /rpg anim set <스킬> <모션> 으로 바꿔 끼우기 가능 */
    public static final String AIM = "BIPED_BOW_AIM", SHOT = "BIPED_BOW_SHOT";
    public static final String BACKFLIP = "BIPED_PHANTOM_ASCENT_BACKWARD|BIPED_ROLL_BACKWARD";

    /** 모션 재생 (안 되면 팔 휘두르기) */
    public static void anim(ServerPlayer p, String skill, String defaults) {
        if (!rpg.core.compat.EpicFightCompat.playSkill(p, skill, defaults)) p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    // ───────── 스나이핑: duration초 조준 (이동 -50%) → 관통 화살 ─────────
    static boolean sniping(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        int aim = (int) Math.max(1, Math.round(s.duration() * 20));
        d.aimUntil = now + aim;
        double pct = s.value(lv);
        ServerLevel l = p.serverLevel();
        l.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 1f, 0.8f);
        anim(p, "sniping", AIM);   // 조준 모션
        rpg.core.net.Net.aimLine(p, aim);   // 조준선 (클라에서 끊김 없는 선으로 그림)
        BranchSkills.task(p, t -> {
            if (t < aim) {
                if (t == aim - 4) l.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 1f, 1.2f);
                return false;
            }
            JobSkills.arrow(p, pct, 0, 5.0f, a -> {
                a.setPierceLevel((byte) 10);
                a.setCritArrow(true);
            });
            l.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getEyeY(), p.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
            l.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.2f, 0.6f);
            anim(p, "sniping_shot", SHOT);
            return true;
        });
        return true;
    }

    // ───────── 표식 사격: 맞으면 표식 (그 몹이 받는 내 피해 +value2%) ─────────
    static boolean markShot(ServerPlayer p, Defs.SkillDef s, int lv) {
        JobSkills.arrow(p, s.value(lv), 0, 3.5f, a -> {
            a.getPersistentData().putDouble("rpgMark", s.value2(lv));
            a.getPersistentData().putInt("rpgMarkTicks", (int) Math.round(s.duration() * 20));
        });
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f, 0.8f);
        anim(p, "mark_shot", SHOT);
        return true;
    }
}
