package rpg.core.compat;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.RpgCore;
import rpg.core.data.RpgData;

/**
 * Epic Fight 연결 (EF 코드를 직접 부르지 않고 이름으로만 연결)
 *  - 접속하면 기본 구르기·가드 스킬 지급, 스킬북은 못 씀 (RPG 스킬·패시브만)
 *  - 스태미나 스탯 → EF 최대 스태미나 (epicfight:staminar)
 *  - EF 몹 체력바 끔 (RPG 체력바 사용)
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class EpicFightCompat {
    private EpicFightCompat() {}

    private static final UUID STAMINA_ID = UUID.fromString("6f1f0d2e-7a51-4c1e-9b0a-52d0c1a1e003");
    private static final ResourceLocation STAMINA = new ResourceLocation("epicfight", "staminar");
    private static final ResourceLocation SKILLBOOK = new ResourceLocation("epicfight", "skillbook");

    private static void run(MinecraftServer server, String cmd) {
        try {
            CommandSourceStack src = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
            server.getCommands().performPrefixedCommand(src, cmd);
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] Epic Fight 명령 실패: {} ({})", cmd, ex.toString());
        }
    }

    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent e) {
        run(e.getServer(), "gamerule disableEntityUI true");
        run(e.getServer(), "gamerule keepSkills true");
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.getServer() == null) return;
        String name = sp.getGameProfile().getName();
        run(sp.getServer(), "epicfight skill add " + name + " dodge epicfight:roll");
        run(sp.getServer(), "epicfight skill add " + name + " guard epicfight:guard");
    }

    @SubscribeEvent
    public static void skillbook(PlayerInteractEvent.RightClickItem e) {
        if (SKILLBOOK.equals(ForgeRegistries.ITEMS.getKey(e.getItemStack().getItem()))) {
            e.setCanceled(true);
            if (!e.getLevel().isClientSide) e.getEntity().displayClientMessage(
                    Component.literal("이 서버에서는 Epic Fight 스킬북을 쓸 수 없어요").withStyle(ChatFormatting.RED), true);
        }
    }

    // ───────── 전투 모드에서는 블록을 못 캠 (EF 클래스는 이름으로 찾아 부름) ─────────
    private static java.lang.reflect.Method getPatch, isBattle;
    private static Class<?> playerPatchClass;
    private static boolean efLookedUp;

    /** EF 전투 모드인가 (EF 없거나 실패하면 false) */
    public static boolean battleMode(net.minecraft.world.entity.player.Player p) {
        try {
            if (!efLookedUp) {
                efLookedUp = true;
                Class<?> caps = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
                playerPatchClass = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
                getPatch = caps.getMethod("getEntityPatch", net.minecraft.world.entity.Entity.class, Class.class);
                isBattle = playerPatchClass.getMethod("isEpicFightMode");
            }
            if (getPatch == null) return false;
            Object patch = getPatch.invoke(null, p, playerPatchClass);
            return patch != null && (boolean) isBattle.invoke(patch);
        } catch (Throwable ex) {
            getPatch = null;
            return false;
        }
    }

    /** 우리 무기 (검·활·지팡이·단검) 들고 있나 */
    public static boolean holdingRpgWeapon(net.minecraft.world.entity.player.Player p) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(p.getMainHandItem().getItem());
        if (id == null || !RpgCore.MODID.equals(id.getNamespace())) return false;
        String n = id.getPath();
        return n.startsWith("warrior_sword") || n.startsWith("archer_bow") || n.startsWith("mage_staff") || n.startsWith("thief_dagger");
    }

    private static boolean noBreak(net.minecraft.world.entity.player.Player p) {
        return !p.isCreative() && (holdingRpgWeapon(p) || battleMode(p));
    }

    @SubscribeEvent
    public static void noBreakInBattle(PlayerEvent.BreakSpeed e) {
        if (noBreak(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void noBreakInBattle2(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
        if (noBreak(e.getPlayer())) e.setCanceled(true);
    }

    // ───────── EF 모션 재생 (보이기만, 피해는 우리 스킬이 줌) ─────────
    private static java.lang.reflect.Method playClient;
    private static Class<?> livingPatchClass;
    private static boolean animLookedUp;

    /** 모션 이름 바꿔 끼우기 (/rpg anim set 스킬 이름) — 게임에서 보고 고른 이름 */
    public static final java.util.Map<String, String> OVERRIDE = new java.util.HashMap<>();

    /**
     * 모션 재생. field = "A|B|C" 면 앞에서부터 되는 것 하나. 스킬 키("skill:id")면 OVERRIDE 먼저.
     */
    public static boolean playAnim(ServerPlayer p, String field) {
        if (field.contains("|")) {
            for (String f : field.split("\\|")) if (playOne(p, f.trim())) return true;
            return false;
        }
        return playOne(p, field);
    }

    /** 모션 재생 속도 배수 (클라에서 보이는 속도): 회전 모션이 느려서 빠르게 */
    /** (EF 20.14 에선 공격 모션 속도가 안 바뀜 — 비워 둠, 진단용으로만 남김) */
    public static final java.util.Map<String, Float> SPEED = new java.util.HashMap<>();
    private static boolean speedDone;
    /** 진단: 속도 설정 결과 · 마지막으로 재생한 모션 */
    public static String speedStatus = "아직 안 함";
    public static final java.util.Map<String, String> LAST = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * EF 모션에 재생 속도 배수 붙이기 (서버·클라 둘 다, 화면은 클라 쪽).
     * 공격 모션(AttackAnimation)은 PLAY_SPEED_MODIFIER 를 안 보고 "플레이어 공격 속도 / BASIS_ATTACK_SPEED" 로 속도를 정함
     * → BASIS = 대검 공속(1.6) / 배수, FACTOR = 1 로 넣으면 그 배수만큼 빨라짐.
     */
    public static void applySpeeds() {
        if (speedDone || speedTries > 40) return;
        speedTries++;
        StringBuilder st = new StringBuilder();
        boolean ok = false;
        for (var e : SPEED.entrySet()) {
            String r = setSpeed(e.getKey(), e.getValue());
            if (r.contains(" 적용")) ok = true;
            st.append(r).append(" · ");
        }
        speedStatus = st.toString();
        speedDone = ok;   // 모션이 아직 안 불러와졌으면 다음에 다시 시도
    }

    /** 클라 쪽 (화면에 그리는 모션은 클라 인스턴스일 수 있어서 따로): 클라 틱에서 몇 번 다시 넣음 */
    private static int clientTries;
    public static String clientSpeedStatus = "아직 안 함";

    public static void applySpeedsClient() {
        if (clientTries > 200 || clientTries++ % 20 != 0) return;   // 1초마다, 10번
        StringBuilder st = new StringBuilder();
        for (var e : SPEED.entrySet()) st.append(setSpeed(e.getKey(), e.getValue())).append(" · ");
        clientSpeedStatus = st.toString();
    }

    /** 무기 기준 공속 (전사 대검) */
    public static float BASE_ATTACK_SPEED = 1.6f;
    private static int speedTries;

    public static String setSpeed(String field, float mul) {
        SPEED.put(field, mul);
        try {
            Object anim;
            try { anim = Class.forName("yesman.epicfight.gameasset.Animations").getField(field).get(null); }
            catch (NoSuchFieldException nf) { return field + " 없음"; }
            if (anim == null) return field + " null";
            // EF 20.x: Animations.X 는 AnimationAccessor (껍데기) → get() 으로 실제 모션 꺼내기
            for (int i = 0; i < 3 && anim.getClass().getSimpleName().contains("Accessor"); i++) {
                java.lang.reflect.Method g = null;
                for (var m : anim.getClass().getMethods()) if (m.getName().equals("get") && m.getParameterCount() == 0) { g = m; break; }
                if (g == null) break;
                Object inner = g.invoke(anim);
                if (inner == null || inner == anim) break;
                anim = inner;
            }
            Class<?> atkProp = Class.forName("yesman.epicfight.api.animation.property.AnimationProperty$AttackAnimationProperty");
            Object basis = atkProp.getField("BASIS_ATTACK_SPEED").get(null), factor = atkProp.getField("ATTACK_SPEED_FACTOR").get(null);
            // addProperty 는 다음 리로드 때 적용(지연)이라 → 모션 안의 속성 맵에 바로 넣음
            java.util.Map<Object, Object> props = null;
            String found = "";
            for (Class<?> c = anim.getClass(); c != null && props == null; c = c.getSuperclass()) {
                for (var f : c.getDeclaredFields()) {
                    if (java.util.Map.class.isAssignableFrom(f.getType()) && f.getName().toLowerCase().contains("propert")
                            && !java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        @SuppressWarnings("unchecked") var m = (java.util.Map<Object, Object>) f.get(anim);
                        props = m;
                        found = c.getSimpleName() + "." + f.getName();
                        break;
                    }
                }
            }
            if (props == null) return field + " 속성 맵 없음 (" + anim.getClass().getSimpleName() + ")";
            props.put(basis, BASE_ATTACK_SPEED / mul);
            props.put(factor, 1.0f);
            return field + " ×" + mul + " 적용 [" + found + " @" + Integer.toHexString(System.identityHashCode(anim)) + "]";
        } catch (Throwable ex) {
            return field + " 실패: " + ex.getClass().getSimpleName();
        }
    }

    /** 스킬용: 바꿔 끼운 이름이 있으면 그것, 없으면 기본 후보들 */
    public static boolean playSkill(ServerPlayer p, String skill, String defaults) {
        String o = OVERRIDE.get(skill);
        if (o != null) return o.equals("NONE") || playAnim(p, o);
        return playAnim(p, defaults);
    }

    /** Animations 클래스의 모션 이름 목록 (filter 포함하는 것만) */
    public static java.util.List<String> animNames(String filter) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            for (var f : Class.forName("yesman.epicfight.gameasset.Animations").getFields())
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && (filter.isEmpty() || f.getName().contains(filter.toUpperCase())))
                    out.add(f.getName());
        } catch (Throwable ignored) {}
        java.util.Collections.sort(out);
        return out;
    }

    private static boolean playOne(ServerPlayer p, String field) {
        applySpeeds();
        LAST.put("try", field);
        try {
            if (!animLookedUp) {
                animLookedUp = true;
                Class<?> caps = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
                livingPatchClass = Class.forName("yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch");
                if (getPatch == null) getPatch = caps.getMethod("getEntityPatch", net.minecraft.world.entity.Entity.class, Class.class);
                for (var m : livingPatchClass.getMethods())
                    if (m.getName().equals("playAnimationInClientSide") && m.getParameterCount() == 2) playClient = m;
            }
            if (playClient == null || getPatch == null) return false;
            Object anim = Class.forName("yesman.epicfight.gameasset.Animations").getField(field).get(null);
            Object patch = getPatch.invoke(null, p, livingPatchClass);
            if (anim == null || patch == null) return false;
            playClient.invoke(patch, anim, 0f);
            LAST.put("played", field);
            return true;
        } catch (Throwable ex) {
            RpgCore.LOG.debug("[rpgcore] EF 모션 실패 {}: {}", field, ex.toString());
            return false;
        }
    }

    /** 스태미나 스탯·패시브 → EF 최대 스태미나 */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp) || sp.tickCount % 10 != 0) return;
        Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(STAMINA);
        if (attr == null) return;
        AttributeInstance inst = sp.getAttribute(attr);
        if (inst == null) return;
        double want = rpg.core.world.RpgWorld.in(sp) ? RpgData.get(sp).staminaBonus() : 0;   // 야생: 기본 스태미나
        AttributeModifier cur = inst.getModifier(STAMINA_ID);
        if (cur != null && Math.abs(cur.getAmount() - want) < 1e-6) return;
        if (cur != null) inst.removeModifier(STAMINA_ID);
        if (want != 0) inst.addTransientModifier(new AttributeModifier(STAMINA_ID, "rpg stamina", want, AttributeModifier.Operation.ADDITION));
    }
}
