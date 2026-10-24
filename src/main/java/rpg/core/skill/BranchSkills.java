package rpg.core.skill;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import rpg.core.RpgCore;
import rpg.core.combat.Combat;
import rpg.core.combat.CombatEvents;
import rpg.core.combat.MobAI;
import rpg.core.combat.MobData;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 2차 전직 스킬 (skills.json, tier 2)
 *  가디언   taunt 도발 · iron_wall 철벽
 *  버서커   berserk 광폭화 · leap_slam 도약 내려찍기
 *  스나이퍼 charged_shot 차징 샷(누르고 떼기, 관통) · focus 집중
 *  헌터     explosive_shot 폭발탄 · trap 덫
 *  아크메이지 fireball 파이어 볼 · teleport 텔레포트
 *  클레릭   heal 힐 · holy_arrow 홀리 애로우
 *  어쌔신   flurry 연속 찌르기 · knife_throw 단검 투척
 *  트릭스터 poison_mist 독안개 · dash 대쉬
 * 패시브는 skills.json 의 stat 으로 PlayerData.bonus 에 들어가고, 여기와 CombatEvents 가 읽어서 씀.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class BranchSkills {
    private BranchSkills() {}

    public static boolean use(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        return switch (s.id()) {
            case "taunt" -> taunt(p, s, lv);
            case "iron_wall" -> {
                WarriorSkills.burst(p, ParticleTypes.END_ROD, SoundEvents.SHIELD_BLOCK, 0.6f);
                yield buff(p, d, "iron_wall", s.value2(lv), s.value(lv), 0, ParticleTypes.CRIT, SoundEvents.SHIELD_BLOCK);
            }
            case "berserk" -> berserk(p, d, s, lv);
            case "charge_rush" -> WarriorSkills.rush(p, s, lv);
            case "blood_spin" -> WarriorSkills.bloodSpin(p, s, lv);
            case "combo_slash" -> WarriorSkills.combo(p, d, s, lv);
            case "leap_slam" -> leapSlam(p, s, lv);
            case "focus" -> buff(p, d, "focus", s.duration(), s.value(lv), 0, ParticleTypes.END_ROD, SoundEvents.BEACON_ACTIVATE);
            case "explosive_shot" -> explosiveShot(p, s, lv);
            case "sniping" -> ArcherSkills.sniping(p, d, s, lv, now);
            case "mark_shot" -> ArcherSkills.markShot(p, s, lv);
            case "hunt_instinct" -> {
                WarriorSkills.burst(p, ParticleTypes.HAPPY_VILLAGER, SoundEvents.WOLF_HOWL, 1.3f);
                yield buff(p, d, "hunt_instinct", s.duration(), s.value(lv), s.value2(lv), ParticleTypes.HAPPY_VILLAGER, SoundEvents.ARROW_HIT_PLAYER);
            }
            case "trap" -> trap(p, d, s, lv, now);
            case "fireball" -> fireball(p, d, s, lv);
            case "teleport" -> teleport(p, s, lv);
            case "heal" -> healSkill(p, d, s, lv);
            case "holy_arrow" -> holyArrow(p, s, lv);
            case "flurry" -> flurry(p, d, s, lv);
            case "knife_throw" -> knifeThrow(p, s, lv);
            case "poison_mist" -> poisonMist(p, d, s, lv, now);
            case "dash" -> dash(p, d, s, lv, now);
            default -> false;
        };
    }

    private static boolean buff(ServerPlayer p, PlayerData d, String id, double seconds, double v, double v2,
                                net.minecraft.core.particles.SimpleParticleType fx, net.minecraft.sounds.SoundEvent snd) {
        long now = p.level().getGameTime();
        d.buffEnd.put(id, now + Math.round(seconds * 20));
        d.buffVal.put(id, v);
        d.buffVal2.put(id, v2);
        ServerLevel l = p.serverLevel();
        l.sendParticles(fx, p.getX(), p.getY() + 1, p.getZ(), 20, 0.4, 0.6, 0.4, 0.1);
        l.playSound(null, p.blockPosition(), snd, SoundSource.PLAYERS, 0.7f, 1.0f);
        return true;
    }

    // ───────── 공통: 범위 안 RPG 몹 ─────────
    public static List<Mob> mobsAround(ServerLevel l, Vec3 c, double r) {
        List<Mob> out = new ArrayList<>();
        for (Mob m : l.getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(r + 2, 3, r + 2))) {
            if (!m.isAlive() || !Combat.isRpgMob(m)) continue;
            double dx = m.getX() - c.x, dz = m.getZ() - c.z;
            double rr = r + m.getBbWidth() / 2;
            if (dx * dx + dz * dz <= rr * rr && m.getY() < c.y + 2.5 && m.getY() + m.getBbHeight() > c.y - 2) out.add(m);
        }
        return out;
    }

    static Vec3 flatLook(ServerPlayer p) {
        return JobSkills.flatAim(p);
    }

    // ═════════ 가디언 ═════════
    private static boolean taunt(ServerPlayer p, Defs.SkillDef s, int lv) {
        ServerLevel l = p.serverLevel();
        int ticks = (int) Math.round(s.value(lv) * 20);
        for (Mob m : mobsAround(l, p.position(), 8)) {
            MobData md = Combat.mob(m);
            MobAI.taunt(m, md, p, ticks);
            l.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 3, 0.2, 0.1, 0.2, 0);
        }
        l.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        l.playSound(null, p.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7f, 1.3f);
        return true;
    }

    // ═════════ 버서커 ═════════
    private static boolean berserk(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        d.berserkExt = 0;
        WarriorSkills.burst(p, ParticleTypes.LAVA, SoundEvents.RAVAGER_ROAR, 0.8f);
        return buff(p, d, "berserk", s.duration(), s.value(lv), s.value2(lv), ParticleTypes.LAVA, SoundEvents.RAVAGER_ROAR);
    }

    /** 연속 분노: 광폭화 중 처치하면 지속시간 늘어남 (최대 +10초) */
    static void onKill(ServerPlayer p, PlayerData d) {
        long now = p.level().getGameTime();
        double add = d.bonus("rageExtend");
        if (add <= 0 || !d.buff("berserk", now) || d.berserkExt >= 10) return;
        add = Math.min(add, 10 - d.berserkExt);
        d.berserkExt += add;
        d.buffEnd.put("berserk", d.buffEnd.get("berserk") + Math.round(add * 20));
        RpgData.sync(p);
    }

    /** 전투 흥분: 때릴 때마다 이동속도 +%, 최대 5중첩, 3초 */
    static void onHit(ServerPlayer p, PlayerData d, long now) {
        double per = d.bonus("thrillSpeed");
        if (per <= 0) return;
        int stacks = d.buff("battle_thrill", now) ? (int) Math.round(d.buffVal2.getOrDefault("battle_thrill", 0.0)) : 0;
        stacks = Math.min(5, stacks + 1);
        d.buffEnd.put("battle_thrill", now + 60);
        d.buffVal.put("battle_thrill", per * stacks);
        d.buffVal2.put("battle_thrill", (double) stacks);
    }

    private static boolean leapSlam(ServerPlayer p, Defs.SkillDef s, int lv) {
        Vec3 dir = flatLook(p);
        p.setDeltaMovement(dir.x * 0.62, 0.62, dir.z * 0.62);   // 앞으로 약 5칸
        p.hurtMarked = true;
        double pct = s.value(lv);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8f, 0.7f);
        task(p, t -> {
            if (t < 4 || (!p.onGround() && t < 30)) return false;   // 내려올 때까지
            p.fallDistance = 0;
            ServerLevel l = p.serverLevel();
            for (Mob m : mobsAround(l, p.position(), 4.5)) {
                CombatEvents.skillHit(p, m, pct);
                m.setDeltaMovement(m.getDeltaMovement().add(0, 0.3, 0));
            }
            l.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 4, 1, 0.1, 1, 0);
            l.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 20, 1.4, 0.05, 1.4, 0.05);
            l.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.3f);
            if (!rpg.core.compat.EpicFightCompat.playSkill(p, "leap_slam", "GREATSWORD_AIR_SLASH|GREATSWORD_AUTO1"))
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            return true;
        });
        return true;
    }

    // ═════════ 스나이퍼 ═════════
    /** 차징 샷: 떼는 순간. 0초 = value%, 1.5초 가득 = value2%, 관통 */
    static void chargedShot(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long held) {
        double ratio = Math.max(0, Math.min(1, held / 30.0));
        double pct = s.value(lv) + (s.value2(lv) - s.value(lv)) * ratio;
        JobSkills.arrow(p, pct, 0, (float) (3.0 + ratio * 1.5), a -> {
            a.setPierceLevel((byte) 10);
            if (ratio >= 1) a.setCritArrow(true);
        });
        ServerLevel l = p.serverLevel();
        if (ratio >= 1) l.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getEyeY(), p.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
        l.playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, ratio >= 1 ? 0.7f : 1.2f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    // ═════════ 헌터 ═════════
    private static boolean explosiveShot(ServerPlayer p, Defs.SkillDef s, int lv) {
        JobSkills.arrow(p, 0, 0, 3.0f, a -> {
            a.getPersistentData().putDouble("rpgExplode", s.value(lv));
            a.getPersistentData().putDouble("rpgExplodeR", 3.0);
        });
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 폭발탄이 터짐 (RpgArrow) — 소이탄 패시브면 불길 장판 */
    public static void explode(ServerPlayer p, Vec3 at, double pct, double r) {
        ServerLevel l = p.serverLevel();
        for (Mob m : mobsAround(l, at.subtract(0, 0.5, 0), r)) CombatEvents.skillHit(p, m, pct);
        l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, r / 3, 0.2, r / 3, 0);
        l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 20, r / 2, 0.3, r / 2, 0.05);
        l.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.4f);
        PlayerData d = RpgData.get(p);
        double inc = d.bonus("incendiaryPct");
        if (inc > 0) addField(p, "fire", at, r, 60, Combat.statAttack(p, d) * inc / 100.0, 0, 0);
    }

    private static boolean trap(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        int max = 2 + (int) Math.round(d.bonus("trapMaster")) * 2;   // 기본 2개, 덫 장인 +2
        List<Field> mine = new ArrayList<>();
        for (Field f : FIELDS) if (f.owner == p && f.kind.equals("trap")) mine.add(f);
        while (mine.size() >= max) FIELDS.remove(mine.remove(0));   // 오래된 덫부터 사라짐
        int root = (int) Math.round((s.value2(lv) + d.bonus("trapRoot")) * 20);
        addField(p, "trap", p.position(), 1.2, (int) Math.round(s.duration() * 20), s.value(lv), root, 0);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    // ═════════ 아크메이지 ═════════
    private static boolean fireball(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        Shot sh = new Shot(p, "fire", 1.0, 20, s.value(lv), 4);
        sh.dot = Combat.statAttack(p, d) * s.value2(lv) / 100.0 * (1 + d.bonus("burnPct") / 100.0);   // 화염 숙련
        sh.dotTicks = (int) Math.round((s.duration() + d.bonus("burnTime")) * 20);
        SHOTS.add(sh);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 1.0f);
        if (!rpg.core.compat.EpicFightCompat.playAnim(p, "SPEAR_TWOHAND_AUTO1"))
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static boolean teleport(ServerPlayer p, Defs.SkillDef s, int lv) {
        ServerLevel l = p.serverLevel();
        Vec3 look = JobSkills.aimDir(p);
        Vec3 eye = p.getEyePosition();
        double dist = s.value(lv);
        var bh = l.clip(new ClipContext(eye, eye.add(look.scale(dist)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = bh.getType() == HitResult.Type.MISS ? dist : Math.max(0, bh.getLocation().distanceTo(eye) - 0.6);
        Vec3 to = null;
        for (double r = reach; r >= 1 && to == null; r -= 0.5) {   // 설 수 있는 자리를 앞에서부터 찾기
            Vec3 feet = eye.add(look.scale(r)).subtract(0, p.getEyeHeight(), 0);
            for (double dy : new double[]{0, 1, -1, 2, -2}) {
                Vec3 q = feet.add(0, dy, 0);
                if (l.noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(q))) { to = q; break; }
            }
        }
        if (to == null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.literal("갈 수 있는 곳이 없어요").withStyle(net.minecraft.ChatFormatting.GRAY), true);
            return false;
        }
        l.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
        p.teleportTo(to.x, to.y, to.z);
        p.fallDistance = 0;
        l.sendParticles(ParticleTypes.REVERSE_PORTAL, to.x, to.y + 1, to.z, 30, 0.3, 0.6, 0.3, 0.05);
        l.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.3f);
        return true;
    }

    // ═════════ 클레릭 ═════════
    private static boolean healSkill(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        for (ServerPlayer t : rpg.core.party.Parties.nearby(p)) {
            if (t.distanceToSqr(p) > 8 * 8) continue;
            PlayerData td = RpgData.get(t);
            if (td.isDown()) continue;
            double amount = Math.round(Combat.statAttack(p, d) * s.value(lv) / 100.0 * (1 + td.bonus("healPct") / 100.0));   // 마법 스공 비례 고정 수치
            heal(p, d, t, td, amount);
            t.serverLevel().sendParticles(ParticleTypes.HEART, t.getX(), t.getY() + 1.2, t.getZ(), 5, 0.4, 0.4, 0.4, 0);
            t.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + 1, t.getZ(), 10, 0.4, 0.6, 0.4, 0);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    /** 회복 (응급치료·힐). 넘치는 치유: 넘친 양의 일부를 보호막으로 (최대 HP 20%, 깨질 때까지 유지) */
    static void heal(ServerPlayer caster, PlayerData cd, ServerPlayer target, PlayerData td, double amount) {
        double before = td.hp;
        td.hp = Math.min(td.maxHp, td.hp + amount);
        double over = amount - (td.hp - before);
        double pct = cd.bonus("overhealShield");
        if (pct > 0 && over > 0) {
            long now = target.level().getGameTime();
            if (now >= td.shieldEnd) td.shield = 0;
            td.shield = Math.min(td.maxHp * 0.2, td.shield + over * pct / 100.0);
            td.shieldEnd = Long.MAX_VALUE;   // 보호막은 깨질 때까지 유지 (다운되면 사라짐)
        }
        if (target != caster) RpgData.sync(target);
    }

    private static boolean holyArrow(ServerPlayer p, Defs.SkillDef s, int lv) {
        SHOTS.add(new Shot(p, "holy", 1.6, 24, s.value(lv), 0));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.6f);
        if (!rpg.core.compat.EpicFightCompat.playAnim(p, "SPEAR_TWOHAND_AUTO1"))
            p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ═════════ 어쌔신 ═════════
    private static boolean flurry(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv) {
        double pct = s.value(lv);
        int bleedTicks = (int) Math.round(s.duration() * 20);
        int n = s.hits(lv);   // 레벨 오를수록 타수 3~6
        for (int i = 0; i < n; i++) {
            Runnable hit = () -> {
                ServerLevel l = p.serverLevel();
                Vec3 look = flatLook(p);
                double bleed = Combat.statAttack(p, RpgData.get(p)) * s.value2(lv) / 100.0;
                for (Mob m : mobsAround(l, p.position(), 4)) {
                    Vec3 to = new Vec3(m.getX() - p.getX(), 0, m.getZ() - p.getZ());
                    if (to.length() > m.getBbWidth() / 2 + 0.5 && to.normalize().dot(look) < 0.5) continue;
                    CombatEvents.skillHit(p, m, pct);
                    CombatEvents.dot(p, m, "bleed", bleed, bleedTicks);
                }
                Vec3 c = p.position().add(look.scale(1.5));
                l.sendParticles(ParticleTypes.CRIT, c.x, p.getY() + 1.1, c.z, 8, 0.4, 0.3, 0.4, 0.2);
                l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6f, 1.5f);
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            };
            if (i == 0) hit.run(); else JobSkills.later(p, i * 3, hit);
        }
        return true;
    }

    private static boolean knifeThrow(ServerPlayer p, Defs.SkillDef s, int lv) {
        for (float off : new float[]{-12, 0, 12}) {
            Shot sh = new Shot(p, "dagger", 1.4, 16, s.value(lv), 0);
            sh.dir = JobSkills.turn(JobSkills.aimDir(p), off);
            SHOTS.add(sh);
        }
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.8f, 1.6f);
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    // ═════════ 트릭스터 ═════════
    private static boolean poisonMist(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        addField(p, "mist", p.position(), 3, (int) Math.round(s.duration() * 20),
                Combat.statAttack(p, d) * s.value(lv) / 100.0, s.value2(lv), 0);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
        return true;
    }

    private static boolean dash(ServerPlayer p, PlayerData d, Defs.SkillDef s, int lv, long now) {
        ServerLevel l = p.serverLevel();
        // 방향키 쪽으로 (대각선 포함), 안 누르면 조준 방향
        Vec3 dir = d.moveX * d.moveX + d.moveZ * d.moveZ > 0.01 ? new Vec3(d.moveX, 0, d.moveZ).normalize() : flatLook(p);
        Vec3 from = p.position().add(0, 0.6, 0);
        var bh = l.clip(new ClipContext(from, from.add(dir.scale(6)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double dist = bh.getType() == HitResult.Type.MISS ? 6 : Math.max(0, bh.getLocation().distanceTo(from) - 0.5);
        // 지나가는 길의 몹
        for (Mob m : mobsAround(l, p.position().add(dir.scale(dist / 2)), dist / 2 + 1)) {
            Vec3 to = new Vec3(m.getX() - p.getX(), 0, m.getZ() - p.getZ());
            double along = to.dot(dir), side = Math.abs(to.x * dir.z - to.z * dir.x);
            if (along >= -0.5 && along <= dist + 0.8 + m.getBbWidth() / 2 && side <= 1.2 + m.getBbWidth() / 2) CombatEvents.skillHit(p, m, s.value(lv));
        }
        // 땅·공중 상관없이 같은 거리: 6틱 동안 매 틱 속도를 다시 넣음
        double step = dist / 6.0;
        task(p, t -> {
            if (t >= 6) {
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
                p.hurtMarked = true;
                return true;
            }
            p.setDeltaMovement(dir.x * step, 0, dir.z * step);
            p.hurtMarked = true;
            p.fallDistance = 0;
            return false;
        });
        p.invulnerableTime = Math.max(p.invulnerableTime, 10);
        for (double t = 0; t <= dist; t += 0.6) {
            Vec3 q = p.position().add(dir.scale(t));
            l.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y + 0.6, q.z, 1, 0.1, 0.2, 0.1, 0);
        }
        l.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.6f);
        double shadow = d.bonus("shadowTime");   // 그림자 발걸음: 대쉬 뒤 잠깐 투명
        if (shadow > 0) {
            int ticks = (int) Math.round(shadow * 20);
            d.buffEnd.put("shadow_step", now + ticks);
            d.buffVal.put("shadow_step", 0.0);
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
            for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32))) if (m.getTarget() == p) m.setTarget(null);
        }
        return true;
    }

    // ═════════ 매 틱: 투사체 · 장판 · 대기 작업 ═════════
    /** 눈에 보이는 투사체 (입자). kind: fire 파이어 볼(폭발+화상) / holy 홀리 애로우 / dagger 단검 */
    private static final class Shot {
        final ServerPlayer owner; final String kind; final double speed, range, pct, radius;
        Vec3 pos, dir; double travelled, dot; int dotTicks;
        Shot(ServerPlayer o, String kind, double speed, double range, double pct, double radius) {
            owner = o; this.kind = kind; this.speed = speed; this.range = range; this.pct = pct; this.radius = radius;
            pos = o.getEyePosition().add(0, -0.15, 0);
            dir = JobSkills.aimDir(o);
        }
    }
    private static final List<Shot> SHOTS = new ArrayList<>();

    /** 장판: trap 덫(a 피해%, b 속박 틱) / fire 불길(a 초당 피해) / mist 독안개(a 초당 피해, b 둔화%) */
    private static final class Field {
        final ServerPlayer owner; final String kind; final Vec3 pos; final double r, a, b; final long end;
        Field(ServerPlayer o, String kind, Vec3 pos, double r, long end, double a, double b) {
            owner = o; this.kind = kind; this.pos = pos; this.r = r; this.end = end; this.a = a; this.b = b;
        }
    }
    private static final List<Field> FIELDS = new ArrayList<>();

    private static void addField(ServerPlayer p, String kind, Vec3 pos, double r, int ticks, double a, double b, double unused) {
        FIELDS.add(new Field(p, kind, pos, r, p.level().getGameTime() + ticks, a, b));
    }

    /** 조건이 될 때까지 매 틱 확인 (t = 지난 틱, true 면 끝) */
    private record Task(ServerPlayer p, long start, java.util.function.IntPredicate step) {}
    private static final List<Task> TASKS = new ArrayList<>();

    static void task(ServerPlayer p, java.util.function.IntPredicate step) {
        TASKS.add(new Task(p, p.level().getGameTime(), step));
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!TASKS.isEmpty()) {
            for (Task t : List.copyOf(TASKS)) {
                if (!t.p().isAlive() || t.p().hasDisconnected() || t.step().test((int) (t.p().level().getGameTime() - t.start())))
                    TASKS.remove(t);
            }
        }
        if (!SHOTS.isEmpty()) for (Shot s : List.copyOf(SHOTS)) if (stepShot(s)) SHOTS.remove(s);
        if (!FIELDS.isEmpty()) for (Field f : List.copyOf(FIELDS)) if (stepField(f)) FIELDS.remove(f);
    }

    private static boolean stepShot(Shot b) {
        if (!b.owner.isAlive() || b.owner.hasDisconnected()) return true;
        ServerLevel l = b.owner.serverLevel();
        Vec3 next = b.pos.add(b.dir.scale(b.speed));
        var bh = l.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.owner));
        Vec3 end = bh.getType() != HitResult.Type.MISS ? bh.getLocation() : next;
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(l, b.owner, b.pos, end, new AABB(b.pos, end).inflate(0.6),
                x -> x instanceof Mob m && m.isAlive() && !m.isSpectator() && Combat.isRpgMob(m));
        for (int i = 0; i < 3; i++) {
            Vec3 q = b.pos.add(end.subtract(b.pos).scale(i / 3.0));
            switch (b.kind) {
                case "fire" -> {
                    l.sendParticles(ParticleTypes.FLAME, q.x, q.y, q.z, 2, 0.08, 0.08, 0.08, 0.01);
                    l.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
                }
                case "holy" -> l.sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 1, 0.02, 0.02, 0.02, 0);
                default -> l.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
        Vec3 at = hit != null ? hit.getLocation() : bh.getType() != HitResult.Type.MISS ? end : null;
        if (at != null) {
            if (b.radius > 0) {   // 파이어 볼: 터지며 범위 피해 + 화상
                for (Mob m : mobsAround(l, at.subtract(0, 0.5, 0), b.radius)) {
                    CombatEvents.skillHit(b.owner, m, b.pct);
                    CombatEvents.dot(b.owner, m, "burn", b.dot, b.dotTicks);
                }
                l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.5, 0.3, 0.5, 0);
                l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, b.radius / 2, 0.4, b.radius / 2, 0.06);
                l.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 1.5f);
            } else if (hit != null && hit.getEntity() instanceof Mob t) {
                CombatEvents.skillHit(b.owner, t, b.pct);
                l.sendParticles(b.kind.equals("holy") ? ParticleTypes.END_ROD : ParticleTypes.CRIT,
                        t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
                l.playSound(null, t.blockPosition(), b.kind.equals("holy") ? SoundEvents.AMETHYST_BLOCK_HIT : SoundEvents.TRIDENT_HIT,
                        SoundSource.PLAYERS, 0.8f, 1.4f);
            }
            return true;
        }
        b.pos = next;
        b.travelled += b.speed;
        return b.travelled >= b.range;
    }

    private static final DustParticleOptions MIST = new DustParticleOptions(new Vector3f(0.35f, 0.75f, 0.25f), 2.0f);
    private static final DustParticleOptions TRAP = new DustParticleOptions(new Vector3f(0.6f, 0.45f, 0.2f), 1.0f);

    /** true = 끝 */
    private static boolean stepField(Field f) {
        if (!f.owner.isAlive() || f.owner.hasDisconnected()) return true;
        ServerLevel l = f.owner.serverLevel();
        long now = l.getGameTime();
        if (now > f.end) return true;
        switch (f.kind) {
            case "trap" -> {
                if (now % 10 == 0) {
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3;
                        l.sendParticles(TRAP, f.pos.x + Math.cos(a) * 0.6, f.pos.y + 0.1, f.pos.z + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
                    }
                }
                if (now % 2 != 0) return false;
                List<Mob> in = mobsAround(l, f.pos, f.r);
                if (in.isEmpty()) return false;
                boolean master = RpgData.get(f.owner).bonus("trapMaster") > 0;   // 덫 장인: 반경 넓고 끌어당김
                for (Mob m : mobsAround(l, f.pos, master ? 3.0 : 1.8)) {
                    if (master) {
                        Vec3 v = f.pos.subtract(m.position());
                        m.setDeltaMovement(v.x * 0.5, m.getDeltaMovement().y, v.z * 0.5);
                        m.hurtMarked = true;
                    }
                    CombatEvents.skillHit(f.owner, m, f.a);
                    MobData md = Combat.mob(m);
                    if (md != null) md.slow(100, now, now + (long) f.b);   // 속박
                    l.sendParticles(ParticleTypes.CRIT, m.getX(), m.getY() + 0.3, m.getZ(), 12, 0.3, 0.1, 0.3, 0.1);
                }
                l.playSound(null, net.minecraft.core.BlockPos.containing(f.pos), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
                return true;
            }
            case "fire" -> {
                if (now % 4 == 0) l.sendParticles(ParticleTypes.FLAME, f.pos.x, f.pos.y + 0.1, f.pos.z, 8, f.r / 2, 0.1, f.r / 2, 0.01);
                if (now % 20 == 0) for (Mob m : mobsAround(l, f.pos, f.r)) CombatEvents.fixedHit(f.owner, m, f.a);
            }
            default -> {   // mist
                if (now % 4 == 0) l.sendParticles(MIST, f.pos.x, f.pos.y + 0.6, f.pos.z, 12, f.r / 2, 0.5, f.r / 2, 0);
                if (now % 20 == 0) for (Mob m : mobsAround(l, f.pos, f.r)) {
                    CombatEvents.fixedHit(f.owner, m, f.a);
                    MobData md = Combat.mob(m);
                    if (md != null) md.slow(f.b, now, now + 25);
                }
            }
        }
        return false;
    }
}
