package rpg.core.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import rpg.core.RpgCore;

/**
 * 3인칭 카메라 기본값 (Shoulder Surfing Reloaded): 처음 한 번만 config/shouldersurfing-client.toml 에 우리 값을 넣음.
 * 런처가 config 파일을 못 받아서 모드가 직접 적용. 적용 기록(config/rpgcore-camera.txt)이 있으면 다시 안 건드림 (각자 바꾼 값 유지).
 * 모드 생성자에서 부름 → Shoulder Surfing 이 설정을 읽기 전에 적용됨.
 */
public final class CameraDefaults {
    private CameraDefaults() {}

    /** 값을 바꾸면 VERSION 을 올려야 이미 적용된 사람에게도 다시 한 번 적용됨 */
    private static final String VERSION = "2";   // 2: 카메라·몸 분리 끄기 (S = 뒷걸음)
    private static final String[][] VALUES = {{"offset_y", "1.5"}, {"offset_z", "6.0"}, {"max_offset_y", "3.0"}, {"max_offset_z", "8.0"}, {"decoupled_camera", "false"}};

    public static void apply() {
        try {
            Path dir = FMLPaths.CONFIGDIR.get();
            Path mark = dir.resolve("rpgcore-camera.txt");
            if (Files.exists(mark) && Files.readString(mark, StandardCharsets.UTF_8).trim().equals(VERSION)) return;
            Path cfg = dir.resolve("shouldersurfing-client.toml");
            if (Files.exists(cfg)) {
                String s = Files.readString(cfg, StandardCharsets.UTF_8);
                for (String[] kv : VALUES) s = s.replaceAll("(?m)^(\\s*" + kv[0] + "\\s*=\\s*)[-0-9.eEa-z]+", "$1" + kv[1]);
                if (!java.util.regex.Pattern.compile("(?m)^\\s*decoupled_camera\\s*=").matcher(s).find()) {   // 키가 없으면 [camera] 에 넣음 (윈도 줄바꿈도)
                    var m = java.util.regex.Pattern.compile("(?m)^\\[camera\\]\\r?\\n").matcher(s);
                    if (m.find()) s = s.substring(0, m.end()) + "\tdecoupled_camera = false\n" + s.substring(m.end());
                    else s = "[camera]\n\tdecoupled_camera = false\n\n" + s;
                }
                Files.writeString(cfg, s, StandardCharsets.UTF_8);
            } else {
                Files.createDirectories(dir);
                Files.writeString(cfg, "[camera]\n\tdecoupled_camera = false\n\n[camera.offset]\n\toffset_x = -0.75\n\toffset_y = 1.5\n\toffset_z = 6.0\n\n[camera.offset.max]\n"
                        + "\tmax_offset_x = 3.0\n\tmax_offset_y = 3.0\n\tmax_offset_z = 8.0\n", StandardCharsets.UTF_8);
            }
            Files.writeString(mark, VERSION, StandardCharsets.UTF_8);
            RpgCore.LOG.info("[rpgcore] 3인칭 카메라 기본값 적용 (Shoulder Surfing)");
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] 카메라 기본값 적용 실패: {}", ex.toString());
        }
    }
}
