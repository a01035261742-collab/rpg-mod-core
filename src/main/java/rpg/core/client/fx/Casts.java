package rpg.core.client.fx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import rpg.core.net.Net;

/** 스킬 사용 이펙트: 힐 · 버프 · 이동 (켜는 순간 + 지속 버프는 몸 겉 은은한 색 테두리) */
final class Casts {
    private Casts() {}

    interface Draw { boolean draw(SkillFx.D d, double t); }
    static final class L extends SkillFx.Fx {
        final Draw f;
        L(Draw f) { this.f = f; }
        boolean draw(SkillFx.D d, double t) { return f.draw(d, t); }
    }

    static Entity get(int id) { var l = Minecraft.getInstance().level; return l == null ? null : l.getEntity(id); }

    /** 바닥 링: 지름 from → to (life 초) */
    static void ring(SkillFx.D d, String tex, Vec3 c, double t, double life, double from, double to, int rgb) {
        double k = t / life;
        if (k < 0 || k >= 1) return;
        d.flat(tex, true, c.add(0, 0.05, 0), (from + (to - from) * k) / 2, rgb, (float) (1 - k));
    }

    /** 솟아오르는 조각들 (대상 몸 주변) */
    static final class Rise {
        final double[][] p; final String tex; final double life, size; final boolean add;
        Rise(int n, double rad, String tex, double life, double size, boolean add) {
            this.tex = tex; this.life = life; this.size = size; this.add = add; p = new double[n][4];
            for (double[] q : p) { double a = SkillFx.RND.nextDouble() * Math.PI * 2, r = rad * (0.4 + 0.6 * SkillFx.RND.nextDouble()); q[0] = Math.cos(a) * r; q[1] = Math.sin(a) * r; q[2] = SkillFx.RND.nextDouble() * 0.5; q[3] = 1 + SkillFx.RND.nextDouble(); }
        }
        boolean draw(SkillFx.D d, Vec3 c, double t, int rgb) {
            boolean any = false;
            for (double[] q : p) {
                double k = (t - q[2]) / life;
                if (k < 0) { any = true; continue; }
                if (k >= 1) continue;
                any = true;
                double s = size * (k < 0.2 ? k / 0.2 : 1 - (k - 0.2) / 0.8 * 0.7);
                d.bill(tex, add, c.add(q[0], 0.2 + k * 1.6 * q[3], q[1]), s, 0, rgb, 1f);
            }
            return any;
        }
    }

    /** 위로 던져졌다 떨어지는 금화 */
    static boolean coins(SkillFx.D d, Vec3 c, double t, double[][] p, double y0, double size) {
        if (t < 0) return true;
        if (t > 0.8) return false;
        for (double[] q : p) {
            double y = y0 + q[2] * t - 7 * t * t, s = size * (t > 0.6 ? (0.8 - t) / 0.2 : 1);
            d.billWH("coin", false, c.add(Math.cos(q[0]) * q[1] * t, Math.max(0.15, y), Math.sin(q[0]) * q[1] * t), s * Math.abs(Math.cos(t * 10 + q[0])) + 0.03, s, 0xFFFFFF, 1f);
        }
        return true;
    }
    static double[][] coinSet(int n) {
        double[][] p = new double[n][3];
        for (double[] q : p) { q[0] = SkillFx.RND.nextDouble() * Math.PI * 2; q[1] = 0.6 + SkillFx.RND.nextDouble() * 0.8; q[2] = 3 + SkillFx.RND.nextDouble() * 2; }
        return p;
    }

    /** 지속 버프: 몸 겉 은은한 색 테두리 (갑옷 위로도 보임) */
    static void aura(int ent, String id, int rgb, double dur) {
        if (dur <= 0) return;
        SkillFx.aura(ent + ":" + id, new L((d, t) -> {
            if (t > dur) return false;
            Entity e = get(ent);
            if (e == null || !e.isAlive()) return t < 1;
            double in = Math.min(1, t / 0.3), out = Math.min(1, (dur - t) / 0.5);
            d.cyl("aura", true, d.ent(e).add(0, -0.05, 0), e.getBbWidth() + 0.5, e.getBbHeight() + 0.25, rgb, (float) (in * out * (0.45 + 0.08 * Math.sin(t * 4))));
            return true;
        }));
    }

