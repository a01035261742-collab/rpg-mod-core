package rpg.core.client.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import rpg.core.net.Net;

/** 근접 검기: 칼이 지나간 길(미리 뽑은 경로)을 공속 배율대로 따라가며, 먼저 지나간 쪽부터 사라짐 */
final class Melee extends SkillFx.Fx {
    static JsonObject DATA;
    static final double LIFE = 0.14, STAB = 0.06;

    final int ent; final double scale; final Vec3 fw, rt; Vec3 anchor;
    final float[][][] win; final Vec3[][][] wfix;   // 구간 · 점 · (칼 시작, 칼 끝)
    final float[][] stab; final Vec3[][] sfix;      // 찌르기: 시각 · 손잡이 · 방향
    final JsonObject area; Vec3 areaC; final double endT;

    static void start(Net.Fx f) {
        JsonObject o = SkillFx.trails().getAsJsonObject(f.s());
        if (o == null) return;
        SkillFx.add(new Melee(f, o));
    }

    Melee(Net.Fx f, JsonObject o) {
        ent = f.e();
        double len = o.has("len") ? o.get("len").getAsDouble() : 1, mul = f.dx(), atk = f.dy();
        double rate = atk <= 0 ? 1 : mul > 0 ? atk * mul / rpg.core.compat.EpicFightCompat.BASE_ATTACK_SPEED : len * atk;   // 에픽파이트와 같은 식
        scale = rate > 0.05 ? rate : 1;
        double yaw = Math.toRadians(f.b());
        fw = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)); rt = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        anchor = new Vec3(f.x(), f.y(), f.z());
        JsonArray w = o.getAsJsonArray("w");
        win = new float[w.size()][][]; wfix = new Vec3[w.size()][][];
        double last = 0;
        for (int i = 0; i < w.size(); i++) {
            JsonArray s = w.get(i).getAsJsonArray();
            win[i] = new float[s.size()][]; wfix[i] = new Vec3[s.size()][];
            for (int k = 0; k < s.size(); k++) { win[i][k] = SkillFx.arr(s.get(k).getAsJsonArray()); last = Math.max(last, win[i][k][0]); }
        }
        JsonArray st = o.getAsJsonArray("s");
        stab = new float[st.size()][]; sfix = new Vec3[st.size()][];
        for (int i = 0; i < st.size(); i++) { stab[i] = SkillFx.arr(st.get(i).getAsJsonArray()); last = Math.max(last, stab[i][0] + STAB); }
        area = o.has("a") && o.get("a").isJsonObject() ? o.getAsJsonObject("a") : null;
        if (area != null) last = Math.max(last, area.get("t").getAsDouble() + 0.3);
        endT = last + LIFE + 0.05;
    }

    Vec3 world(double x, double y, double z) { return anchor.add(rt.scale(x)).add(0, y, 0).add(fw.scale(z)); }

    boolean draw(SkillFx.D d, double t) {
        double tl = t * scale;
        if (tl > endT) return false;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(ent);
        if (e != null) anchor = d.ent(e);
        VertexConsumer vc = d.add("trail");
        // 휘두르기
        for (int i = 0; i < win.length; i++) {
            float[][] s = win[i]; Vec3[][] fx = wfix[i]; int n = s.length - 1;
            for (int k = 0; k <= n; k++) if (fx[k] == null && s[k][0] <= tl) fx[k] = new Vec3[]{world(s[k][1], s[k][2], s[k][3]), world(s[k][4], s[k][5], s[k][6])};
            for (int k = 0; k < n; k++) {
                if (fx[k] == null || fx[k + 1] == null || s[k][0] < tl - LIFE) continue;
                float u0 = (float) k / n, u1 = (float) (k + 1) / n;
                d.quad(vc, fx[k][0], fx[k + 1][0], fx[k + 1][1], fx[k][1], u0, 0, u1, 1, 0xFFFFFF, 1f, true);
            }
        }
        // 찌르기 섬광: 손잡이에서 칼 방향으로 2.6칸, 0.06초 동안 뻗어 나감
        for (int i = 0; i < stab.length; i++) {
            float[] s = stab[i];
            if (tl < s[0]) continue;
            if (sfix[i] == null) {
                Vec3 o = world(s[1], s[2], s[3]), dd = world(s[4], s[5], s[6]).subtract(anchor).normalize();
                sfix[i] = new Vec3[]{o, dd};
            }
            Vec3 o = sfix[i][0], dir = sfix[i][1];
            Vec3 side = new Vec3(0, 1, 0).cross(dir); side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = dir.cross(side).normalize();
            int n = 24;
            for (Vec3 ax : new Vec3[]{side, up}) for (int k = 0; k < n; k++) {
                double f0 = (double) k / n, f1 = (double) (k + 1) / n, t0 = s[0] + STAB * f0, t1 = s[0] + STAB * f1;
                if (t1 > tl || t0 < tl - LIFE) continue;
                double w0 = 0.22 * (0.35 + 0.65 * Math.sin(Math.PI * Math.min(1, f0 * 1.15))), w1 = 0.22 * (0.35 + 0.65 * Math.sin(Math.PI * Math.min(1, f1 * 1.15)));
                Vec3 c0 = o.add(dir.scale(0.05 + 2.6 * f0)), c1 = o.add(dir.scale(0.05 + 2.6 * f1));
                d.quad(vc, c0.subtract(ax.scale(w0)), c1.subtract(ax.scale(w1)), c1.add(ax.scale(w1)), c0.add(ax.scale(w0)), (float) f0, 0, (float) f1, 1, 0xFFFFFF, 1f, true);
            }
        }
        // 충격파 (강타 앞 180° · 도약 내려찍기 원)
        if (area != null) {
            double at = area.get("t").getAsDouble();
            if (tl >= at) {
                if (areaC == null) { float[] c = SkillFx.arr(area.getAsJsonArray("c")); areaC = world(c[0], 0, c[2]).add(0, 0.05, 0); }
                double k = (tl - at) / 0.3;
                if (k <= 1) {
                    double R = area.get("r").getAsDouble(), r = 0.3 + (R - 0.3) * k;
                    boolean circle = "circle".equals(area.get("k").getAsString());
                    double deg = circle ? 360 : area.get("deg").getAsDouble(), face = Math.toDegrees(Math.atan2(fw.z, fw.x));
                    d.arc("white", areaC, r, 0.82, face - deg / 2, deg, 0xD8DCF0, (float) (1 - k * 0.5));
                }
            }
        }
        return true;
    }
}
