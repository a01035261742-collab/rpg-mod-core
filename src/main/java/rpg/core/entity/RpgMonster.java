package rpg.core.entity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** 새로 만든 몹 공통: 플레이어를 보면 쫓아와서 근접 공격 (피해는 RPG 공식) */
public class RpgMonster extends Monster {
    /** 소리 종류: 0 쥐 · 1 버섯 · 2 짐승 · 3 요정 */
    private final int voice;

    public RpgMonster(EntityType<? extends Monster> type, Level level, int voice) {
        super(type, level);
        this.voice = voice;
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder attrs(double speed) {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.FOLLOW_RANGE, 24).add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new rpg.core.combat.RpgMeleeGoal(this, 1.15));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected SoundEvent getAmbientSound() {
        return switch (voice) {
            case 0 -> SoundEvents.SILVERFISH_AMBIENT;
            case 2 -> SoundEvents.RAVAGER_AMBIENT;
            case 3 -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
            default -> SoundEvents.SLIME_SQUISH_SMALL;
        };
    }

    @Override protected SoundEvent getHurtSound(DamageSource s) {
        return switch (voice) {
            case 0 -> SoundEvents.SILVERFISH_HURT;
            case 2 -> SoundEvents.RAVAGER_HURT;
            case 3 -> SoundEvents.ALLAY_HURT;
            default -> SoundEvents.SLIME_HURT_SMALL;
        };
    }

    @Override protected SoundEvent getDeathSound() {
        return switch (voice) {
            case 0 -> SoundEvents.SILVERFISH_DEATH;
            case 2 -> SoundEvents.RAVAGER_DEATH;
            case 3 -> SoundEvents.ALLAY_DEATH;
            default -> SoundEvents.SLIME_DEATH_SMALL;
        };
    }

    @Override protected float getSoundVolume() { return voice == 0 ? 0.5f : 0.8f; }
}
