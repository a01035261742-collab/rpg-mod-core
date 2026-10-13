package rpg.core.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;

/**
 * 플레이어 RPG 데이터 붙이기·저장·회복·체력.
 * RPG 체력이 진짜 체력이고, 바닐라 체력(하트)은 그 비율을 그대로 비춘다.
 * (피격 모션·죽음은 바닐라 그대로, 숫자는 RPG 기준)
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class PlayerEvents {
    private PlayerEvents() {}

    @Mod.EventBusSubscriber(modid = RpgCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerCaps(RegisterCapabilitiesEvent e) {
            e.register(PlayerData.class);
        }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player && !e.getObject().getCapability(PlayerDataProvider.CAP).isPresent()) {
            e.addCapability(RpgCore.id("player"), new PlayerDataProvider());
        }
    }

    /** 죽거나 엔드에서 돌아올 때 데이터 옮기기 */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        e.getOriginal().getCapability(PlayerDataProvider.CAP).ifPresent(old ->
                e.getEntity().getCapability(PlayerDataProvider.CAP).ifPresent(now -> now.copyFrom(old)));
        e.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        if (d.isNew()) d.init();
        d.recalc();
        RpgData.sync(sp);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        d.recalc();
        d.hp = d.maxHp;
        d.mp = d.maxMp;
        RpgData.sync(sp);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) RpgData.sync(sp);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        if (d.isNew()) { d.init(); RpgData.sync(sp); }
        // 배고픔 없음
        sp.getFoodData().setFoodLevel(20);
        sp.getFoodData().setSaturation(5f);

        boolean changed = false;
        if (sp.tickCount % 20 == 0) {
            changed |= d.recalc();
            if (sp.isAlive() && d.hp > 0) {
                double hp0 = d.hp, mp0 = d.mp;
                d.hp = Math.min(d.maxHp, d.hp + d.maxHp * Balance.hpRegenPct / 100.0);
                d.mp = Math.min(d.maxMp, d.mp + d.maxMp * Balance.mpRegenPct / 100.0);
                changed |= hp0 != d.hp || mp0 != d.mp;
            }
        }
        // 바닐라 체력 = RPG 체력 비율
        if (sp.isAlive() && d.hp > 0) {
            float want = (float) Math.max(0.5, d.hp / d.maxHp * sp.getMaxHealth());
            if (Math.abs(sp.getHealth() - want) > 0.01f) sp.setHealth(want);
        }
        if (changed) RpgData.sync(sp);
    }

    /**
     * 플레이어가 받는 피해 (1단계 임시): 바닐라 피해를 같은 비율로 RPG 체력에 적용.
     * 몹 공격력 공식은 2단계(전투)에서 따로 만든다.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        double rpg = e.getAmount() / 20.0 * d.maxHp;
        d.hp = Math.max(0, d.hp - rpg);
        float cur = sp.getHealth();
        if (d.hp <= 0) e.setAmount(cur + 1000f);
        else e.setAmount(Math.max(0f, cur - (float) Math.max(0.5, d.hp / d.maxHp * sp.getMaxHealth())));
        RpgData.sync(sp);
    }

    /** 바닐라 회복(음식·재생 효과)은 막고 RPG 회복만 */
    @SubscribeEvent
    public static void heal(LivingHealEvent e) {
        if (e.getEntity() instanceof Player) e.setCanceled(true);
    }
}
