package rpg.core.combat;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.Progress;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/**
 * 전투 처리. RPG 몹은 가상 체력(MobData.hp)을 쓰고, 바닐라 체력은 그 비율을 비춘다.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class CombatEvents {
    private CombatEvents() {}

    /**
     * 근접 배율: 들어온 바닐라 피해 ÷ 공격력 속성. Epic Fight 모션별 배율(콤보·강공격)과
     * 바닐라 쿨타임·치명타가 그대로 RPG 피해에 곱해짐. 대상 엔티티 id 별로 기억.
     */
    private static final Map<Integer, Double> MELEE_RATIO = new java.util.HashMap<>();

    @SubscribeEvent
    public static void attachMob(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Mob && !e.getObject().getCapability(MobData.Provider.CAP).isPresent()) {
            e.addCapability(RpgCore.id("mob"), new MobData.Provider());
        }
    }

    /** RPG 몹끼리는 안 싸움: 다른 몹을 노리지 않음 */
    @SubscribeEvent
    public static void noInfight(net.minecraftforge.event.entity.living.LivingChangeTargetEvent e) {
        if (e.getEntity() instanceof Mob m && !m.level().isClientSide && isRpg(m)
                && e.getNewTarget() != null && !(e.getNewTarget() instanceof net.minecraft.world.entity.player.Player)) {
            e.setCanceled(true);
        }
    }

    /** RPG 몹끼리 피해 없음 (스켈레톤 화살 오사 등) */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void noFriendlyFire(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if (e.getEntity() instanceof Mob t && !t.level().isClientSide && e.getSource().getEntity() instanceof Mob a
                && isRpg(t) && isRpg(a)) e.setCanceled(true);
    }

    /** 좀비 → 드라운드 같은 변신 없음 */
    @SubscribeEvent
    public static void noConvert(net.minecraftforge.event.entity.living.LivingConversionEvent.Pre e) {
        if (e.getEntity() instanceof Mob m && isRpg(m)) {
            e.setCanceled(true);
            e.setConversionTimer(Integer.MAX_VALUE / 2);
        }
    }

    // ── 슬라임 분열 막기: RPG 슬라임이 죽은 자리에서 같은 순간 생기는 슬라임은 취소 ──
    private record Split(net.minecraft.world.level.Level level, net.minecraft.world.phys.Vec3 pos, long time) {}
    private static final java.util.List<Split> SPLITS = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void noSplit(net.minecraftforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof net.minecraft.world.entity.monster.Slime s) || SPLITS.isEmpty()) return;
        long now = e.getLevel().getGameTime();
        SPLITS.removeIf(sp -> now - sp.time() > 40);   // 분열은 죽는 모션(약 1초) 뒤에 일어남
        if (Combat.mob(s) != null && Combat.mob(s).isRpg()) return;
        for (Split sp : SPLITS) {
            if (sp.level() == e.getLevel() && sp.pos().distanceToSqr(s.position()) < 16) { e.setCanceled(true); return; }
        }
    }

    /** RPG 몹은 햇빛에 안 탐 */
    @SubscribeEvent
    public static void noBurn(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent e) {
        if (e.getEntity() instanceof Mob m && !m.level().isClientSide && m.getRemainingFireTicks() > 0 && !m.isInLava()
                && m.level().canSeeSky(m.blockPosition()) && isRpg(m)) m.clearFire();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (e.getSource().getEntity() instanceof ServerPlayer ap && e.getSource().getDirectEntity() == ap) {
            double base = Math.max(0.5, ap.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
            MELEE_RATIO.put(e.getEntity().getId(), Math.max(0.2, Math.min(4.0, e.getAmount() / base)));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide) return;
        Entity src = e.getSource().getEntity();

        // ── 플레이어가 맞음 ──
        if (target instanceof ServerPlayer sp) {
            PlayerData d = RpgData.get(sp);
            double dmg;
            if (src instanceof Mob m && isRpg(m)) dmg = Combat.mobToPlayer(Combat.mob(m).atk, d);
            else if (src instanceof Mob) dmg = e.getAmount() / 20.0 * d.maxHp * (1 - Combat.defReducePct(d) / 100.0);  // 일반 몹: 비율 + 방어
            else dmg = e.getAmount() / 20.0 * d.maxHp;   // 환경 피해: 바닐라와 같은 비율
            d.hp = Math.max(0, d.hp - dmg);
            mirror(e, sp, d.hp, d.maxHp);
            RpgData.sync(sp);
            return;
        }

        // ── RPG 몹이 맞음 ──
        if (!(target instanceof Mob mob) || !isRpg(mob)) return;
        MobData m = Combat.mob(mob);
        double dmg;
        boolean crit = false;
        if (src instanceof ServerPlayer ap) {
            Entity direct = e.getSource().getDirectEntity();
            Double mr = MELEE_RATIO.remove(target.getId());
            double ratio;
            if (direct == ap) ratio = mr == null ? 1.0 : mr;                       // 근접 (Epic Fight 모션 배율)
            else if (direct != null && direct.getPersistentData().contains("rpgPower"))
                ratio = Math.max(0.1, direct.getPersistentData().getFloat("rpgPower"));  // 활: 당긴 정도
            else ratio = 1.0;
            Combat.Hit h = Combat.roll(ap, RpgData.get(ap), ratio);
            dmg = h.amount();
            crit = h.crit();
        } else if (src instanceof Mob other && isRpg(other)) {
            dmg = Combat.mob(other).atk;
        } else {
            dmg = e.getAmount() / Math.max(1f, mob.getMaxHealth()) * m.maxHp;  // 환경 피해
        }
        m.hp = Math.max(0, m.hp - dmg);
        mirror(e, mob, m.hp, m.maxHp);
        Mobs.sync(mob);
        if (src instanceof ServerPlayer) Net.damageText(mob, dmg, crit);
    }

    private static boolean isRpg(Mob m) {
        return Combat.isRpgMob(m);
    }

    /** RPG 체력 비율에 맞게 바닐라가 받을 피해량을 정함 */
    private static void mirror(LivingDamageEvent e, LivingEntity t, double hp, double maxHp) {
        float cur = t.getHealth();
        if (hp <= 0) {
            e.setAmount(cur + 1000f);
            return;
        }
        float want = (float) Math.max(0.5, hp / maxHp * t.getMaxHealth());
        e.setAmount(Math.max(0f, cur - want));
    }

    @SubscribeEvent
    public static void slimeDied(LivingDeathEvent e) {
        if (e.getEntity() instanceof net.minecraft.world.entity.monster.Slime s && !s.level().isClientSide && isRpg(s)) {
            SPLITS.add(new Split(s.level(), s.position(), s.level().getGameTime()));
        }
    }

    /** 몹 처치 보상 */
    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || mob.level().isClientSide || !isRpg(mob)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer)) return;
        MobData m = Combat.mob(mob);
        PlayerData d = RpgData.get(killer);
        rpg.core.loot.Drops.roll(mob, m, killer, d);   // 드롭은 막타 기준 (골드는 주머니로)
        // 경험치: 근처 파티원끼리 균등 분배 + 인원당 보너스
        var members = rpg.core.party.Parties.nearby(killer);
        int n = members.size();
        double total = m.exp * (1 + rpg.core.balance.Balance.partyBonusPerMember * (n - 1) / 100.0);
        long each = Math.max(1, Math.round(total / n));
        for (ServerPlayer mem : members) {
            Net.reward(mem, each, 0);
            Progress.giveExp(mem, each);
        }
    }

    /** RPG 몹은 바닐라 드롭·경험치 구슬 없음 (RPG 드롭은 loot.Drops) */
    @SubscribeEvent
    public static void drops(LivingDropsEvent e) {
        if (e.getEntity() instanceof Mob m && isRpg(m)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void xpDrop(LivingExperienceDropEvent e) {
        if (e.getEntity() instanceof Mob m && isRpg(m)) e.setCanceled(true);
    }


    /** 몹이 보이기 시작하면 레벨·체력 보내기 */
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof Mob mob && e.getEntity() instanceof ServerPlayer sp) {
            MobData m = Combat.mob(mob);
            if (m != null && m.isRpg()) Net.mobInfoTo(sp, mob, m);
        }
    }
}
