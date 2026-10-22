package rpg.core.party;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/** 사망 패널티 · 다운·부활 · 파티 HUD 동기화 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class PartyEvents {
    private PartyEvents() {}

    // ───────── 죽음: 다운으로 바꾸거나, 경험치 잃기 ─────────
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        if (!d.forceDie && !d.isDown() && canGoDown(sp)) {
            e.setCanceled(true);
            goDown(sp, d);
            return;
        }
        d.forceDie = false;
        d.downEnd = 0;
        d.reviver = null;
        long loss = Math.round(Balance.expNeed(d.level) * Balance.deathExpLossPct / 100.0);
        long lost = Math.min(d.exp, loss);
        d.exp -= lost;
        if (lost > 0) sp.sendSystemMessage(Component.literal("사망: 경험치 " + String.format("%,d", lost) + " 잃음").withStyle(ChatFormatting.RED));
    }

    /** 다운될 수 있나: 설정상 다운이 켜져 있고, 근처에 살아있는(다운 아닌) 파티원이 있을 때 */
    private static boolean canGoDown(ServerPlayer sp) {
        if (!Balance.downEverywhere && !rpg.core.combat.Boss.inArena(sp)) return false;   // 다운은 보스 경기장 안에서만 (downEverywhere 면 어디서나)
        for (ServerPlayer m : Parties.nearby(sp)) {
            if (m != sp && !RpgData.get(m).isDown()) return true;
        }
        return false;
    }

    private static void goDown(ServerPlayer sp, PlayerData d) {
        long now = sp.level().getGameTime();
        d.downEnd = now + Math.round(Balance.downSeconds * 20);
        d.reviver = null;
        d.hp = 1;
        sp.setHealth(1f);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (Balance.downSeconds * 20) + 20, 6, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (Balance.downSeconds * 20) + 20, 0, false, false));
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 0.6f);
        Party party = Parties.of(sp);
        if (party != null) Parties.broadcast(sp.getServer(), party, sp.getGameProfile().getName() + "님이 쓰러졌어요! 옆에서 우클릭으로 살리세요 ("
                + (int) Balance.downSeconds + "초)");
        RpgData.sync(sp);
    }

    // ───────── 다운 중: 피해·공격·스킬 막기 ─────────
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void noDamageWhileDown(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && RpgData.get(sp).isDown() && !RpgData.get(sp).forceDie) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void noAttackWhileDown(AttackEntityEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && RpgData.get(sp).isDown()) e.setCanceled(true);
    }

    // ───────── 부활: 쓰러진 동료를 우클릭 ─────────
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getEntity() instanceof ServerPlayer helper) || !(e.getTarget() instanceof ServerPlayer downed)) return;
        PlayerData dd = RpgData.get(downed);
        if (!dd.isDown() || RpgData.get(helper).isDown()) return;
        Party pa = Parties.of(helper);
        if (pa == null || pa != Parties.of(downed)) return;
        e.setCanceled(true);
        if (dd.reviver != null) return;
        dd.reviver = helper.getUUID();
        dd.reviveStart = downed.level().getGameTime();
        double cut = RpgData.get(helper).bonus("reviveTime");
        dd.reviveNeed = (int) Math.max(5, Math.round(Balance.reviveSeconds * 20 * (1 - cut / 100.0)));
        helper.displayClientMessage(Component.literal("부활시키는 중… (가까이 있어야 해요)").withStyle(ChatFormatting.GREEN), true);
        RpgData.sync(downed);
    }

    // ───────── 매 틱: 다운 시간·부활 진행 ─────────
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        if (!d.isDown()) return;
        long now = sp.level().getGameTime();
        if (d.reviver != null) {
            ServerPlayer h = sp.getServer() == null ? null : sp.getServer().getPlayerList().getPlayer(d.reviver);
            if (h == null || !h.isAlive() || RpgData.get(h).isDown() || h.distanceToSqr(sp) > 3.5 * 3.5) {
                d.reviver = null;
                if (h != null) h.displayClientMessage(Component.literal("부활이 끊겼어요").withStyle(ChatFormatting.RED), true);
                RpgData.sync(sp);
            } else if (now - d.reviveStart >= d.reviveNeed) {
                revive(sp, d, h);
                return;
            } else if (now % 5 == 0) {
                int pct = (int) ((now - d.reviveStart) * 100 / d.reviveNeed);
                h.displayClientMessage(Component.literal("부활 " + pct + "%").withStyle(ChatFormatting.GREEN), true);
                RpgData.sync(sp);
            }
        }
        if (now >= d.downEnd) {
            d.forceDie = true;
            d.downEnd = 0;
            sp.kill();
            return;
        }
        if (now % 20 == 0) RpgData.sync(sp);
    }

    private static void revive(ServerPlayer sp, PlayerData d, ServerPlayer helper) {
        d.downEnd = 0;
        d.reviver = null;
        d.hp = Math.max(1, d.maxHp * Balance.reviveHpPct / 100.0);
        sp.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        sp.removeEffect(MobEffects.GLOWING);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.5f);
        Party party = Parties.of(sp);
        if (party != null) Parties.broadcast(sp.getServer(), party, helper.getGameProfile().getName() + "님이 " + sp.getGameProfile().getName() + "님을 살렸어요");
        RpgData.sync(sp);
    }

    // ───────── 파티 HUD: 1초마다 파티원 정보 보내기 ─────────
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) Net.partyTo(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Parties.logout(sp);
    }
}
