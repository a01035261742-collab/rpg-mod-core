package rpg.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import rpg.core.RpgCore;
import rpg.core.net.Net;

/** 클라이언트: 키, HUD 등록, 바닐라 하트·배고픔·경험치 숨기기 */
public final class ClientEvents {
    /**
     * 화면 가운데(조준점)가 가리키는 곳: 카메라에서 64칸 광선 (블록·엔티티).
     * 3인칭·숄더 시점 모드에서도 스킬이 조준점으로 날아가게 서버에 같이 보냄.
     */
    /** 누르고 있는 방향키 (WASD) → 월드 방향 (카메라 기준). 안 누르면 0, 0 */
    public static float[] moveDir(Minecraft mc) {
        if (mc.player == null) return new float[]{0, 0};
        float fwd = mc.player.input.forwardImpulse, left = mc.player.input.leftImpulse;
        if (Math.abs(fwd) < 0.01f && Math.abs(left) < 0.01f) return new float[]{0, 0};
        double yaw = Math.toRadians(mc.gameRenderer.getMainCamera().getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double x = left * cos - fwd * sin, z = fwd * cos + left * sin;
        double l = Math.sqrt(x * x + z * z);
        return new float[]{(float) (x / l), (float) (z / l)};
    }

    public static net.minecraft.world.phys.Vec3 aimPoint(Minecraft mc) {
        var cam = mc.gameRenderer.getMainCamera();
        var lv = cam.getLookVector();
        var dir = new net.minecraft.world.phys.Vec3(lv.x(), lv.y(), lv.z()).normalize();
        var from = cam.getPosition();
        if (mc.player == null || mc.level == null) return from.add(dir.scale(64));
        // 카메라와 플레이어 사이(벽 등)는 건너뛰기: 플레이어 눈 높이쯤부터 광선 시작
        double skip = Math.max(0, mc.player.getEyePosition().subtract(from).dot(dir));
        from = from.add(dir.scale(skip));
        var end = from.add(dir.scale(64));
        var bh = mc.level.clip(new net.minecraft.world.level.ClipContext(from, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        if (bh.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = bh.getLocation();
        var eh = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(mc.player, from, end,
                new net.minecraft.world.phys.AABB(from, end).inflate(1), e -> e != mc.player && !e.isSpectator() && e.isPickable(),
                from.distanceToSqr(end));
        return eh != null ? eh.getLocation() : end;   // 조준점이 닿은 몸 표면 그대로 (예전엔 + 키 절반 → 큰 몹 위로 날아감)
    }

    private ClientEvents() {}

    public static final String CATEGORY = "key.categories.rpgcore";
    public static final KeyMapping STAT_KEY = new KeyMapping("key.rpgcore.stats", InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping SKILL_WINDOW_KEY = new KeyMapping("key.rpgcore.skills", InputConstants.KEY_L, CATEGORY);
    public static final KeyMapping PASSIVE_KEY = new KeyMapping("key.rpgcore.passives", InputConstants.KEY_N, CATEGORY);
    public static final KeyMapping PARTY_KEY = new KeyMapping("key.rpgcore.party", InputConstants.KEY_P, CATEGORY);
    public static final KeyMapping QUEST_KEY = new KeyMapping("key.rpgcore.quests", InputConstants.KEY_J, CATEGORY);
    public static final KeyMapping MENU_KEY = new KeyMapping("key.rpgcore.menu", InputConstants.KEY_M, CATEGORY);
    public static final String[] SLOT_LABELS = {"Q", "E", "R", "F", "Z", "X"};
    public static final KeyMapping[] SKILL_KEYS = {
            new KeyMapping("key.rpgcore.skill1", InputConstants.KEY_Q, CATEGORY),
            new KeyMapping("key.rpgcore.skill2", InputConstants.KEY_E, CATEGORY),
            new KeyMapping("key.rpgcore.skill3", InputConstants.KEY_R, CATEGORY),
            new KeyMapping("key.rpgcore.skill4", InputConstants.KEY_F, CATEGORY),
            new KeyMapping("key.rpgcore.skill5", InputConstants.KEY_Z, CATEGORY),
            new KeyMapping("key.rpgcore.skill6", InputConstants.KEY_X, CATEGORY),
    };

    @Mod.EventBusSubscriber(modid = RpgCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        /** 창고 창: 바닐라 상자 화면(ContainerScreen)을 상속해서 제네릭이 안 맞음 → 원시 타입으로 등록 */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void registerStorage() {
            net.minecraft.client.gui.screens.MenuScreens.register((net.minecraft.world.inventory.MenuType) rpg.core.shop.Storage.TYPE.get(),
                    (net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor) (m, inv, t) -> new StorageScreen((rpg.core.shop.Storage.StorageMenu) m, inv, t));
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(STAT_KEY);
            e.register(SKILL_WINDOW_KEY);
            e.register(PASSIVE_KEY);
            e.register(PARTY_KEY);
            e.register(QUEST_KEY);
            e.register(MENU_KEY);
            for (KeyMapping k : SKILL_KEYS) e.register(k);
        }

        @SubscribeEvent
        public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
            e.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(rpg.core.equip.EquipMenu.TYPE.get(), EquipScreen::new));
            e.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(rpg.core.shop.Trade.TYPE.get(), TradeScreen::new));
            e.enqueueWork(() -> registerStorage());
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rpg_hud", RpgHud::render);
        }
    }

    @Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        private static boolean keysMoved;
        private static final int[] wait = new int[6];
        private static final boolean[] charging = new boolean[6];

        @SubscribeEvent
        public static void hideVanilla(RenderGuiOverlayEvent.Pre e) {
            NamedGuiOverlay o = e.getOverlay();
            if (o == VanillaGuiOverlay.PLAYER_HEALTH.type()
                    || o == VanillaGuiOverlay.FOOD_LEVEL.type()
                    || o == VanillaGuiOverlay.ARMOR_LEVEL.type()
                    || o == VanillaGuiOverlay.EXPERIENCE_BAR.type()
                    || o == VanillaGuiOverlay.AIR_LEVEL.type()) {
                e.setCanceled(true);
            }
            // Epic Fight 무기 고유 스킬 아이콘 (오른쪽 아래, 스킬바와 겹침) 숨김
            if (o.id().equals(EF_INNATE)) e.setCanceled(true);
        }

        private static final net.minecraft.resources.ResourceLocation EF_INNATE =
                new net.minecraft.resources.ResourceLocation("epicfight", "weapon_innate");

        /** 서바이벌에서 인벤토리를 열면 장비 창(인벤토리 + 장비 칸)으로 바꿔 열기 */
        @SubscribeEvent
        public static void openInventory(net.minecraftforge.client.event.ScreenEvent.Opening e) {
            Minecraft mc = Minecraft.getInstance();
            if (e.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                    && mc.player != null && !mc.player.isCreative()) {
                e.setCanceled(true);
                Net.CH.sendToServer(new Net.OpenEquip(0));
            }
        }

        private static int faceTicks;

        /** 캐릭터 몸·머리를 조준점 쪽으로 돌리기 (클라에서 돌리면 서버에도 이동 패킷으로 감) */
        private static void faceAim(Minecraft mc, net.minecraft.world.phys.Vec3 at) {
            var p = mc.player;
            if (p == null) return;
            var d = at.subtract(p.getEyePosition());
            if (d.lengthSqr() < 0.25) return;
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            p.setYRot(yaw); p.setXRot(pitch);
            p.yRotO = yaw; p.xRotO = pitch;
            p.setYHeadRot(yaw); p.yHeadRotO = yaw;
            p.setYBodyRot(yaw); p.yBodyRotO = yaw;
        }

        private static KeyMapping efRoll;
        private static boolean efRollSearched, rollWasDown;

        /** Epic Fight 구르기 키 (이름으로 찾기, 없으면 null) */
        private static KeyMapping efDodgeKey(Minecraft mc) {
            if (!efRollSearched) {
                efRollSearched = true;
                for (KeyMapping k : mc.options.keyMappings) {
                    String n = k.getName();
                    if (n.contains("epicfight") && n.contains("dodge")) { efRoll = k; break; }
                }
            }
            return efRoll;
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (!keysMoved) { keysMoved = true; moveVanillaKeys(mc); }
            if (mc.player != null && mc.player.tickCount % 100 == 1) killInnateKey(mc);
            boolean free = mc.player != null && mc.screen == null;
            // 스킬 쓴 직후·차지 중: 캐릭터가 조준점을 보게 (숄더 시점에서 뒤로 걸으며 써도 앞으로 나가게)
            boolean anyCharge = false;
            for (boolean c : charging) anyCharge |= c;
            if (mc.player != null && (faceTicks > 0 || anyCharge)) {
                faceAim(mc, aimPoint(mc));
                if (faceTicks > 0) faceTicks--;
            }
            // Epic Fight 구르기 키: 누르는 순간 서버에 알림 (회피 본능)
            KeyMapping roll = efDodgeKey(mc);
            boolean rollDown = roll != null && roll.isDown();
            if (rollDown && !rollWasDown && free && mc.player.onGround()) Net.CH.sendToServer(new Net.Rolled(0));
            rollWasDown = rollDown;
            while (STAT_KEY.consumeClick()) if (free) mc.setScreen(new StatScreen());
            while (SKILL_WINDOW_KEY.consumeClick()) if (free) mc.setScreen(new SkillScreen());
            while (PASSIVE_KEY.consumeClick()) if (free) mc.setScreen(new PassiveScreen());
            while (PARTY_KEY.consumeClick()) if (free) mc.setScreen(new PartyScreen());
            while (QUEST_KEY.consumeClick()) if (free) mc.setScreen(new QuestScreen());
            while (MENU_KEY.consumeClick()) if (free) mc.setScreen(new MenuScreen());
            for (int i = 0; i < 6; i++) {
                if (wait[i] > 0) wait[i]--;
                boolean pressed = false;
                while (SKILL_KEYS[i].consumeClick()) pressed = true;
                if (pressed && free && wait[i] == 0) {
                    wait[i] = 4;
                    var a = aimPoint(mc);
                    faceAim(mc, a);
                    faceTicks = 10;
                    float[] mv = moveDir(mc);
                    Net.CH.sendToServer(new Net.UseSkill(i, a.x, a.y, a.z, mv[0], mv[1]));
                    var def = ClientData.slots[i] == null ? null : rpg.core.skill.Defs.SKILLS.get(ClientData.slots[i]);
                    if (def != null && def.charge()) charging[i] = true;
                }
                // 차지 스킬: 키를 떼면 발동
                if (charging[i] && !SKILL_KEYS[i].isDown()) {
                    charging[i] = false;
                    var a = aimPoint(mc);
                    faceAim(mc, a);
                    faceTicks = 10;
                    Net.CH.sendToServer(new Net.SkillRelease(i, a.x, a.y, a.z));
                }
            }
        }

        /**
         * 처음 한 번: 스킬 키와 겹치는 바닐라 키 옮기기
         * 인벤토리 E→Tab, 플레이어 목록 Tab→`, 버리기 Q→Backspace, 손 바꾸기 F→없음
         */
        private static void moveVanillaKeys(Minecraft mc) {
            Options o = mc.options;
            // v4: 도전과제(L) 해제 — 스킬 창 L
            Path flag4 = FMLPaths.CONFIGDIR.get().resolve("rpgcore-keys-v4");
            if (!Files.exists(flag4)) {
                move(o.keyAdvancements, "key.keyboard.l", null);
                KeyMapping.resetMapping();
                o.save();
                try { Files.writeString(flag4, "done"); } catch (IOException ignored) {}
            }
            // v3: Epic Fight 키 — 전투/채광 모드 R→V (스킬 키 R), 스킬 편집 창 K→없음 (스탯 창 K)
            Path flag3 = FMLPaths.CONFIGDIR.get().resolve("rpgcore-keys-v3");
            if (!Files.exists(flag3)) {
                for (KeyMapping k : o.keyMappings) {
                    if (k.getName().equals("key.epicfight.switch_mode")) move(k, "key.keyboard.r", "key.keyboard.v");
                    if (k.getName().equals("key.epicfight.skill_gui")) move(k, "key.keyboard.k", null);
                }
                KeyMapping.resetMapping();
                o.save();
                try { Files.writeString(flag3, "done"); } catch (IOException ignored) {}
            }
            // v2: 소셜 상호작용(P)을 파티 창에 양보
            Path flag2 = FMLPaths.CONFIGDIR.get().resolve("rpgcore-keys-v2");
            if (!Files.exists(flag2)) {
                move(o.keySocialInteractions, "key.keyboard.p", null);
                KeyMapping.resetMapping();
                o.save();
                try { Files.writeString(flag2, "done"); } catch (IOException ignored) {}
            }
            Path flag = FMLPaths.CONFIGDIR.get().resolve("rpgcore-keys-v1");
            if (Files.exists(flag)) return;
            move(o.keyInventory, "key.keyboard.e", "key.keyboard.tab");
            move(o.keyPlayerList, "key.keyboard.tab", "key.keyboard.grave.accent");
            move(o.keyDrop, "key.keyboard.q", "key.keyboard.backspace");
            move(o.keySwapOffhand, "key.keyboard.f", null);
            KeyMapping.resetMapping();
            o.save();
            try { Files.writeString(flag, "done"); } catch (IOException ignored) {}
        }

        /** Epic Fight 무기 고유 스킬은 안 씀: 키가 묶여 있으면 항상 해제 (좌클릭 꾹 = 특수 공격 막기) */
        private static void killInnateKey(Minecraft mc) {
            for (KeyMapping k : mc.options.keyMappings) {
                if (k.getName().equals("key.epicfight.weapon_innate_skill") && !k.isUnbound()) {
                    k.setKey(InputConstants.UNKNOWN);
                    KeyMapping.resetMapping();
                    mc.options.save();
                }
            }
        }

        private static void move(KeyMapping m, String from, String to) {
            try {
                if (!from.equals(m.saveString())) return;
                m.setKey(to == null ? InputConstants.UNKNOWN : InputConstants.getKey(to));
            } catch (RuntimeException ignored) {}
        }
    }
}
