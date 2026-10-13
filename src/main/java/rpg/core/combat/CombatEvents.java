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
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
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

    /** 평타 직전의 공격 쿨타임 충전도 (바닐라가 피해 계산 전에 초기화해서 미리 기억) */
    private static final Map<UUID, Float> STRENGTH = new WeakHashMap<>();

    @SubscribeEvent
    public static void attachMob(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Mob && !e.getObject().getCapability(MobData.Provider.CAP).isPresent()) {
            e.addCapability(RpgCore.id("mob"), new MobData.Provider());
        }
    }

    @SubscribeEvent
    public static void beforeAttack(AttackEntityEvent e) {
        STRENGTH.put(e.getEntity().getUUID(), e.getEntity().getAttackStrengthScale(0.5f));
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
            float str = STRENGTH.getOrDefault(ap.getUUID(), 1.0f);
            STRENGTH.remove(ap.getUUID());
            double ratio = e.getSource().getDirectEntity() == ap ? Math.max(0.2, str * str) : 1.0;  // 평타 쿨타임 덜 찼으면 약하게
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

    /** 몹 처치 보상 */
    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || mob.level().isClientSide || !isRpg(mob)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer)) return;
        MobData m = Combat.mob(mob);
        PlayerData d = RpgData.get(killer);
        long gold = Math.round(m.gold * (1.0 + d.goldBonus() / 100.0));
        d.gold += gold;
        RpgData.sync(killer);
        // 경험치: 근처 파티원끼리 균등 분배 + 인원당 보너스
        var members = rpg.core.party.Parties.nearby(killer);
        int n = members.size();
        double total = m.exp * (1 + rpg.core.balance.Balance.partyBonusPerMember * (n - 1) / 100.0);
        long each = Math.max(1, Math.round(total / n));
        for (ServerPlayer mem : members) {
            Net.reward(mem, each, mem == killer ? gold : 0);
            Progress.giveExp(mem, each);
        }
    }

    /** RPG 몹은 바닐라 드롭·경험치 구슬 없음 (드롭은 나중에 RPG 드롭으로) */
    @SubscribeEvent
    public static void drops(LivingDropsEvent e) {
        if (e.getEntity() instanceof Mob m && isRpg(m)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void xpDrop(LivingExperienceDropEvent e) {
        if (e.getEntity() instanceof Mob m && isRpg(m)) e.setCanceled(true);
    }

    /** 무기 바꾸면 스공 다시 보내기 */
    @SubscribeEvent
    public static void equip(LivingEquipmentChangeEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) RpgData.sync(sp);
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
