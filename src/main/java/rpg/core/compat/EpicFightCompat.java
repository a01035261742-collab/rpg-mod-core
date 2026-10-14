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

    /** Animations.<field> 모션을 나와 주변 사람 화면에 재생. 실패하면 false */
    public static boolean playAnim(ServerPlayer p, String field) {
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
        double want = RpgData.get(sp).staminaBonus();
        AttributeModifier cur = inst.getModifier(STAMINA_ID);
        if (cur != null && Math.abs(cur.getAmount() - want) < 1e-6) return;
        if (cur != null) inst.removeModifier(STAMINA_ID);
        if (want != 0) inst.addTransientModifier(new AttributeModifier(STAMINA_ID, "rpg stamina", want, AttributeModifier.Operation.ADDITION));
    }
}
