package rpg.core.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.combat.Boss;
import rpg.core.combat.Combat;
import rpg.core.combat.CombatEvents;
import rpg.core.combat.MobData;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 도적 개편 스킬: 더블 스탭 · 회피 도약 · 그림자 칼날 · 분신술 · 약탈 작전 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class ThiefSkills {
    private ThiefSkills() {}

    public static final String ANIM_STAB = "DAGGER_AUTO1|SWORD_AUTO1";

    static void anim(ServerPlayer p, String skill, String defaults) {
        if (!rpg.core.compat.EpicFightCompat.playSkill(p, skill, defaults)) p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    // ───────── 더블 스탭: 앞 2연타 ─────────
    static boolean doubleStab(ServerPlayer p, Defs.SkillDef s, int lv) {
        double pct = s.value(lv);
        for (int i = 0; i < 2; i++) {
            final int k = i;
            Runnable hit = () -> {
                if (!p.isAlive()) return;
                ServerLevel l = p.serverLevel();
                Vec3 look = BranchSkills.flatLook(p);
                for (Mob m : BranchSkills.mobsAround(l, p.position(), 3.5)) {
                    Vec3 to = new Vec3(m.getX() - p.getX(), 0, m.getZ() - p.getZ());
                    if (to.length() > m.getBbWidth() / 2 + 0.5 && to.normalize().dot(look) < 0.5) continue;
                    CombatEvents.skillHit(p, m, pct);
                }
                Vec3 c = p.position().add(look.scale(1.5));
                l.sendParticles(ParticleTypes.CRIT, c.x, p.getY() + 1.1, c.z, 8, 0.3, 0.2, 0.3, 0.2);
                l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7f, 1.4f + k * 0.2f);
                anim(p, "double_stab", k == 0 ? ANIM_STAB : "DAGGER_AUTO2|SWORD_AUTO2");
            };
            if (i == 0) hit.run(); else JobSkills.later(p, 4, hit);
        }
        return true;
    }

    // ───────── 회피 도약: 누르는 방향으로 4칸 (무적 없음) ─────────
    static boolean evadeLeap(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        Vec3 look = BranchSkills.flatLook(p);
        Vec3 dir = new Vec3(d.moveX, 0, d.moveZ);
        if (dir.lengthSqr() < 0.01) dir = look;
        dir = dir.normalize();
        double sp = 0.25 * s.value(lv);   // 4칸 ≈ 1.0
        p.setDeltaMovement(dir.x * sp, 0.3, dir.z * sp);
        p.hurtMarked = true;
        p.fallDistance = 0;
        ServerLevel l = p.serverLevel();
        l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.3, 0.05, 0.3, 0.03);
        l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.8f);
        // 방향에 맞는 스텝 모션
        double fwd = dir.dot(look), side = dir.x * look.z - dir.z * look.x;
        String a = Math.abs(fwd) >= Math.abs(side) ? (fwd >= 0 ? "BIPED_STEP_FORWARD" : "BIPED_STEP_BACKWARD")
                : (side > 0 ? "BIPED_STEP_LEFT" : "BIPED_STEP_RIGHT");
        rpg.core.compat.EpicFightCompat.playSkill(p, "evade_leap", a);
        PassiveEffects.onRoll(d, p.level().getGameTime());   // 잔상 걸음 등 구르기 효과도
        return true;
    }

    // ───────── 그림자 칼날: 버프 중 공격 시 30% 확률로 단검 하나 더 ─────────
    private static boolean extra;   // 추가 단검이 또 추가 단검을 부르지 않게

    public static void onDeal(ServerPlayer p, PlayerData d, Mob mob, long now) {
        if (extra || !d.buff("shadow_blade", now) || p.getRandom().nextFloat() >= 0.3f) return;
        double pct = d.buffV("shadow_blade", now);
        JobSkills.later(p, 2, () -> {
            if (!mob.isAlive()) return;
            extra = true;
            try { CombatEvents.skillHit(p, mob, pct); } finally { extra = false; }
            ServerLevel l = p.serverLevel();
            Vec3 a = p.getEyePosition(), b = mob.position().add(0, mob.getBbHeight() * 0.6, 0);
            for (int i = 0; i <= 8; i++) {
                Vec3 q = a.add(b.subtract(a).scale(i / 8.0));
                l.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            l.playSound(null, mob.blockPosition(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.4f, 1.8f);
        });
    }

    // ───────── 분신술 ─────────
    private static final class Clone {
        final ServerPlayer owner; final ArmorStand stand; final long end, start; final double pct;
        Clone(ServerPlayer o, ArmorStand s, long start, long end, double pct) { owner = o; stand = s; this.start = start; this.end = end; this.pct = pct; }
    }
    private static final Map<UUID, Clone> CLONES = new HashMap<>();

    /** 분신이 살아 있으면 터뜨림 (다시 누르기). true = 터뜨렸음 */
    public static boolean detonate(ServerPlayer p) {
        Clone c = CLONES.remove(p.getUUID());
        if (c == null) return false;
        explode(c);
        return true;
    }

    static boolean shadowClone(ServerPlayer p, Defs.SkillDef s, int lv) {
        detonate(p);
        ServerLevel l = p.serverLevel();
        ArmorStand st = new ArmorStand(l, p.getX(), p.getY(), p.getZ());
        st.setYRot(p.getYRot());
        st.setInvulnerable(true);
        st.setShowArms(true);
        st.setNoBasePlate(true);
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.getOrCreateTag().putString("SkullOwner", p.getGameProfile().getName());
        st.setItemSlot(EquipmentSlot.HEAD, head);
        for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND})
            st.setItemSlot(es, p.getItemBySlot(es).copy());
        st.getPersistentData().putLong("rpgDecoyAt", l.getGameTime());
        l.addFreshEntity(st);
        long now = l.getGameTime();
        CLONES.put(p.getUUID(), new Clone(p, st, now, now + Math.round(s.duration() * 20), s.value(lv)));
        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1, p.getZ(), 25, 0.4, 0.6, 0.4, 0.02);
        l.playSound(null, p.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    private static void explode(Clone c) {
        ServerLevel l = (ServerLevel) c.stand.level();
        Vec3 at = c.stand.position();
        c.stand.discard();
        for (Mob m : BranchSkills.mobsAround(l, at, 4)) {
            MobData md = Combat.mob(m);
            if (md != null) md.decoyUntil = 0;
            if (c.owner.isAlive()) CombatEvents.skillHit(c.owner, m, c.pct);
        }
        l.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 30, 1.5, 0.5, 1.5, 0.05);
        l.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9f, 1.2f);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || CLONES.isEmpty()) return;
        List<UUID> done = new ArrayList<>();
        for (var en : CLONES.entrySet()) {
            Clone c = en.getValue();
            ServerLevel l = (ServerLevel) c.stand.level();
            long now = l.getGameTime();
            if (!c.owner.isAlive() || c.owner.hasDisconnected() || c.stand.isRemoved()) { c.stand.discard(); done.add(en.getKey()); continue; }
            if (now >= c.end) { explode(c); done.add(en.getKey()); continue; }
            if (now % 5 == 0) {   // 주변 8칸 몹이 분신을 노림 (보스는 처음 1초만)
                for (Mob m : BranchSkills.mobsAround(l, c.stand.position(), 8)) {
                    MobData md = Combat.mob(m);
                    if (md == null || (Boss.isBoss(md) && now - c.start > 20)) continue;
                    md.decoyUntil = now + 10;
                    md.decoyPos = c.stand.position();
                }
                l.sendParticles(ParticleTypes.SMOKE, c.stand.getX(), c.stand.getY() + 1, c.stand.getZ(), 3, 0.3, 0.5, 0.3, 0.01);
            }
        }
        done.forEach(CLONES::remove);
    }

    /** 서버 껐다 켜서 남은 분신 인형은 지우기 */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ArmorStand st)) return;
        if (st.getPersistentData().contains("rpgDecoyAt") && e.getLevel().getGameTime() - st.getPersistentData().getLong("rpgDecoyAt") > 200)
            e.setCanceled(true);
    }

    // ───────── 약탈 작전: 파티 드롭률·골드 ─────────
    static boolean plunder(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        long now = p.level().getGameTime();
        long end = now + MageSkills.dur(d, s.duration());
        for (ServerPlayer t : rpg.core.party.Parties.nearby(p)) {
            if (t.distanceToSqr(p) > 64) continue;
            PlayerData td = RpgData.get(t);
            if (td.buffV("plunder", now) > s.value(lv)) continue;
            td.buffEnd.put("plunder", end);
            td.buffVal.put("plunder", s.value(lv));
            t.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + 1, t.getZ(), 10, 0.4, 0.6, 0.4, 0);
            if (t != p) RpgData.sync(t);
        }
        WarriorSkills.burst(p, ParticleTypes.HAPPY_VILLAGER, SoundEvents.PLAYER_LEVELUP, 1.6f);
        return true;
    }
}
