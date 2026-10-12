package rpg.modcore;

import net.fabricmc.api.ClientModInitializer;

/**
 * RPG Mod Core — RPG 데이터팩 전용 클라이언트 모드.
 * 기능은 모듈별 클래스로 나눠서 여기서 등록한다. (새 기능 = 새 클래스 + 아래에 한 줄)
 */
public class RpgModCore implements ClientModInitializer {
    public static final String MOD_ID = "rpgmodcore";

    @Override
    public void onInitializeClient() {
        SkillKeys.init();   // 스킬 단축키 Q·E·R·F
    }
}
