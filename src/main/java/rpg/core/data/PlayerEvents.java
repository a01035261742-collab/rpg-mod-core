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

    /**
     * 직업 개편 때 한 번만 하는 초기화 한 줄.
     * flag: 이미 받은 사람을 기억하는 이름. 플레이어 저장 데이터에 남아 있으니 **절대 바꾸지 말 것** (바꾸면 다시 초기화됨)
     * job: 대상 직업 번호 (1 전사 · 2 궁수 · 3 마법사 · 4 도적), passivesToo: 패시브 포인트도 돌려줄지
     */
    private record Reform(String flag, int job, boolean passivesToo, String message) {}

    /** 전사 2건 (예전 순서 유지용). 새 개편은 여기 말고 REFORMS 에 추가 */
    private static final java.util.List<Reform> EARLY_REFORMS = java.util.List.of(
            new Reform("rpgWarriorReset", 1, false, "[전사 개편] 스킬 포인트를 전부 돌려받았어요. 스킬 창(L)에서 다시 찍어주세요"),
            new Reform("rpgWarriorSwap", 1, false, "[전사 개편] 연속 베기가 1차, 강타가 버서커 2차 스킬이 됐어요. 스킬 포인트를 전부 돌려받았으니 스킬 창(L)에서 다시 찍어주세요"));

    /** 앞으로 개편이 생기면 끝에 한 줄만 추가 (플래그 이름은 새로 지을 것) */
    private static final java.util.List<Reform> REFORMS = java.util.List.of(
            new Reform("rpgThiefReset", 4, true, "[도적 개편] 스킬·패시브 포인트를 전부 돌려받았어요. 스킬 창(L)·패시브 창(N)에서 다시 찍어주세요"),
            new Reform("rpgMageReset", 3, true, "[마법사 개편] 스킬·패시브 포인트를 전부 돌려받았어요. 스킬 창(L)·패시브 창(N)에서 다시 찍어주세요"),
            new Reform("rpgArcherReset", 2, true, "[궁수 개편] 스킬·패시브 포인트를 전부 돌려받았어요. 스킬 창(L)·패시브 창(N)에서 다시 찍어주세요"));

    /** 아직 안 받은 개편만 실행 (대상 직업이면 초기화 + 안내, 아니면 표시만 하고 넘어감) */
    private static void applyReforms(ServerPlayer sp, PlayerData d, net.minecraft.nbt.CompoundTag keep, java.util.List<Reform> list) {
        for (Reform r : list) {
            if (keep.getInt(r.flag()) >= 1) continue;
            if (d.job == r.job()) refund(sp, d, r.passivesToo(), r.message());
            markDone(sp, keep, r.flag());
        }
    }

    /** 스킬 포인트(+ 원하면 패시브 포인트)를 전부 돌려주고 안내 메시지 */
    private static void refund(ServerPlayer sp, PlayerData d, boolean passivesToo, String message) {
        d.refundSkills();
        if (passivesToo) {
            d.passives.clear();
            d.passivePoints = Math.max(0, (d.level - 9) * Balance.passivePointsPerLevel);
        }
        sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(message).withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    /** "이 개편은 받았음" 표시를 플레이어 저장 데이터에 남김 */
    private static void markDone(ServerPlayer sp, net.minecraft.nbt.CompoundTag keep, String flag) {
        keep.putInt(flag, 1);
        sp.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        if (d.isNew()) d.init();
        // 개편 때 한 번만 하는 초기화 (전사 2건: 아래 "넘친 포인트 돌려주기"보다 먼저 돌던 순서 그대로)
        applyReforms(sp, d, keep, EARLY_REFORMS);
        // 트리 무료 초기화 (한 번만): 이미 2차인 사람한테 1회
        if (keep.getInt("rpgTreeResetV1") < 1) {
            if (d.branch > 0 && d.freeTreeReset == 0) d.freeTreeReset = 1;
            markDone(sp, keep, "rpgTreeResetV1");
        }
        // 스킬 최대 레벨이 줄었으면 (편의 스킬 20 → 10) 넘친 포인트 돌려줌 (매 로그인마다)
        int back = 0;
        for (var se : d.skills.entrySet()) {
            var sd = rpg.core.skill.Defs.SKILLS.get(se.getKey());
            if (sd != null && se.getValue() > sd.maxLevel()) { back += se.getValue() - sd.maxLevel(); se.setValue(sd.maxLevel()); }
        }
        if (back > 0) {
            d.recalc();
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("[스킬] 최대 레벨이 줄어든 스킬의 포인트 " + back + "점을 돌려받았어요").withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        // 직업 개편 (한 번만): 새 개편은 아래 REFORMS 표에 한 줄만 추가
        applyReforms(sp, d, keep, REFORMS);
        // 패시브 트리 개편 (한 번만): 패시브 초기화 + 레벨당 포인트 다시 계산 (레벨업당 2)
        if (keep.getInt("rpgTreeV1") < 1) {
            d.passives.clear();
            d.passivePoints = Math.max(0, (d.level - 9) * rpg.core.balance.Balance.passivePointsPerLevel);
            if (d.level > 1) sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("[패시브 개편] 패시브가 초기화되고 포인트를 다시 받았어요 (레벨업당 2). 패시브 창(N)에서 찍어주세요")
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            markDone(sp, keep, "rpgTreeV1");
        }
        d.recalc();
        rpg.core.net.Net.defsTo(sp);
        RpgData.syncNow(sp);
    }

    /** /reload 하면 스킬·패시브 정의 다시 보내기 */
    @SubscribeEvent
    public static void datapackSync(net.minecraftforge.event.OnDatapackSyncEvent e) {
        if (e.getPlayer() != null) { rpg.core.net.Net.defsTo(e.getPlayer()); return; }
        for (ServerPlayer sp : e.getPlayerList().getPlayers()) {
            rpg.core.net.Net.defsTo(sp);
            RpgData.get(sp).recalc();
            RpgData.syncNow(sp);
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
        RpgData.syncNow(sp);
    }

    private static final java.util.Map<java.util.UUID, Integer> FULL_AFTER_RESPAWN = new java.util.HashMap<>();

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) RpgData.syncNow(sp);
    }

    /**
     * 서버 틱 끝: 이번 틱에 RpgData.sync 가 불린 플레이어에게 한 번만 보냄.
     * 플레이어 틱이 아니라 서버 틱에서 모든 접속자를 보기 때문에, 죽어 있거나 차원 이동 중이어도 전송이 밀리지 않음.
     * 다른 틱 처리가 끝난 뒤에 보내도록 우선순위를 가장 낮게.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void flushSync(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (ServerPlayer sp : e.getServer().getPlayerList().getPlayers()) {
            if (sp.connection == null) continue;
            if (RpgData.get(sp).syncDirty) RpgData.syncNow(sp);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        PlayerData d = RpgData.get(sp);
        d.clock = sp.level().getGameTime();
        if (d.isNew()) { d.init(); RpgData.syncNow(sp); }
        if (!rpg.core.world.RpgWorld.in(sp)) { rpg.core.skill.Skills.tick(sp, d); return; }   // 야생: 바닐라 체력·배고픔
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
                d.mp = Math.min(d.maxMp, d.mp + d.maxMp * (d.job == 3 ? Balance.mageMpRegenPct : Balance.mpRegenPct) / 100.0 * (1 + d.bonus("mpRegenPct") / 100.0)
                        * (d.bonus("meditation") > 0 && d.clock - d.lastAttack >= 60 ? 3 : 1));   // 명상: 3초 안 때리면 ×3
                changed |= hp0 != d.hp || mp0 != d.mp;
            }
        }
        // 사과: 5초마다 HP 고정 회복
        if (sp.tickCount % 100 == 0 && sp.isAlive() && d.hp > 0 && !d.isDown() && d.buffV("item_regen", d.clock) > 0 && d.hp < d.maxHp) {
            d.hp = Math.min(d.maxHp, d.hp + d.buffV("item_regen", d.clock));
            changed = true;
        }
        // 바닐라 체력 = RPG 체력 비율
        if (sp.isAlive() && d.hp > 0) {
            float want = (float) Math.max(0.5, d.hp / d.maxHp * sp.getMaxHealth());
            if (Math.abs(sp.getHealth() - want) > 0.01f) sp.setHealth(want);
        }
        rpg.core.skill.Skills.tick(sp, d);
        if (changed || sp.tickCount % 10 == 0 && rpg.core.skill.Skills.anyCooldown(sp, d)) RpgData.sync(sp);
    }

    /** 도약의 물약: 점프 높이 (위 속도 +%) */
    @SubscribeEvent
    public static void jump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent e) {
        if (!(e.getEntity() instanceof Player p) || !p.level().isClientSide) return;   // 움직임은 클라가 정함
        double v = rpg.core.client.ClientJump.pct(p);
        if (v <= 0) return;
        var m = p.getDeltaMovement();
        p.setDeltaMovement(m.x, m.y * (1 + v / 100.0), m.z);
    }

    /** 바닐라 회복(음식·재생 효과)은 막고 RPG 회복만 */
    @SubscribeEvent
    public static void heal(LivingHealEvent e) {
        if (e.getEntity() instanceof Player p && rpg.core.world.RpgWorld.in(p)) e.setCanceled(true);   // RPG 대륙만 (야생은 바닐라 회복)
    }

    /** 서버가 꺼질 때 호출: 게임 중에 쌓인 상태만 비움 (데이터팩에서 읽은 정의는 그대로) */
    public static void clearState() { FULL_AFTER_RESPAWN.clear(); }
}
