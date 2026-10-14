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
    private ClientEvents() {}

    public static final String CATEGORY = "key.categories.rpgcore";
    public static final KeyMapping STAT_KEY = new KeyMapping("key.rpgcore.stats", InputConstants.KEY_K, CATEGORY);
    public static final KeyMapping SKILL_WINDOW_KEY = new KeyMapping("key.rpgcore.skills", InputConstants.KEY_L, CATEGORY);
    public static final KeyMapping PASSIVE_KEY = new KeyMapping("key.rpgcore.passives", InputConstants.KEY_N, CATEGORY);
    public static final KeyMapping PARTY_KEY = new KeyMapping("key.rpgcore.party", InputConstants.KEY_P, CATEGORY);
    public static final KeyMapping QUEST_KEY = new KeyMapping("key.rpgcore.quests", InputConstants.KEY_J, CATEGORY);
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
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(STAT_KEY);
            e.register(SKILL_WINDOW_KEY);
            e.register(PASSIVE_KEY);
            e.register(PARTY_KEY);
            e.register(QUEST_KEY);
            for (KeyMapping k : SKILL_KEYS) e.register(k);
        }

        @SubscribeEvent
        public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
            e.enqueueWork(() -> net.minecraft.client.gui.screens.MenuScreens.register(rpg.core.equip.EquipMenu.TYPE.get(), EquipScreen::new));
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

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (!keysMoved) { keysMoved = true; moveVanillaKeys(mc); }
            if (mc.player != null && mc.player.tickCount % 100 == 1) killInnateKey(mc);
            boolean free = mc.player != null && mc.screen == null;
            while (STAT_KEY.consumeClick()) if (free) mc.setScreen(new StatScreen());
            while (SKILL_WINDOW_KEY.consumeClick()) if (free) mc.setScreen(new SkillScreen());
            while (PASSIVE_KEY.consumeClick()) if (free) mc.setScreen(new PassiveScreen());
            while (PARTY_KEY.consumeClick()) if (free) mc.setScreen(new PartyScreen());
            while (QUEST_KEY.consumeClick()) if (free) mc.setScreen(new QuestScreen());
            for (int i = 0; i < 6; i++) {
                if (wait[i] > 0) wait[i]--;
                boolean pressed = false;
                while (SKILL_KEYS[i].consumeClick()) pressed = true;
                if (pressed && free && wait[i] == 0) {
                    wait[i] = 4;
                    Net.CH.sendToServer(new Net.UseSkill(i));
                    var def = ClientData.slots[i] == null ? null : rpg.core.skill.Defs.SKILLS.get(ClientData.slots[i]);
                    if (def != null && def.charge()) charging[i] = true;
                }
                // 차지 스킬: 키를 떼면 발동
                if (charging[i] && !SKILL_KEYS[i].isDown()) {
                    charging[i] = false;
                    Net.CH.sendToServer(new Net.SkillRelease(i));
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
