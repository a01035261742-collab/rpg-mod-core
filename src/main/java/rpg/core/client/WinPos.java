package rpg.core.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * 창 옮기기 (메이플처럼): 제목줄을 끌면 창이 움직이고, 창 종류마다 위치를 config/rpgcore-windows.txt 에 기억.
 * 위치 = 가운데에서 얼마나 떨어졌나 (dx, dy). 캐릭터 창 8탭은 "char" 하나를 같이 씀.
 */
public final class WinPos {
    private WinPos() {}

    private static final Map<String, int[]> OFF = new HashMap<>();
    private static boolean loaded;
    /** 지금 열린 화면의 창 이름 */
    public static String cur = "";

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("rpgcore-windows.txt"); }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(file())) return;
            for (String l : Files.readAllLines(file())) {
                String[] p = l.trim().split("\\s+");
                if (p.length == 3) OFF.put(p[0], new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2])});
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        StringBuilder sb = new StringBuilder();
        for (var e : OFF.entrySet()) sb.append(e.getKey()).append(' ').append(e.getValue()[0]).append(' ').append(e.getValue()[1]).append('\n');
        try { Files.writeString(file(), sb.toString()); } catch (Exception ignored) {}
    }

    public static int[] off(String key) { load(); return OFF.computeIfAbsent(key, k -> new int[2]); }

    /** 화면 이름 → 창 이름 (캐릭터 창 탭은 모두 "char") */
    public static String keyOf(Object screen) {
        return screen instanceof StatScreen || screen instanceof SkillScreen || screen instanceof PassiveScreen || screen instanceof QuestScreen
                || screen instanceof PartyScreen || screen instanceof EnhanceScreen || screen instanceof DailyScreen || screen instanceof EquipScreen
                ? "char" : screen.getClass().getSimpleName();
    }

    /** 가운데 + 옮긴 만큼 (화면 밖으로 안 나가게) */
    public static int x(int screenW, int w) {
        int x = (screenW - w) / 2 + off(cur)[0];
        return Math.max(Ui.MARGIN, Math.min(screenW - w - Ui.MARGIN, x));
    }

    public static int y(int screenH, int h) {
        int y = (screenH - h) / 2 + off(cur)[1];
        return Math.max(Ui.MARGIN, Math.min(screenH - h - Ui.MARGIN, y));
    }

    // ───────── 끌기 ─────────
    /** 이번 화면에 그린 창들 {x, y, w, h} (제목줄 잡기 판정) */
    public static final java.util.List<int[]> RECTS = new java.util.ArrayList<>();
    public static boolean dragging;
    private static double sx, sy;
    private static int ox, oy;
    private static long lastClick;

    /** 제목줄(위 16칸)을 눌렀나 → 끌기 시작. 두 번 누르면 가운데로 */
    public static boolean press(double mx, double my) {
        for (int[] r : RECTS) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + 18) {
                long now = System.currentTimeMillis();
                int[] o = off(cur);
                if (now - lastClick < 300) { o[0] = 0; o[1] = 0; save(); lastClick = 0; return true; }
                lastClick = now;
                dragging = true; sx = mx; sy = my; ox = o[0]; oy = o[1];
                return true;
            }
        }
        return false;
    }

    /** 끄는 중: 위치 바꿈 → true 면 화면 다시 배치 */
    public static boolean drag(double mx, double my) {
        if (!dragging) return false;
        int[] o = off(cur);
        int nx = ox + (int) Math.round(mx - sx), ny = oy + (int) Math.round(my - sy);
        if (nx == o[0] && ny == o[1]) return false;
        o[0] = nx; o[1] = ny;
        return true;
    }

    public static boolean release() {
        if (!dragging) return false;
        dragging = false;
        save();
        return true;
    }
}
