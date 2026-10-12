package rpg.modcore;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * [RPG Mod Core] 스킬 단축키 모듈.
 * Q·E·R·F 를 누르면 서버에 "/trigger rpg_sk set 1~4" 를 보내고, 데이터팩이 그 스킬을 발동한다.
 * trigger 라서 권한(op)이 필요 없다. 같은 키 연타도 매번 전송된다.
 * 처음 실행할 때 한 번, 겹치는 바닐라 키를 옮긴다: 인벤토리 E→I, 버리기 Q→Z, 손 바꾸기 F→X
 */
public final class SkillKeys {
    /** 키를 꾹 누르고 있을 때 반복 전송 간격 (틱) */
    private static final int REPEAT_TICKS = 4;

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RpgModCore.MOD_ID, "skills"));

    private static final KeyMapping[] keys = new KeyMapping[4];
    private static final int[] wait = new int[4];

    private SkillKeys() {}

    public static void init() {
        int[] defaults = {InputConstants.KEY_Q, InputConstants.KEY_E, InputConstants.KEY_R, InputConstants.KEY_F};
        for (int i = 0; i < 4; i++) {
            keys[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.rpgmodcore.skill" + (i + 1),
                    defaults[i],
                    CATEGORY));
        }
        ClientLifecycleEvents.CLIENT_STARTED.register(SkillKeys::firstRunSetup);
        ClientTickEvents.END_CLIENT_TICK.register(SkillKeys::tick);
    }

    /** 처음 한 번만: 스킬 키와 겹치는 바닐라 키를 다른 키로 옮긴다 (사용자가 이미 바꾼 키는 건드리지 않음) */
    private static void firstRunSetup(Minecraft client) {
        Path flag = FabricLoader.getInstance().getConfigDir().resolve("rpgmodcore-keys-setup-done");
        if (Files.exists(flag)) return;
        Options o = client.options;
        move(o.keyInventory, "key.keyboard.e", "key.keyboard.i");
        move(o.keyDrop, "key.keyboard.q", "key.keyboard.z");
        move(o.keySwapOffhand, "key.keyboard.f", "key.keyboard.x");
        KeyMapping.resetMapping();
        o.save();
        try {
            Files.createDirectories(flag.getParent());
            Files.writeString(flag, "done");
        } catch (IOException ignored) {
        }
    }

    private static void move(KeyMapping mapping, String from, String to) {
        try {
            if (mapping.getDefaultKey().equals(InputConstants.getKey(from)) && mapping.isDefault()) {
                mapping.setKey(InputConstants.getKey(to));
            }
        } catch (RuntimeException ignored) {
            // 키 이름을 못 찾으면 그냥 두기 (게임이 멈추지 않게)
        }
    }

    private static void tick(Minecraft client) {
        for (int i = 0; i < 4; i++) {
            if (wait[i] > 0) wait[i]--;
            boolean pressed = false;
            while (keys[i].consumeClick()) pressed = true;
            if (!pressed) continue;
            if (client.player == null || client.getConnection() == null) continue;
            if (!client.mouseHandler.isMouseGrabbed()) continue;   // 채팅·인벤토리 등 창이 열려 있으면(마우스 풀림) 무시
            if (wait[i] > 0) continue;              // 꾹 누를 때 너무 자주 보내지 않게
            wait[i] = REPEAT_TICKS;
            client.getConnection().sendCommand("trigger rpg_sk set " + (i + 1));
        }
    }
}
