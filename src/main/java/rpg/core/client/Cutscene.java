package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/**
 * 보스 등장 컷씬: 카메라가 플레이어 눈에서 보스 얼굴 앞으로 날아가 반 바퀴 돌고 돌아옴.
 * 그동안 이동·공격 막음, HUD 대신 위아래 검은 띠 + 보스 이름. 점프 키 = 건너뛰기.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class Cutscene {
    private Cutscene() {}

    private static int bossId = -1, total, tick;
    private static String name = "";
    private static String[] lines = new String[0];   // 보스 대사 ("|" 로 나눈 토막을 차례로)
    private static int burst, slamTick;               // 대사가 다 끝나는 틱 (변신) · 화면 흔들림 틱
    private static ArmorStand cam;
    private static Vec3 eye0;
    private static float yaw0, pitch0;
    private static float yawB, pitchB;   // 플레이어 눈에서 보스를 바라보는 각도 (끝나면 이 방향으로 고정)

    public static boolean active() { return cam != null; }

    public static void start(int entityId, int ticks, String bossName, String line, int burstAt, int slamAt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Entity boss = mc.level.getEntity(entityId);
        if (boss == null) return;
        bossId = entityId; total = Math.max(20, ticks); tick = 0; name = bossName; burst = burstAt; slamTick = slamAt;
        lines = line == null || line.isBlank() ? new String[0] : line.split("\\|");
        eye0 = mc.player.getEyePosition();
        yaw0 = mc.player.getYRot(); pitch0 = mc.player.getXRot();
        yawB = yaw0; pitchB = pitch0;
        cam = new ArmorStand(mc.level, eye0.x, eye0.y, eye0.z);
        place(eye0, yaw0, pitch0, true);
        mc.setCameraEntity(cam);
        freeMouse(true);
    }

    /**
     * 컷씬 동안 마우스 잡기를 풀어서 화면 돌리기 자체를 막음 (커서는 숨김).
     * 숄더 서핑 같은 3인칭 모드는 플레이어 각도와 따로 카메라를 돌려서, 각도를 되돌리는 것만으론 못 막음
     */
    private static void freeMouse(boolean free) {
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().getWindow();
        if (free) {
            if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();
            org.lwjgl.glfw.GLFW.glfwSetInputMode(win, org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
        } else if (mc.screen == null && !mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        boolean was = cam != null;
        if (cam != null && mc.player != null) mc.setCameraEntity(mc.player);
        cam = null; bossId = -1;
        if (was) freeMouse(false);
    }

    private static void place(Vec3 eye, float yaw, float pitch, boolean snap) {
        double eh = cam.getEyeHeight();
        if (snap) { cam.xo = eye.x; cam.yo = eye.y - eh; cam.zo = eye.z; cam.yRotO = yaw; cam.xRotO = pitch; }
        else { cam.xo = cam.getX(); cam.yo = cam.getY(); cam.zo = cam.getZ(); cam.yRotO = cam.getYRot(); cam.xRotO = cam.getXRot(); }
        cam.setPos(eye.x, eye.y - eh, eye.z);
        // 각도가 -180 ↔ 180 을 넘을 때 한 바퀴 휙 도는 것 방지
        float y = yaw;
        while (y - cam.yRotO > 180) y -= 360;
        while (y - cam.yRotO < -180) y += 360;
        cam.setYRot(y); cam.setXRot(pitch);
    }

    private static double ease(double f) { f = Mth.clamp(f, 0, 1); return f * f * (3 - 2 * f); }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || cam == null) return;
        Minecraft mc = Minecraft.getInstance();
        Entity boss = mc.level == null ? null : mc.level.getEntity(bossId);
        if (boss == null || mc.player == null || ++tick >= total || (tick > 10 && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), mc.options.keyJump.getKey().getValue()))) { stop(); return; }
        if (mc.mouseHandler.isMouseGrabbed()) freeMouse(true);   // 클릭해서 다시 잡혀도 다시 풂
        double f = tick / (double) total;
        double h = boss.getBbHeight();
        Vec3 focus = boss.position().add(0, h * 0.6, 0);
        double by = Math.toRadians(boss instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : boss.getYRot());
        double front = Math.atan2(Math.cos(by), -Math.sin(by));   // 보스 앞쪽 방향 각도 (x-z 평면)
        double r = Math.max(6, boss.getBbWidth() * 2.2);
        // 0~0.18 다가감 · 0.18~0.85 앞에서 반원 · 0.85~1 돌아옴
        double o = 0.18, b = 0.85;
        double oa = front - Math.toRadians(35) + Math.toRadians(70) * ease((f - o) / (b - o));
        double oh = h * (0.45 + 0.3 * ease((f - o) / (b - o)));
        Vec3 orbit = boss.position().add(Math.cos(oa) * r, oh, Math.sin(oa) * r);
        Vec3 pos;
        double lookMix;
        if (f < o) { double k = ease(f / o); pos = eye0.lerp(orbit, k); lookMix = k; }
        else if (f < b) { pos = orbit; lookMix = 1; }
        else { double k = ease((f - b) / (1 - b)); pos = orbit.lerp(mc.player.getEyePosition(), k); lookMix = 1; }   // 돌아올 때도 보스를 봄 → 끝나면 그 방향 그대로
        { Vec3 de = focus.subtract(mc.player.getEyePosition());
          yawB = (float) (Math.toDegrees(Math.atan2(de.z, de.x)) - 90);
          pitchB = (float) -Math.toDegrees(Math.atan2(de.y, Math.sqrt(de.x * de.x + de.z * de.z))); }
        // 컷씬 동안 눌린 키(평타·스킬·EF 공격)는 전부 버림
        for (net.minecraft.client.KeyMapping km : mc.options.keyMappings) { while (km.consumeClick()) {} }
        net.minecraft.client.KeyMapping.releaseAll();
        // 지팡이 내려찍기 (애니 3.05초) 순간 흔들림
        double slam = slamTick;
        if (tick > slam && tick < slam + 8) {
            double a = (slam + 8 - tick) / 8.0 * 0.25;
            pos = pos.add((Math.random() - 0.5) * a, (Math.random() - 0.5) * a, (Math.random() - 0.5) * a);
        }
        Vec3 d = focus.subtract(pos);
        float fy = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
        float fp = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        float py = yaw0, pp = pitch0;
        float yaw = py + Mth.wrapDegrees(fy - py) * (float) lookMix;
        float pitch = Mth.lerp((float) lookMix, pp, fp);
        place(pos, yaw, pitch, false);
    }

    /** 마우스로 고개 돌리기 막음 (컷씬 동안 플레이어 시점 고정) */
    @SubscribeEvent
    public static void lockLook(TickEvent.RenderTickEvent e) {
        if (cam == null || e.phase != TickEvent.Phase.START) return;
        var pl = Minecraft.getInstance().player;
        if (pl == null) return;
        pl.setYRot(yawB); pl.setXRot(pitchB); pl.yRotO = yawB; pl.xRotO = pitchB;
        pl.setYHeadRot(yawB); pl.yHeadRotO = yawB;
    }

    /** 틱 시작에도 키 입력 버림 (EF·스킬 키가 틱 중간에 읽기 전에) */
    @SubscribeEvent
    public static void eatKeys(TickEvent.ClientTickEvent e) {
        if (cam == null || e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        for (net.minecraft.client.KeyMapping km : mc.options.keyMappings) { while (km.consumeClick()) {} }
        net.minecraft.client.KeyMapping.releaseAll();
    }

    @SubscribeEvent
    public static void noMove(MovementInputUpdateEvent e) {
        if (cam == null) return;
        var in = e.getInput();
        in.forwardImpulse = 0; in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = in.jumping = in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void noAction(InputEvent.InteractionKeyMappingTriggered e) {
        if (cam != null) { e.setCanceled(true); e.setSwingHand(false); }
    }

    @SubscribeEvent
    public static void noHand(RenderHandEvent e) {
        if (cam != null) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void noHud(RenderGuiOverlayEvent.Pre e) {
        if (cam != null) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void draw(RenderGuiEvent.Post e) {
        if (cam == null) return;
        var g = e.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        int w = g.guiWidth(), h = g.guiHeight();
        double f = (tick + e.getPartialTick()) / total;
        int bar = (int) (h * 0.11 * ease(Math.min(f / 0.08, (1 - f) / 0.08)));
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
        // 이름: 0.3~0.9 사이에 나타났다 사라짐
        double a = Math.min(ease((f - 0.3) / 0.08), ease((0.9 - f) / 0.08));
        if (a > 0.02 && !name.isEmpty()) {
            int al = Math.max(8, (int) (a * 255)) << 24;
            g.pose().pushPose();
            g.pose().translate(w / 2f, h * 0.68f, 0);
            g.drawString(font, "— 보스 —", -font.width("— 보스 —") / 2, -14, al | 0xC79BFF, true);
            g.pose().scale(2.6f, 2.6f, 1);
            g.drawString(font, name, -font.width(name) / 2, 0, al | 0xFFFFFF, true);
            g.pose().popPose();
        }
        // 보스 대사: 토막마다 한 글자씩 나타났다가 다음 토막으로 (전체의 앞 90%)
        if (lines.length > 0) {
            // 대사는 변신(burst) 전까지 토막을 나눠 다 보여주고, 마지막 토막은 변신 뒤 조금 더 남음
            double end = burst > 0 ? Math.min(0.9, burst / (double) total) : 0.9, st0 = 0.03;
            double span = (end - st0) / lines.length;
            int i = (int) Math.max(0, Math.min(lines.length - 1, Math.floor((f - st0) / span)));
            double lf = (f - st0 - i * span) / span;
            double out = end + 0.12;
            String ln = lines[i].trim();
            int n = (int) Math.min(ln.length(), Math.ceil(ln.length() * Math.min(1, lf / 0.55)));
            double la = f < st0 ? 0 : f > out ? Math.max(0, 1 - (f - out) / 0.06) : 1;
            if (n > 0 && la > 0.02) {
                String shown = ln.substring(0, n);
                int al = Math.max(8, (int) (la * 255)) << 24;
                g.pose().pushPose();
                g.pose().translate(w / 2f, h - bar - 26, 0);
                g.pose().scale(1.5f, 1.5f, 1);
                g.drawString(font, shown, -font.width(ln) / 2, 0, al | 0xC79BFF, true);
                g.pose().popPose();
            }
        }
        String skip = "점프: 건너뛰기";
        g.drawString(font, skip, w - font.width(skip) - 8, h - bar / 2 - 4, 0x99FFFFFF, false);
    }
}
