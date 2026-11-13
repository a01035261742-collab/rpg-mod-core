package rpg.core.combat;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 플레이어 상태이상 (buffEnd "st_" + 종류).
 * 기절 = 못 움직임·스킬·구르기 X, 침묵 = 스킬 X, 출혈 = 1초마다 최대 HP % (움직이면 2배),
 * 둔화 = 이동속도 −%, 독 = 표시 (피해는 BossX).
 */
public final class Status {
    private Status() {}

    public static final Map<String, String> NAMES = Map.of(
            "st_stun", "기절", "st_silence", "침묵", "st_bleed", "출혈", "st_slow", "둔화", "st_poison", "독");

    public static void apply(ServerPlayer p, String kind, double sec, double val) {
        if (sec <= 0) return;
        PlayerData d = RpgData.get(p);
        long now = p.level().getGameTime();
        String k = "st_" + kind;
        long end = now + Math.round(sec * 20);
        if (d.buff(k, now)) {
            end = Math.max(end, d.buffEnd.get(k));
            val = Math.max(val, d.buffV(k, now));
        }
        d.buffEnd.put(k, end);
        d.buffVal.put(k, val);
        if (kind.equals("stun")) STUN_POS.put(p.getUUID(), p.position());
        if (kind.equals("bleed")) LAST_POS.put(p.getUUID(), p.position());
        RpgData.sync(p);
    }

    public static boolean stunned(PlayerData d, long now) { return d.buff("st_stun", now); }
    public static boolean silenced(PlayerData d, long now) { return d.buff("st_silence", now); }
    public static double slowPct(PlayerData d, long now) { return (stunned(d, now) ? 100 : 0) + d.buffV("st_slow", now); }

    /** 스킬 막기: 막았으면 true (메시지) */
    public static boolean blockSkill(ServerPlayer p, PlayerData d, long now) {
        String why = stunned(d, now) ? "기절" : silenced(d, now) ? "침묵" : null;
        if (why == null) return false;
        p.displayClientMessage(Component.literal(why + " 중에는 스킬을 쓸 수 없어요").withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    private static final Map<java.util.UUID, Vec3> STUN_POS = new java.util.HashMap<>(), LAST_POS = new java.util.HashMap<>();

    /** 매 틱 (보스 경기장 · 사냥터 공통): 기절 자리 고정 · 출혈 피해 (피해 주는 몹 필요) */
    public static void tick(ServerPlayer p, Mob src, long now) {
        PlayerData d = RpgData.get(p);
        if (stunned(d, now)) {
            Vec3 at = STUN_POS.computeIfAbsent(p.getUUID(), u -> p.position());
            if (Boss.flat(p.position(), at) > 0.25) p.connection.teleport(at.x, p.getY(), at.z, p.getYRot(), p.getXRot());
        } else STUN_POS.remove(p.getUUID());
        if (d.buff("st_bleed", now)) {
            if (now % 20 == 0 && src != null && d.hp > 0 && !d.isDown()) {
                Vec3 last = LAST_POS.getOrDefault(p.getUUID(), p.position());
                boolean moved = Boss.flat(p.position(), last) > 0.5;
                LAST_POS.put(p.getUUID(), p.position());
                CombatEvents.maxHpHit(src, p, d.buffV("st_bleed", now) * (moved ? 2 : 1));
                p.serverLevel().sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.7f, 0f, 0f), 1f),
                        p.getX(), p.getY() + 1, p.getZ(), moved ? 8 : 4, 0.3, 0.4, 0.3, 0);
            }
        } else LAST_POS.remove(p.getUUID());
    }

    /** 패턴 json: pStun · pSilence · pBleed (초), bleedPct */
    public static void fromPattern(ServerPlayer p, Boss.Pattern pt) {
        if (!p.isAlive() || RpgData.get(p).isDown()) return;
        apply(p, "stun", pt.x("pStun", 0), 0);
        apply(p, "silence", pt.x("pSilence", 0), 0);
        apply(p, "bleed", pt.x("pBleed", 0), pt.x("bleedPct", 1));
    }

    /** 사망·쓰러짐·경기장 끝: 전부 지움 */
    public static void clear(PlayerData d) {
        for (String k : NAMES.keySet()) d.buffEnd.remove(k);
    }
}
