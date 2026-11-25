package rpg.core.client.fx;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import rpg.core.RpgCore;
import rpg.core.net.Net;

/**
 * 스킬 이펙트 (도트 텍스처 + 더하기 빛). 서버가 보낸 Net.Fx 를 받아 그림.
 * 근접 검기는 에픽파이트 모션에서 미리 뽑은 칼 경로(assets/rpgcore/fx/trails.json)를 공속 배율대로 따라 그림.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class SkillFx {
    private SkillFx() {}

    /** 다른 사람 이펙트 보기 (/rpgfx 로 끄기) */
    public static boolean others = true;
    private static final int MAX = 96;
    private static final double FAR = 48;
    private static final List<Fx> LIST = new ArrayList<>();
    private static final Map<Integer, Shot> SHOTS = new HashMap<>();
    private static final Map<Integer, Field> FIELDS = new HashMap<>();
    private static final Map<String, Fx> AURAS = new HashMap<>();
    static final Random RND = new Random();

    static double now() { return System.nanoTime() / 1e9; }

    /** 이펙트 하나: draw 가 false 면 끝 */
    abstract static class Fx {
        final double t0 = now();
        boolean done;
        abstract boolean draw(D d, double t);
    }

    // ───────── 받기 ─────────
    public static void receive(Net.Fx f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 at = new Vec3(f.x(), f.y(), f.z());
        if (mc.player.position().distanceTo(at) > FAR + 16) return;
        boolean mine = f.e() == mc.player.getId();
        switch (f.k()) {
            case "melee" -> { if (mine || others) Melee.start(f); }
            case "cast" -> { if (mine || others) Casts.start(f); }
            case "shot" -> {
                String[] s = f.s().split("\\|");
                int owner = s.length > 1 ? parse(s[1]) : -1;
                if (owner != mc.player.getId() && !others) return;
                Shot sh = new Shot(f.e(), s[0], at, new Vec3(f.dx(), f.dy(), f.dz()), f.a(), f.b(), mc.level.getEntity(owner));
                SHOTS.put(f.e(), sh); add(sh);
            }
            case "shotEnd" -> {
                Shot sh = SHOTS.remove(f.e());
                if (sh != null) sh.done = true;
                switch (f.s()) {
                    case "fire" -> add(new Boom(at, f.a() > 0 ? f.a() : 3, false));
                    case "holy" -> add(new Pop("holy", at, 1.2, 0.35, true, 0xFFFFFF));
                    default -> add(new Pop("hit", at, 1.0, 0.25, true, 0xFFFFFF));
                }
            }
            case "flash" -> {
                switch (f.s()) {
                    case "holy" -> add(new Pop("holy", at, 1.5, 0.25, true, 0xFFFFFF));
                    case "claw0", "claw1" -> { add(new Claw(at, f.s().endsWith("1"))); add(new Pop("star", at, 1.6, 0.3, true, 0xFFFFFF)); }
                    default -> add(new Pop("hit", at, 1.1, 0.25, true, 0xFFFFFF));
                }
            }
            case "boom" -> add(new Boom(at, f.a(), "shadow".equals(f.s())));
            case "shock" -> add(new Shock(at, f.a(), f.s().equals("circle") ? 360 : parseD(f.s()), f.b()));
            case "sblade" -> add(new Blade(at, new Vec3(f.dx(), f.dy(), f.dz())));
            case "chain" -> add(new Chain(f.s()));
            case "field" -> { Field fl = new Field(f.s(), at, f.a(), f.b()); FIELDS.put(f.e(), fl); add(fl); }
            case "fieldSnap" -> { Field fl = FIELDS.get(f.e()); if (fl != null) fl.snap = now(); }
            case "fieldEnd" -> { Field fl = FIELDS.remove(f.e()); if (fl != null) fl.end = now(); }
            default -> {}
        }
    }

    static int parse(String s) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return -1; } }
    static double parseD(String s) { try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 360; } }

    /** 충격파: 서버가 실제로 친 순간 · 바닥 고리가 범위까지 퍼짐 */
    static final class Shock extends Fx {
        final Vec3 c; final double r, deg, face;
        Shock(Vec3 c, double r, double deg, double yaw) {
            this.c = c.add(0, 0.05, 0); this.r = r; this.deg = deg;
            double y = Math.toRadians(yaw); this.face = Math.toDegrees(Math.atan2(Math.cos(y), -Math.sin(y)));
        }
        boolean draw(D d, double t) {
            double k = t / 0.3;
            if (k > 1) return false;
            d.arc("white", c, 0.3 + (r - 0.3) * k, 0.82, face - deg / 2, deg, 0xD8DCF0, (float) (1 - k * 0.5));
            return true;
        }
    }

    static void add(Fx fx) {
        if (LIST.size() >= MAX) LIST.remove(0);
        LIST.add(fx);
    }

    /** 오래 가는 버프 표시 (같은 사람·같은 버프면 새 걸로 바꿈) */
    static void aura(String key, Fx fx) {
        Fx old = AURAS.put(key, fx);
        if (old != null) old.done = true;
        add(fx);
    }

    /** 보스 바닥 예고가 다 차서 터지는 순간 (Telegraphs 에서 부름) */
    public static void burst(Net.Telegraph t, float y) {
        add(new Burst(t, y));
    }

    // ───────── 그리기 ─────────
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIST.clear(); SHOTS.clear(); FIELDS.clear(); AURAS.clear(); return; }
        D d = new D(mc, e.getCamera(), e.getPoseStack(), e.getPartialTick());
        double now = now();
        Iterator<Fx> it = LIST.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            boolean keep;
            try { keep = !fx.done && fx.draw(d, now - fx.t0); }
            catch (Exception ex) { keep = false; RpgCore.LOG.debug("[rpgcore] 이펙트 오류 {}", ex.toString()); }
            if (!keep) it.remove();
        }
        AURAS.values().removeIf(f -> f.done || !LIST.contains(f));
        d.buf.endBatch();
    }

    /** 그리기 도구: 카메라 기준 좌표 · 빌보드 · 띠 · 바닥 */
    static final class D {
        final Minecraft mc; final Vec3 cam; final Matrix4f m; final org.joml.Matrix3f nm; final MultiBufferSource.BufferSource buf; final float pt;
        final Vector3f left, up;
        D(Minecraft mc, Camera c, PoseStack ps, float pt) {
            this.mc = mc; this.cam = c.getPosition(); this.m = ps.last().pose(); this.nm = new org.joml.Matrix3f(ps.last().normal()); this.buf = mc.renderBuffers().bufferSource(); this.pt = pt;
            this.left = c.getLeftVector(); this.up = c.getUpVector();
        }
        static ResourceLocation tex(String n) { return new ResourceLocation(RpgCore.MODID, "textures/fx/" + n + ".png"); }
        VertexConsumer add(String tex) { return buf.getBuffer(RenderType.eyes(tex(tex))); }
        VertexConsumer norm(String tex) { return buf.getBuffer(RenderType.entityTranslucent(tex(tex))); }
        Vec3 ent(Entity en) { return en.getPosition(pt); }

        void v(VertexConsumer vc, double x, double y, double z, float u, float vv, int rgb, float a, boolean additive) {
            float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
            if (additive) { r *= a; g *= a; b *= a; a = 1; }
            vc.vertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).color(r, g, b, a).uv(u, vv)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 1, 0).endVertex();
        }
        /** 네 점 사각형 (양면) */
        void quad(VertexConsumer vc, Vec3 a, Vec3 b, Vec3 c, Vec3 e, float u0, float v0, float u1, float v1, int rgb, float al, boolean addv) {
            v(vc, a.x, a.y, a.z, u0, v1, rgb, al, addv); v(vc, b.x, b.y, b.z, u1, v1, rgb, al, addv);
            v(vc, c.x, c.y, c.z, u1, v0, rgb, al, addv); v(vc, e.x, e.y, e.z, u0, v0, rgb, al, addv);
            v(vc, e.x, e.y, e.z, u0, v0, rgb, al, addv); v(vc, c.x, c.y, c.z, u1, v0, rgb, al, addv);
            v(vc, b.x, b.y, b.z, u1, v1, rgb, al, addv); v(vc, a.x, a.y, a.z, u0, v1, rgb, al, addv);
        }
        /** 화면을 보는 판 */
        void bill(String tex, boolean addv, Vec3 p, double size, double rot, int rgb, float al) {
            if (size <= 0.001 || al <= 0.002) return;
            double h = size / 2, cs = Math.cos(rot), sn = Math.sin(rot);
            Vec3 L = new Vec3(left.x(), left.y(), left.z()), U = new Vec3(up.x(), up.y(), up.z());
            Vec3 ax = L.scale(cs).add(U.scale(sn)).scale(h), ay = U.scale(cs).subtract(L.scale(sn)).scale(h);
            VertexConsumer vc = addv ? add(tex) : norm(tex);
            quad(vc, p.add(ax).subtract(ay), p.subtract(ax).subtract(ay), p.subtract(ax).add(ay), p.add(ax).add(ay), 0, 0, 1, 1, rgb, al, addv);
        }
        /** 화면을 보는 판 (가로·세로 따로) */
        void billWH(String tex, boolean addv, Vec3 p, double w, double hh, int rgb, float al) {
            if (w <= 0.001 || al <= 0.002) return;
            Vec3 L = new Vec3(left.x(), left.y(), left.z()).scale(w / 2), U = new Vec3(up.x(), up.y(), up.z()).scale(hh / 2);
            VertexConsumer vc = addv ? add(tex) : norm(tex);
            quad(vc, p.add(L).subtract(U), p.subtract(L).subtract(U), p.subtract(L).add(U), p.add(L).add(U), 0, 0, 1, 1, rgb, al, addv);
        }
        /** 바닥에 누운 판 (중심, 반지름) */
        void flat(String tex, boolean addv, Vec3 c, double r, int rgb, float al) {
            if (r <= 0.001 || al <= 0.002) return;
            VertexConsumer vc = addv ? add(tex) : norm(tex);
            double y = c.y;
            quad(vc, new Vec3(c.x - r, y, c.z + r), new Vec3(c.x + r, y, c.z + r), new Vec3(c.x + r, y, c.z - r), new Vec3(c.x - r, y, c.z - r), 0, 0, 1, 1, rgb, al, addv);
        }
        /** 방향 d 로 누운 막대 판 두 장 (십자) — 화살·단검 */
        void stick(String tex, boolean addv, Vec3 c, Vec3 dir, double len, double w, int rgb, float al) {
            Vec3 f = dir.normalize(), s = f.cross(new Vec3(0, 1, 0));
            if (s.lengthSqr() < 1e-4) s = new Vec3(1, 0, 0);
            s = s.normalize(); Vec3 u = s.cross(f).normalize();
            VertexConsumer vc = addv ? add(tex) : norm(tex);
            Vec3 a = c.subtract(f.scale(len / 2)), b = c.add(f.scale(len / 2));
            for (Vec3 side : new Vec3[]{s, u}) {
                Vec3 o = side.scale(w / 2);
                quad(vc, a.subtract(o), b.subtract(o), b.add(o), a.add(o), 0, 0, 1, 1, rgb, al, addv);
            }
        }
        /** 세로로 선 판 한 장, 늘 화면 쪽을 봄 (몸 테두리) */
        void cyl(String tex, boolean addv, Vec3 base, double w, double h, int rgb, float al) {
            if (al <= 0.002) return;
            Vec3 r = new Vec3(left.x(), 0, left.z());
            r = r.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : r.normalize().scale(w / 2);
            VertexConsumer vc = addv ? add(tex) : norm(tex);
            quad(vc, base.add(r), base.subtract(r), base.subtract(r).add(0, h, 0), base.add(r).add(0, h, 0), 0, 0, 1, 1, rgb, al, addv);
        }
        /** 세로로 선 판 두 장 (기둥) */
        void pillar(String tex, boolean addv, Vec3 base, double w, double h, int rgb, float al) {
            VertexConsumer vc = addv ? add(tex) : norm(tex);
            for (int k = 0; k < 2; k++) {
                double dx = k == 0 ? w / 2 : 0, dz = k == 0 ? 0 : w / 2;
                quad(vc, base.add(-dx, 0, -dz), base.add(dx, 0, dz), base.add(dx, h, dz), base.add(-dx, h, -dz), 0, 0, 1, 1, rgb, al, addv);
            }
        }
        /** 바닥 고리 띠 (안 반지름 비율, 각도 범위) — 충격파·터짐 */
        void arc(String tex, Vec3 c, double r, double inner, double fromDeg, double spanDeg, int rgb, float al) {
            if (r <= 0.01 || al <= 0.002) return;
            VertexConsumer vc = add(tex);
            int n = Math.max(8, (int) (spanDeg / 7.5));
            for (int i = 0; i < n; i++) {
                double a0 = Math.toRadians(fromDeg + spanDeg * i / n), a1 = Math.toRadians(fromDeg + spanDeg * (i + 1) / n);
                Vec3 o0 = new Vec3(Math.cos(a0), 0, Math.sin(a0)), o1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
                quad(vc, c.add(o0.scale(r * inner)), c.add(o1.scale(r * inner)), c.add(o1.scale(r)), c.add(o0.scale(r)), 0, 0, 1, 1, rgb, al, true);
            }
        }
        /** 바닥 부채꼴/원 채우기 */
        void fan(String tex, Vec3 c, double r, double fromDeg, double spanDeg, int rgb, float al) {
            if (r <= 0.01 || al <= 0.002) return;
            VertexConsumer vc = add(tex);
            int n = Math.max(8, (int) (spanDeg / 7.5));
            for (int i = 0; i < n; i++) {
                double a0 = Math.toRadians(fromDeg + spanDeg * i / n), a1 = Math.toRadians(fromDeg + spanDeg * (i + 1) / n);
                Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
                quad(vc, c, c, p1, p0, 0, 0, 1, 1, rgb, al, true);
            }
        }
        int light(Vec3 p) { return mc.level == null ? 0xF000F0 : LevelRenderer.getLightColor(mc.level, BlockPos.containing(p)); }
    }

    // ───────── 공통 이펙트 ─────────
    /** 커졌다 작아지는 번쩍임 */
    static final class Pop extends Fx {
        final String tex; final Vec3 p; final double size, life; final boolean add; final int rgb;
        Pop(String tex, Vec3 p, double size, double life, boolean add, int rgb) { this.tex = tex; this.p = p; this.size = size; this.life = life; this.add = add; this.rgb = rgb; }
        boolean draw(D d, double t) {
            double k = t / life;
            if (k >= 1) return false;
            double s = k < 0.25 ? k / 0.25 * size : size * (1 - (k - 0.25) / 0.75);
            d.bill(tex, add, p, s, 0, rgb, 1f);
            return true;
        }
    }

    /** 폭발: 6장 불꽃 + 바닥 링 + 튀는 불씨 */
    static final class Boom extends Fx {
        final Vec3 c; final double r; final boolean shadow; final double[][] em = new double[14][3];
        Boom(Vec3 c, double r, boolean shadow) {
            this.c = c; this.r = r; this.shadow = shadow;
            for (int i = 0; i < em.length; i++) { double a = i * Math.PI * 2 / em.length; double v = 2 + RND.nextDouble() * 2; em[i] = new double[]{Math.cos(a) * v, Math.sin(a) * v, 3 + RND.nextDouble() * 3}; }
        }
        boolean draw(D d, double t) {
            if (t > 0.8) return false;
            double b = t / 0.5;
            if (b < 1) {
                int f = Math.min(5, (int) (b * 6));
                double s = (0.75 + 1.25 * Math.min(1, b * 2)) * r;
                d.bill((shadow ? "pboom" : "boom") + f, true, c.add(0, 0.6, 0), s, 0, 0xFFFFFF, 1f);
            }
            double k = t / 0.35;
            if (k < 1) d.flat("ring_fire", true, c.add(0, 0.05, 0), (0.5 + (2 * r - 0.5) * k) / 2, shadow ? 0xA070FF : 0xFFFFFF, (float) (1 - k));
            if (!shadow && t < 0.7) for (double[] e : em) {
                double s = 0.3 * (1 - t / 0.7);
                d.bill("orb", true, c.add(e[0] * t * r / 4, 0.4 + e[2] * t - 9 * t * t - 0.5, e[1] * t * r / 4), s, 0, 0xFFFFFF, 1f);
            }
            return true;
        }
    }

    /** 매직 클로 할퀸 자국 (X 자) */
    static final class Claw extends Fx {
        final Vec3 c; final boolean second;
        Claw(Vec3 c, boolean second) { this.c = c; this.second = second; }
        boolean draw(D d, double t) {
            double k = t / 0.35;
            if (k >= 1) return false;
            double sc = (k < 0.2 ? k / 0.2 : 1) * 1.4, sh = 1 - Math.max(0, k - 0.6) / 0.4;
            Vec3 L = new Vec3(d.left.x(), d.left.y(), d.left.z()), U = new Vec3(d.up.x(), d.up.y(), d.up.z());
            double rot = second ? -0.8 : 0.8, cs = Math.cos(rot), sn = Math.sin(rot);
            Vec3 ax = L.scale(cs).add(U.scale(sn)).scale(sc / 2), ay = U.scale(cs).subtract(L.scale(sn)).scale(sc / 4 * sh);
            d.quad(d.add("claw"), c.add(ax).subtract(ay), c.subtract(ax).subtract(ay), c.subtract(ax).add(ay), c.add(ax).add(ay), 0, 0, 1, 1, 0xFFFFFF, 1f, true);
            return true;
        }
    }

    /** 투사체 (서버 경로를 그대로 따라감, 처음 0.15초는 몸 앞 가운데에서 합류) */
    static final class Shot extends Fx {
        final int id; final String kind; final Vec3 start, dir, off; final double speed, life;
        Shot(int id, String kind, Vec3 start, Vec3 dir, double speed, double range, Entity owner) {
            this.id = id; this.kind = kind; this.start = start; this.dir = dir.normalize(); this.speed = speed * 20;
            this.life = range / Math.max(0.01, speed * 20) + 0.3;
            Vec3 o = Vec3.ZERO;
            if (owner != null) {
                Vec3 look = owner.getLookAngle(); Vec3 flat = new Vec3(look.x, 0, look.z);
                flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
                o = owner.position().add(flat.scale(1.5)).add(0, 1.0, 0).subtract(start);
            }
            this.off = o;
        }
        Vec3 at(double t) { return start.add(dir.scale(speed * t)).add(off.scale(Math.max(0, 1 - t / 0.15))); }
        boolean draw(D d, double t) {
            if (t > life) { SHOTS.remove(id); return false; }
            Vec3 p = at(t);
            switch (kind) {
                case "fire" -> {
                    double fl = 0.85 + 0.15 * Math.sin(t * 90);
                    d.bill("orb", true, p, 0.9 * fl, 0, 0xFFFFFF, 1f);
                    for (int i = 1; i <= 8; i++) {
                        double tt = t - i * 0.025;
                        if (tt < 0) break;
                        d.bill("orb", true, at(tt), 0.6 * (1 - i / 9.0), 0, 0xFFFFFF, 0.8f);
                    }
                }
                case "holy" -> { d.stick("holy", true, p, dir, 2.2, 0.22, 0xFFFFFF, 1f); d.bill("holy", true, p, 0.8, 0, 0xFFFFFF, 1f); }
                default -> d.stick("knife", false, p, dir, 0.9, 0.3, 0xFFFFFF, 1f);
            }
            return true;
        }
    }

    /** 그림자 칼날: 보라 단검 + 잔상 */
    static final class Blade extends Fx {
        final Vec3 a, b; final double fly;
        Blade(Vec3 a, Vec3 b) { this.a = a; this.b = b; this.fly = a.distanceTo(b) / 28; }
        boolean draw(D d, double t) {
            double k = t / fly;
            if (k > 1 + 0.25 / fly) return false;
            Vec3 dir = b.subtract(a);
            if (k < 1) {
                d.stick("sknife", false, a.add(dir.scale(k)), dir, 0.9, 0.3, 0xFFFFFF, 1f);
                for (int i = 1; i <= 6; i++) { double kk = k - i * 0.06; if (kk < 0) break; d.bill("mist", true, a.add(dir.scale(kk)), 0.35 * (1 - i / 6.0), 0, 0x8A4AFF, 1f); }
            } else {
                double h = (t - fly) / 0.25, s = h < 0.25 ? h / 0.25 * 1.2 : 1.2 * (1 - (h - 0.25) / 0.75);
                d.bill("hit", true, b, s, 0, 0xB08AFF, 1f);
            }
            return true;
        }
    }

    /** 체인 라이트닝: 점에서 점으로 지그재그 번개 */
    static final class Chain extends Fx {
        final List<Vec3> pts = new ArrayList<>();
        Chain(String s) {
            for (String p : s.split(";")) {
                String[] c = p.split(",");
                if (c.length == 3) try { pts.add(new Vec3(Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]))); } catch (Exception ignored) {}
            }
        }
        boolean draw(D d, double t) {
            if (pts.size() < 2 || t > pts.size() * 0.09 + 0.4) return false;
            for (int i = 0; i + 1 < pts.size(); i++) {
                double k = (t - i * 0.09) / 0.25;
                if (k >= 0 && k < 1) {
                    Vec3 a = pts.get(i), b = pts.get(i + 1);
                    long seed = (long) (t * 30) + i * 7L;
                    Random r = new Random(seed);
                    Vec3 prev = a; double w = 0.09 * (1 - k * 0.6);
                    VertexConsumer vc = d.add("bolt");
                    for (int j = 1; j <= 10; j++) {
                        Vec3 p = a.add(b.subtract(a).scale(j / 10.0));
                        if (j < 10) p = p.add((r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.5);
                        Vec3 seg = p.subtract(prev), side = seg.cross(p.subtract(d.cam)).normalize().scale(w);
                        d.quad(vc, prev.subtract(side), p.subtract(side), p.add(side), prev.add(side), 0, 0, 1, 1, 0xFFFFFF, 1f, true);
                        prev = p;
                    }
                }
                double h = (t - i * 0.09) / 0.3;
                if (h >= 0 && h < 1) d.bill("star", true, pts.get(i + 1), h < 0.25 ? h / 0.25 * 1.4 : 1.4 * (1 - (h - 0.25) / 0.75), 0, 0xFFFFFF, 1f);
            }
            return true;
        }
    }

    /** 장판: 곰덫(블록 모델) · 독안개 */
    static final class Field extends Fx {
        final String kind; final Vec3 c; final double r, dur; double snap = -1, end = -1;
        final double[][] puffs;
        Field(String kind, Vec3 c, double r, double dur) {
            this.kind = kind; this.c = c; this.r = r; this.dur = dur;
            puffs = new double[kind.equals("mist") ? 40 : 0][5];
            for (double[] p : puffs) { double a = RND.nextDouble() * Math.PI * 2, rr = Math.sqrt(RND.nextDouble()) * r * 0.93; p[0] = Math.cos(a) * rr; p[1] = Math.sin(a) * rr; p[2] = RND.nextDouble() * 6; p[3] = 1.1 + RND.nextDouble() * 0.8; p[4] = RND.nextDouble(); }
        }
        boolean draw(D d, double t) {
            double n = now();
            if (kind.equals("trap")) {
                if (snap > 0 && n - snap > 1.0) return false;
                if (snap < 0 && end > 0) return false;
                if (t > dur + 2) return false;
                Trap.draw(d, c, Math.min(1, 0.3 + 0.7 * t / 0.12), snap < 0 ? 0 : (n - snap));
                if (snap > 0) { double h = (n - snap) / 0.25; if (h < 1) d.bill("hit", true, c.add(0, 0.5, 0), h < 0.25 ? h / 0.25 * 1.3 : 1.3 * (1 - (h - 0.25) / 0.75), 0, 0xFFFFFF, 1f); }
                return true;
            }
            // 독안개
            double out = end > 0 ? (n - end) / 0.4 : Math.max(0, (t - dur) / 0.4);
            if (out >= 1) return false;
            double k = Math.min(1, t / 0.4);
            for (double[] p : puffs) {
                double sc = p[3] * k * (1 - out) * (0.85 + 0.15 * Math.sin(t * 3 + p[2]));
                double y = (0.3 + p[4] * 1.3) * k + ((t * 0.25 + p[2]) % 1) * 0.2;
                d.bill("mist", true, c.add(p[0] * (0.6 + 0.4 * k), y, p[1] * (0.6 + 0.4 * k)), sc, 0, 0xFFFFFF, 0.7f);
            }
            return true;
        }
    }

    /** 보스 예고 터짐: 같은 모양 섬광 → 주황 열기 → 가장자리 밀려남 → 불똥 */
    static final class Burst extends Fx {
        final Net.Telegraph t; final float y; final double[][] sp = new double[26][3];
        Burst(Net.Telegraph t, float y) {
            this.t = t; this.y = y;
            int base = t.shape() % 10;
            double deg = base == 2 ? t.b() : 360, face = Math.toDegrees(Math.atan2(t.dz(), t.dx()));
            for (double[] s : sp) {
                double a = Math.toRadians(face - deg / 2 + RND.nextDouble() * deg), r = (0.4 + 0.6 * RND.nextDouble()) * t.a();
                s[0] = Math.cos(a) * r; s[1] = Math.sin(a) * r; s[2] = 2 + RND.nextDouble() * 3;
            }
        }
        boolean draw(D d, double tt) {
            double b = tt / 0.45;
            if (b > 1) return false;
            int base = t.shape() % 10;
            Vec3 c = new Vec3(t.x(), y + 0.03, t.z());
            double deg = base == 2 ? t.b() : 360, face = Math.toDegrees(Math.atan2(t.dz(), t.dx())), from = face - deg / 2;
            d.fan("white", c, t.a(), from, deg, 0xFFFFFF, (float) Math.max(0, 1 - b * 3.2));
            d.fan("white", c.add(0, -0.01, 0), t.a(), from, deg, 0xFF9040, (float) (Math.max(0, 0.85 - b) * 0.9));
            double s = 1 + 0.12 * b;
            d.arc("white", c.add(0, 0.01, 0), t.a() * s, 0.93, from, deg, 0xFFF0D0, (float) (1 - b));
            double tm = b * 0.45;
            if (b < 0.8) for (double[] p : sp) d.bill("white", true, new Vec3(t.x() + p[0] * (1 + b * 0.15), y + 0.1 + p[2] * tm - 9 * tm * tm, t.z() + p[1] * (1 + b * 0.15)), 0.16 * (1 - b), 0, 0xFFE0A0, 1f);
            return true;
        }
    }

    static void reload() { Melee.DATA = null; }

    /** /rpgfx : 다른 사람 스킬 이펙트 켜기·끄기 */
    @SubscribeEvent
    public static void cmd(net.minecraftforge.client.event.RegisterClientCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("rpgfx").executes(c -> {
            others = !others;
            c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("다른 사람 스킬 이펙트: " + (others ? "켜짐" : "꺼짐")), false);
            return 1;
        }));
    }

    /** 근접 검기 데이터 */
    static JsonObject trails() {
        if (Melee.DATA != null) return Melee.DATA;
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(RpgCore.MODID, "fx/trails.json"));
            if (res.isPresent()) try (var rd = res.get().openAsReader()) { Melee.DATA = JsonParser.parseReader(rd).getAsJsonObject(); }
        } catch (Exception ex) { RpgCore.LOG.warn("[rpgcore] 검기 데이터 못 읽음: {}", ex.toString()); }
        if (Melee.DATA == null) Melee.DATA = new JsonObject();
        return Melee.DATA;
    }

    static float[] arr(JsonArray a) { float[] f = new float[a.size()]; for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat(); return f; }
}