    static void start(Net.Fx pk) {
        String[] sp0 = pk.s().split("\\|");
        String id = sp0[0];
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i < sp0.length; i++) ids.add(SkillFx.parse(sp0[i]));
        int me = pk.e();
        double dur = pk.a();
        Vec3 pos = new Vec3(pk.x(), pk.y(), pk.z()), before = new Vec3(pk.dx(), pk.dy(), pk.dz());
        switch (id) {
            case "heal" -> {
                Rise[] r = new Rise[ids.size()];
                for (int i = 0; i < r.length; i++) r[i] = new Rise(7, 0.5, "plus", 1.0, 0.45, true);
                SkillFx.add(new L((d, t) -> {
                    Entity c = get(me);
                    ring(d, "ring_heal", c != null ? d.ent(c) : pos, t, 0.5, 0.5, 16, 0xFFFFFF);
                    boolean any = t < 0.5;
                    for (int i = 0; i < r.length; i++) {
                        Entity e = get(ids.get(i));
                        if (e == null) continue;
                        Vec3 p = d.ent(e); double lag = c == null ? 0 : p.distanceTo(d.ent(c)) / 16;
                        any |= r[i].draw(d, p, t - lag, 0xFFFFFF);
                        ring(d, "ring_heal", p, t - lag, 0.5, 1.4, 0.4, 0xFFFFFF);
                    }
                    return any;
                }));
            }
            case "first_aid" -> { Rise r = new Rise(6, 0.45, "plus", 0.9, 0.35, true); SkillFx.add(new L((d, t) -> { Entity e = get(me); return e != null && r.draw(d, d.ent(e), t, 0xFFFFFF); })); }
            case "bless" -> {
                Rise[] r = new Rise[ids.size()];
                for (int i = 0; i < r.length; i++) r[i] = new Rise(6, 0.5, "gold", 0.8, 0.4, true);
                SkillFx.add(new L((d, t) -> {
                    Entity c = get(me);
                    ring(d, "ring_gold", c != null ? d.ent(c) : pos, t, 0.5, 0.5, 16, 0xFFFFFF);
                    boolean any = t < 0.6;
                    for (int i = 0; i < r.length; i++) {
                        Entity e = get(ids.get(i));
                        if (e == null) continue;
                        Vec3 p = d.ent(e); double k = (t - (i == 0 ? 0 : 0.12)) / 0.6;
                        if (k >= 0 && k < 1) d.pillar("white", true, p, 1.1 * (1 - k * 0.6), 3.2 * (k < 0.25 ? k / 0.25 : 1), 0xF2C440, (float) (0.6 * (1 - k)));
                        any |= r[i].draw(d, p, t - 0.15, 0xFFFFFF);
                    }
                    return any;
                }));
            }
            case "haste" -> {
                Rise[] r = new Rise[ids.size()];
                for (int i = 0; i < r.length; i++) r[i] = new Rise(5, 0.45, "chevron", 0.7, 0.4, true);
                SkillFx.add(new L((d, t) -> {
                    Entity c = get(me);
                    ring(d, "ring_cyan", c != null ? d.ent(c) : pos, t, 0.5, 0.5, 16, 0xFFFFFF);
                    boolean any = t < 0.5;
                    for (int i = 0; i < r.length; i++) { Entity e = get(ids.get(i)); if (e != null) any |= r[i].draw(d, d.ent(e), t - (i == 0 ? 0 : 0.12), 0xFFFFFF); }
                    return any;
                }));
            }
            case "plunder" -> {
                double[][][] c = new double[ids.size()][][];
                for (int i = 0; i < c.length; i++) c[i] = coinSet(6);
                SkillFx.add(new L((d, t) -> {
                    Entity cc = get(me);
                    ring(d, "ring_gold", cc != null ? d.ent(cc) : pos, t, 0.5, 0.5, 16, 0xFFFFFF);
                    boolean any = t < 0.5;
                    for (int i = 0; i < c.length; i++) { Entity e = get(ids.get(i)); if (e != null) any |= coins(d, d.ent(e), t - (i == 0 ? 0 : 0.12), c[i], 1.2, 0.3); }
                    return any;
                }));
            }
            case "taunt" -> SkillFx.add(new L((d, t) -> {
                Entity c = get(me);
                Vec3 cp = c != null ? d.ent(c) : pos;
                ring(d, "ring_red", cp, t, 0.45, 0.6, 16, 0xFFFFFF);
                for (int mid : ids) {
                    Entity m = get(mid);
                    if (m == null) continue;
                    Vec3 mp = d.ent(m);
                    double k = (t - mp.distanceTo(cp) / 16 * 0.45) / 1.6;
                    if (k < 0 || k >= 1) continue;
                    double sc = k < 0.12 ? k / 0.12 * 0.7 : 0.55;
                    d.billWH("excl", false, mp.add(0, m.getBbHeight() + 0.45 + Math.max(0, 0.3 - k * 3) * 0.5, 0), sc * 0.5, sc, 0xFFFFFF, 1f);
                }
                return t < 2.2;
            }));
            case "iron_body" -> { selfBurst(me, pos, "ring_grey", 2.4, 0.6, 0.4, new Rise(10, 0.5, "grey", 0.6, 0.5, true)); aura(me, id, 0x9AA4B8, dur); }
            case "berserk" -> { selfBurst(me, pos, "ring_red", 2.6, 0.8, 0.35, new Rise(14, 0.55, "ember", 0.5, 0.55, true)); aura(me, id, 0xE03A2A, dur); }
            case "hunt_instinct" -> selfBurst(me, pos, "ring_leaf", 0.5, 2.4, 0.4, new Rise(8, 0.5, "green", 0.6, 0.4, true));
            case "meso_guard" -> {
                double[][] c = coinSet(7);
                SkillFx.add(new L((d, t) -> { Entity e = get(me); Vec3 p = e != null ? d.ent(e) : pos; ring(d, "ring_gold", p, t, 0.4, 0.5, 2.4, 0xFFFFFF); return coins(d, p, t, c, 1.2, 0.3); }));
                aura(me, id, 0xF2C440, dur);
            }
            case "iron_wall" -> {
                SkillFx.add(new L((d, t) -> {
                    Entity e = get(me); Vec3 p = e != null ? d.ent(e) : pos;
                    ring(d, "ring_blue", p, t, 0.4, 0.6, 2.6, 0xFFFFFF);
                    double k = t / 0.35;
                    if (k >= 1) return false;
                    for (int i = 0; i < 4; i++) {   // 방패판 4장이 몸으로 모임
                        double a = i * Math.PI / 2 + t * 2, r = 1.4 * (1 - k);
                        Vec3 c = p.add(Math.cos(a) * r, 1.0, Math.sin(a) * r), tan = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.4);
                        d.quad(d.add("shield"), c.subtract(tan).add(0, -0.6, 0), c.add(tan).add(0, -0.6, 0), c.add(tan).add(0, 0.6, 0), c.subtract(tan).add(0, 0.6, 0), 0, 0, 1, 1, 0xFFFFFF, (float) (1 - k), true);
                    }
                    return true;
                }));
                aura(me, id, 0x4A7AD0, dur);
            }
            case "magic_guard" -> {
                SkillFx.add(new L((d, t) -> {
                    Entity e = get(me); Vec3 p = (e != null ? d.ent(e) : pos).add(0, 1.0, 0);
                    ring(d, "ring_blue", p.add(0, -1.0, 0), t, 0.4, 0.5, 2.4, 0xFFFFFF);
                    double k = t / 0.4;
                    if (k >= 1) return false;
                    sphere(d, p, 1.3 - 0.5 * k, (float) (1 - k));   // 벌집 방울이 번쩍하고 사라짐
                    return true;
                }));
                aura(me, id, 0x5AB0FF, dur);
            }
            case "focus" -> SkillFx.aura(me + ":" + id, new L((d, t) -> {
                Entity e = get(me);
                if (e == null || t > Math.max(dur, 0.6)) return false;
                Vec3 p = d.ent(e);
                ring(d, "ring_gold", p, t, 0.4, 2.2, 0.5, 0xFFFFFF);
                double k = Math.min(1, t / 0.3), out = Math.min(1, (dur - t) / 0.5);
                double rot = t * 1.5 * (1 - k) + 0.785 * k, s = 1.6 - 0.9 * k;
                d.bill("reticle", true, p.add(0, e.getBbHeight() + 0.55, 0), s, rot, 0xFFFFFF, (float) ((k < 1 ? k : 0.6 + 0.25 * Math.sin(t * 6)) * out));
                return true;
            }));
            case "booster" -> SkillFx.aura(me + ":" + id, new L((d, t) -> {
                Entity e = get(me);
                if (e == null || t > Math.max(dur, 0.6)) return false;
                Vec3 p = d.ent(e);
                ring(d, "ring_leaf", p, t, 0.4, 0.5, 2.4, 0xFFFFFF);
                double out = Math.min(1, (dur - t) / 0.5), in = Math.min(1, t / 0.3);
                for (int i = 0; i < 6; i++) {   // 초록 바람이 몸을 감고 올라감
                    double a = i * Math.PI / 3 + t * 7;
                    d.bill("green", true, p.add(Math.cos(a) * 0.65, 0.3 + ((t * 1.2 + i / 6.0) % 1) * 1.6, Math.sin(a) * 0.65), 0.35 * in * out, 0, 0xFFFFFF, 1f);
                }
                return true;
            }));
            case "elemental_amp" -> SkillFx.aura(me + ":" + id, new L((d, t) -> {
                Entity e = get(me);
                if (e == null || t > Math.max(dur, 0.6)) return false;
                Vec3 p = d.ent(e);
                ring(d, "ring_blue", p, t, 0.4, 0.5, 2.4, 0xFFFFFF);
                double out = Math.min(1, (dur - t) / 0.5), in = Math.min(1, t / 0.3);
                String[] o = {"fire_o", "ice_o", "volt_o"};
                for (int i = 0; i < 3; i++) {   // 불·얼음·번개 구슬
                    double a = i * Math.PI * 2 / 3 + t * 3;
                    d.bill(o[i], true, p.add(Math.cos(a) * 0.85, 1.2 + Math.sin(t * 3 + i) * 0.12, Math.sin(a) * 0.85), 0.4 * in * out, 0, 0xFFFFFF, 1f);
                }
                return true;
            }));
            case "sprint" -> SkillFx.aura(me + ":" + id, new L((d, t) -> {
                Entity e = get(me);
                if (e == null || t > Math.max(dur, 0.6)) return false;
                Vec3 p = d.ent(e);
                ring(d, "ring_green", p, t, 0.4, 0.4, 2.4, 0xFFFFFF);
                Vec3 flat = new Vec3(e.getX() - e.xo, 0, e.getZ() - e.zo);   // 남의 캐릭터도 움직임 보이게 (위치 차이)
                if (flat.lengthSqr() > 0.002 && t > 0.1) {   // 달리는 동안 발 뒤로 바람 줄기
                    Vec3 f = flat.normalize(), side = new Vec3(-f.z, 0, f.x);
                    for (int i = 0; i < 6; i++) {
                        double ph = (t * 2.2 + i * 0.37) % 1, sx = (i % 2 == 0 ? 1 : -1) * (0.15 + 0.06 * i);
                        Vec3 c = p.add(side.scale(sx)).add(0, 0.1 + (i * 0.083) % 0.5, 0).subtract(f.scale(0.4 + ph * 1.2));
                        d.stick("wind", true, c, f, 0.9, 0.04, 0xFFFFFF, (float) (1 - ph));
                    }
                }
                return true;
            }));
            case "teleport" -> {
                double[][] sp = new double[20][3];
                for (double[] q : sp) { q[0] = SkillFx.RND.nextDouble() * Math.PI * 2; q[1] = SkillFx.RND.nextDouble() * 1.8; q[2] = 0.3 + SkillFx.RND.nextDouble() * 0.5; }
                SkillFx.add(new L((d, t) -> {
                    if (t > 0.6) return false;
                    double k1 = (t + 0.08) / 0.35, k2 = t / 0.4;
                    if (k1 < 1) d.pillar("column", true, before, 0.9 * (1 - k1), 2.4 * (1 + 0.3 * k1), 0xFFFFFF, 1f);   // 사라짐
                    if (k2 < 1) d.pillar("column", true, pos, 0.9 * Math.min(1, k2 * 4) * (1 - k2), 2.4 * (1 + 0.3 * k2), 0xFFFFFF, 1f);   // 나타남
                    for (int i = 0; i < sp.length; i++) {
                        double[] q = sp[i]; boolean out = i < 10; double k = t / 0.4;
                        if (k >= 1) continue;
                        Vec3 c = out ? before : pos; double r = out ? q[2] + k * 0.8 : q[2] * (1 - k) + 0.05;
                        d.bill("star", true, c.add(Math.cos(q[0]) * r, q[1] + (out ? k * 0.6 : -k * 0.3), Math.sin(q[0]) * r), 0.35 * (1 - k), 0, 0xFFFFFF, 1f);
                    }
                    ring(d, "ring_blue", before, t, 0.35, 0.5, 2.2, 0xFFFFFF);
                    ring(d, "ring_blue", pos, t, 0.35, 2.2, 0.5, 0xFFFFFF);
                    return true;
                }));
            }
            case "charge_rush" -> {
                double[][] dust = dustSet(25);
                SkillFx.add(new L((d, t) -> {
                    if (t > 0.9) return false;
                    Entity e = get(me);
                    Vec3 now = e != null ? d.ent(e) : pos;
                    for (int i = 0; i < 5; i++) puff(d, before.add(now.subtract(before).scale(i / 4.0)), t - i * 0.06, 0.45, 0.45, dust, i * 5, 5);
                    if (t < 0.45 && e != null) {   // 몸 주변 바람 줄기
                        Vec3 look = e.getLookAngle(), f = new Vec3(look.x, 0, look.z).normalize(), side = new Vec3(-f.z, 0, f.x);
                        for (int i = 0; i < 7; i++) {
                            Vec3 c = now.add(side.scale(((i * 37) % 11 / 11.0 - 0.5) * 1.1)).add(0, 0.3 + (i * 0.2) % 1.4, 0).subtract(f.scale(0.6 + (i % 3) * 0.2));
                            d.stick("wind", true, c, f, 1.4, 0.05, 0xFFFFFF, (float) (t < 0.3 ? 1 : 1 - (t - 0.3) / 0.15));
                        }
                    }
                    return true;
                }));
            }
            case "evade_leap" -> {
                double[][] dust = dustSet(16);
                Vec3[] ghost = new Vec3[3]; double[] land = {-1};
                SkillFx.add(new L((d, t) -> {
                    if (t > 1.2) return false;
                    Entity e = get(me);
                    puff(d, before, t, 0.45, 0.55, dust, 0, 8);
                    if (e != null) {
                        Vec3 p = d.ent(e);
                        for (int i = 0; i < 3; i++) if (ghost[i] == null && t >= 0.08 + i * 0.1) ghost[i] = p;   // 보라 잔상
                        if (land[0] < 0 && t > 0.15 && e.onGround()) land[0] = t;
                        if (land[0] > 0) puff(d, p, t - land[0], 0.5, 0.65, dust, 8, 8);
                    }
                    for (int i = 0; i < 3; i++) {
                        if (ghost[i] == null) continue;
                        double k = (t - (0.08 + i * 0.1)) / 0.3;
                        if (k >= 0 && k < 1) d.cyl("aura", true, ghost[i], 0.9, 1.9, 0x7A4AE0, (float) (0.9 * (1 - k)));
                    }
                    return true;
                }));
            }
            default -> {}
        }
    }

    static void selfBurst(int me, Vec3 pos, String ringTex, double from, double to, double life, Rise r) {
        SkillFx.add(new L((d, t) -> {
            Entity e = get(me); Vec3 p = e != null ? d.ent(e) : pos;
            ring(d, ringTex, p, t, life, from, to, 0xFFFFFF);
            return r.draw(d, p, t, 0xFFFFFF) || t < life;
        }));
    }

    static double[][] dustSet(int n) {
        double[][] p = new double[n][2];
        for (double[] q : p) { q[0] = SkillFx.RND.nextDouble() * Math.PI * 2; q[1] = 0.8 + SkillFx.RND.nextDouble() * 1.2; }
        return p;
    }
    /** 바닥 먼지 (작게) */
    static void puff(SkillFx.D d, Vec3 c, double t, double life, double size, double[][] p, int from, int n) {
        double k = t / life;
        if (k < 0 || k >= 1) return;
        for (int i = from; i < from + n && i < p.length; i++) {
            double[] q = p[i];
            d.bill("dust", false, c.add(Math.cos(q[0]) * q[1] * k * 0.9, 0.15 + k * 0.35, Math.sin(q[0]) * q[1] * k * 0.9), size * 0.6 * (0.5 + k), 0, 0xFFFFFF, (float) (0.85 * (1 - k)));
        }
    }

    /** 벌집 방울 (위도·경도 사각형) */
    static void sphere(SkillFx.D d, Vec3 c, double r, float al) {
        var vc = d.add("hex");
        int la = 8, lo = 16;
        for (int i = 0; i < la; i++) for (int j = 0; j < lo; j++) {
            double t0 = Math.PI * i / la, t1 = Math.PI * (i + 1) / la, p0 = Math.PI * 2 * j / lo, p1 = Math.PI * 2 * (j + 1) / lo;
            Vec3 a = pt(c, r, t0, p0), b = pt(c, r, t0, p1), e = pt(c, r, t1, p1), f = pt(c, r, t1, p0);
            d.quad(vc, a, b, e, f, (float) j / lo, (float) i / la, (float) (j + 1) / lo, (float) (i + 1) / la, 0x5AB0FF, al, true);
        }
    }
    static Vec3 pt(Vec3 c, double r, double th, double ph) { return c.add(r * Math.sin(th) * Math.cos(ph), r * Math.cos(th), r * Math.sin(th) * Math.sin(ph)); }
}
