package rpg.core.skill;

import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/** 스킬 이펙트 보내기 (그리는 건 클라 rpg.core.client.fx.SkillFx) */
public final class FxOut {
    private FxOut() {}

    /** 우리 이펙트가 있는 버프: 바닐라 파티클은 안 뿌림 */
    public static final Set<String> HANDLED = Set.of("iron_body", "iron_wall", "berserk", "focus", "hunt_instinct", "booster",
            "elemental_amp", "magic_guard", "meso_guard", "bless", "haste", "plunder", "heal", "first_aid", "sprint", "taunt");

    private static int seq;
    public static int nextId() { return ++seq; }

    private static void send(ServerLevel l, String k, int e, Vec3 at, Vec3 d, double a, double b, String s) {
        Net.fx(l, new Net.Fx(k, e, at.x, at.y, at.z, d.x, d.y, d.z, (float) a, (float) b, s == null ? "" : s));
    }

    /** 근접 스킬 모션 재생됨 → 검기 (스킬:모션, 공속 배율) */
    public static void melee(ServerPlayer p, String skill, String anim) {
        if (anim == null || anim.isEmpty()) return;
        PlayerData d = RpgData.get(p);
        double scale = 1 + (rpg.core.world.RpgWorld.in(p) ? d.atkSpeedPct / 100.0 : 0);
        send(p.serverLevel(), "melee", p.getId(), p.position(), Vec3.ZERO, scale, p.getYRot(), skill + ":" + anim);
    }

    /** 스킬 사용 성공 (버프·이동·파티 스킬) — before = 쓰기 전 위치 */
    public static void cast(ServerPlayer p, PlayerData d, String id, Vec3 before) {
        ServerLevel l = p.serverLevel();
        long now = l.getGameTime();
        StringBuilder s = new StringBuilder(id);
        switch (id) {
            case "heal", "bless", "haste", "plunder" -> {
                for (ServerPlayer t : rpg.core.party.Parties.nearby(p)) if (t.distanceToSqr(p) <= 8 * 8) s.append('|').append(t.getId());
            }
            case "taunt" -> {
                for (Mob m : BranchSkills.mobsAround(l, p.position(), 8)) s.append('|').append(m.getId());
            }
            default -> {}
        }
        long end = "sprint".equals(id) ? d.sprintEnd : d.buffEnd.getOrDefault(id, 0L);
        double dur = Math.max(0, (end - now) / 20.0);
        send(l, "cast", p.getId(), p.position(), before == null ? p.position() : before, dur, p.getYRot(), s.toString());
    }

    // ───── 투사체: 출발 · 끝 · 관통 ─────
    public static void shot(ServerPlayer p, int id, String kind, Vec3 pos, Vec3 dir, double speed, double range) {
        send(p.serverLevel(), "shot", id, pos, dir, speed, range, kind + "|" + p.getId());
    }
    public static void shotEnd(ServerLevel l, int id, String kind, Vec3 at, double radius) {
        send(l, "shotEnd", id, at, Vec3.ZERO, radius, 0, kind);
    }
    /** 맞은 자리 번쩍 (kind: holy · dagger · claw0 · claw1 · sblade) */
    public static void flash(ServerLevel l, String kind, Vec3 at) { send(l, "flash", 0, at, Vec3.ZERO, 0, 0, kind); }
    /** 폭발 (kind: fire · shadow) */
    public static void boom(ServerLevel l, String kind, Vec3 at, double r) { send(l, "boom", 0, at, Vec3.ZERO, r, 0, kind); }
    /** 그림자 칼날: 눈 → 몹 */
    public static void sblade(ServerLevel l, Vec3 a, Vec3 b) { send(l, "sblade", 0, a, b, 0, 0, ""); }
    /** 체인 라이트닝: 점 목록 */
    public static void chain(ServerLevel l, List<Vec3> pts) {
        StringBuilder s = new StringBuilder();
        for (Vec3 v : pts) s.append(String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f;", v.x, v.y, v.z));
        send(l, "chain", 0, pts.get(0), Vec3.ZERO, 0, 0, s.toString());
    }
    // ───── 장판: 덫 · 독안개 ─────
    public static void field(ServerLevel l, int id, String kind, Vec3 at, double r, double seconds) {
        send(l, "field", id, at, Vec3.ZERO, r, seconds, kind);
    }
    public static void fieldSnap(ServerLevel l, int id, Vec3 at) { send(l, "fieldSnap", id, at, Vec3.ZERO, 0, 0, ""); }
    public static void fieldEnd(ServerLevel l, int id, Vec3 at) { send(l, "fieldEnd", id, at, Vec3.ZERO, 0, 0, ""); }
}
