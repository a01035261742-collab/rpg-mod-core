package rpg.core.data;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
        // 전사 스킬 개편 (한 번만): 전사는 스킬 포인트 전부 돌려받음
        var keep = sp.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        if (keep.getInt("rpgWarriorReset") < 1) {
            if (d.job == 1) {
                d.refundSkills();
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("[전사 개편] 스킬 포인트를 전부 돌려받았어요. 스킬 창(L)에서 다시 찍어주세요")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            keep.putInt("rpgWarriorReset", 1);
            sp.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, keep);
        }
        // 패시브 트리 개편 (한 번만): 패시브 초기화 + 레벨당 포인트 다시 계산 (레벨업당 2)
        if (keep.getInt("rpgTreeV1") < 1) {
            d.passives.clear();
            d.passivePoints = Math.max(0, (d.level - 1) * rpg.core.balance.Balance.passivePointsPerLevel);
            if (d.level > 1) sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("[패시브 개편] 패시브가 초기화되고 포인트를 다시 받았어요 (레벨업당 2). 패시브 창(N)에서 찍어주세요")
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            keep.putInt("rpgTreeV1", 1);
            sp.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, keep);
        }
        d.recalc();
        rpg.core.net.Net.defsTo(sp);
        RpgData.sync(sp);
    }

    /** /reload 하면 스킬·패시브 정의 다시 보내기 */
    @SubscribeEvent
    public static void datapackSync(net.minecraftforge.event.OnDatapackSyncEvent e) {
        if (e.getPlayer() != null) { rpg.core.net.Net.defsTo(e.getPlayer()); return; }
        for (ServerPlayer sp : e.getPlayerList().getPlayers()) {
            rpg.core.net.Net.defsTo(sp);
            RpgData.get(sp).recalc();
            RpgData.sync(sp);
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        d.recalc();
        d.hp = d.maxHp;
        d.mp = d.maxMp;
        FULL_AFTER_RESPAWN.put(sp.getUUID(), sp.tickCount + 40);   // 장비·패시브가 늦게 붙어도 2초 동안 가득 채움
        RpgData.sync(sp);
    }

    private static final java.util.Map<java.util.UUID, Integer> FULL_AFTER_RESPAWN = new java.util.HashMap<>();

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) RpgData.sync(sp);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        d.clock = sp.level().getGameTime();
        if (d.isNew()) { d.init(); RpgData.sync(sp); }
        // 배고픔 없음
        sp.getFoodData().setFoodLevel(20);
        sp.getFoodData().setSaturation(5f);

        boolean changed = false;
        if (sp.tickCount % 20 == 0) changed |= d.recalc();
        Integer until = FULL_AFTER_RESPAWN.get(sp.getUUID());
        if (until != null) {
            if (sp.tickCount > until) FULL_AFTER_RESPAWN.remove(sp.getUUID());
            else if (sp.isAlive() && !d.isDown()) {
                d.recalc();
                if (d.hp < d.maxHp || d.mp < d.maxMp) { d.hp = d.maxHp; d.mp = d.maxMp; changed = true; }
            }
        }
        // 자연 회복: regenIntervalSec 마다 한 번 (기본 HP 0% · MP 3%)
        int every = (int) Math.max(20, Math.round(Balance.regenInterval * 20));
        if (sp.tickCount % every == 0) {
            if (sp.isAlive() && d.hp > 0 && !d.isDown()) {
                double hp0 = d.hp, mp0 = d.mp;
                d.hp = Math.min(d.maxHp, d.hp + d.maxHp * Balance.hpRegenPct / 100.0 * (1 + d.bonus("hpRegenPct") / 100.0));
                d.mp = Math.min(d.maxMp, d.mp + d.maxMp * Balance.mpRegenPct / 100.0 * (1 + d.bonus("mpRegenPct") / 100.0));
                changed |= hp0 != d.hp || mp0 != d.mp;
            }
        }
        // 바닐라 체력 = RPG 체력 비율
        if (sp.isAlive() && d.hp > 0) {
            float want = (float) Math.max(0.5, d.hp / d.maxHp * sp.getMaxHealth());
            if (Math.abs(sp.getHealth() - want) > 0.01f) sp.setHealth(want);
        }
        rpg.core.skill.Skills.tick(sp, d);
        if (changed || sp.tickCount % 10 == 0 && rpg.core.skill.Skills.anyCooldown(sp, d)) RpgData.sync(sp);
    }

    /** 바닐라 회복(음식·재생 효과)은 막고 RPG 회복만 */
    @SubscribeEvent
    public static void heal(LivingHealEvent e) {
        if (e.getEntity() instanceof Player) e.setCanceled(true);
    }
}
