package rpg.core.balance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;

/**
 * 밸런스 수치. data/rpgcore/rpg_balance/player.json 에서 읽는다.
 * 서버 폴더에 같은 경로의 데이터팩을 넣으면 덮어쓸 수 있고, /reload 로 바로 반영된다.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Balance {
    private Balance() {}

    // 기본값 (파일이 없거나 항목이 빠졌을 때)
    public static int startStat = 4, startStamina = 0;
    public static double staminaPerPoint = 1.0;
    public static int statPointsPerLevel = 5;
    public static int maxLevel = 200;
    public static double hpBase = 50, hpPerLevel = 10, hpPerVit = 5;
    public static double mpBase = 30, mpPerLevel = 4, mpPerInt = 2;
    public static double hpRegenPct = 0.0, mpRegenPct = 0.5, mageMpRegenPct = 1.0, regenInterval = 5.0, bossPotionCooldown = 5;
    /** 직업별 레벨당 MP (초보자 전사 궁수 마법사 도적) */
    public static double[] mpPerLevelByJob = {5, 5, 10, 5, 10};
    public static double critBase = 5.0, critPerDex = 0.1, healPerInt = 0.5, dropPerLuk = 0.2, goldPerLuk = 0.3;
    public static double expBase = 15, expPower = 2.0, expLinear = 30;
    public static int skillPointsPerLevel = 2, passivePointsPerLevel = 1;
    public static int jobLevel1 = 10, jobRewardPotions = 10, jobLevel2 = 30, jobReward2Potions = 20;
    public static double potionCooldown = 1.0, rangedRange = 20;
    /** 물약 종류 (player.json potions): hp/mp 고정 회복, hpPct/mpPct 최대치 %, cooldown 초 (0 = potionCooldown) */
    public record PotionDef(double hp, double mp, double hpPct, double mpPct, double cooldown) {}
    public static final java.util.Map<String, PotionDef> POTIONS = new java.util.LinkedHashMap<>();
    static {   // player.json 을 못 읽어도 기본 물약은 동작
        POTIONS.put("red_potion", new PotionDef(60, 0, 0, 0, 0));
        POTIONS.put("blue_potion", new PotionDef(0, 40, 0, 0, 0));
    }
    public static int partyMax = 6;
    public static double partyRange = 40, partyBonusPerMember = 10, deathExpLossPct = 30;
    public static boolean downEverywhere = true;
    public static double downSeconds = 30, reviveSeconds = 3, reviveHpPct = 30;
    public static double dodgeCooldown = 3.0, dodgePower = 1.1;
    public static int dodgeIframes = 8;
    /** 직업별 평타 계수 % (0 초보 1 전사 2 궁수 3 법사 4 도적) */
    public static double[] basicAttackPct = {100, 70, 70, 70, 100};
    /** 몹 방어율 % (mobs.json defRate). 없으면 일반 레벨/5 (최대 10), 엘리트 20, 보스 30 */
    public static final java.util.Map<String, Double> MOB_DEF = new java.util.HashMap<>();
    /** 직업별 기본 숙련도 (0~1). 전사 0.4 (마스터리 스킬로 올림), 나머지는 개편 전까지 0.8 */
    public static double[] masteryByJob = {0.8, 0.4, 0.4, 0.4, 0.8};
    public static double mastery = 0.8, critDamage = 1.5, fistAttack = 5, defPerVit = 1.0, defBase = 50, defPerMobLevel = 10;
    public static java.util.Set<String> huntingDimensions = new java.util.HashSet<>();

    /** 몹 표: 키 → 몹 정의 */
    public static final Map<String, MobDef> MOBS = new java.util.LinkedHashMap<>();

    public record MobStats(double hp, double atk, long exp, long gold) {}

    /**
     * 몹 패턴 (예고 후 공격). type: slam(내 주변 원) · leap(대상 자리로 점프, 원) · dash(직선 돌진) ·
     * shot(직선 사격) · zone(대상 자리 장판) · cone(앞 부채꼴). 시간은 초, 피해는 몹 공격력의 %
     */
    /**
     * 근접 공격 (EF 방식 판정): anim = GeckoLib 애니 이름, start~end 틱 동안 판정, length = 전체 길이(틱).
     * range = 몸 끝에서 앞으로 몇 칸, angle > 0 이면 부채꼴(도), 아니면 폭 width 직사각형. knock = 넉백 세기, lunge = 타격 순간 앞으로 내딛는 세기
     */
    public record MobAttack(String anim, int start, int end, int length, double range, double width, double angle, double knock, double lunge) {}

    public record MobSkill(String type, double range, double cd, double delay, double pct, double r, double len,
                           double w, double angle, int hits, String effect, double hpBelow, int count) {}

    /** 몹 모양·행동 옵션: head = 머리에 씌울 아이템, baby = 아기, passive = 먼저 안 때림, chase = 다가오게 만듦 */
    public record MobLook(String head, boolean baby, boolean passive, boolean chase) {}

    /** 사냥터 프리셋 */
    public record Ground(String name, java.util.List<String> mobs, int min, int max) {}
    public static final Map<String, Ground> GROUNDS = new java.util.LinkedHashMap<>();
    /** 엘리트: 확률 %, 체력·공격·보상 배수, 장비 드롭 배수 */
    public static double eliteChance = 5, eliteHp = 5, eliteAtk = 1.5, eliteReward = 5, eliteGear = 3;
    /** 경직 (EF 방식): 충격 - 경직 방어 = 남는 충격. shortMin 이상 짧은 경직, longMin 이상 긴 경직 */
    public static double stunShortMin = 0.5, stunLongMin = 1.4, eliteStunArmor = 0.8;
    public static int stunShortTicks = 6, stunLongTicks = 12, stunChain = 4, stunChainWindow = 40, stunImmuneTicks = 30;

    public record MobDef(String key, String entity, String name, java.util.TreeMap<Integer, MobStats> levels,
                         MobLook look, java.util.List<MobSkill> skills, java.util.List<MobAttack> attacks, double stunArmor) {
        /** 그 레벨 수치 (표에 없으면 바로 아래 레벨, 그것도 없으면 가장 낮은 레벨) */
        public MobStats at(int level) {
            var e = levels.floorEntry(level);
            if (e == null) e = levels.firstEntry();
            return e == null ? new MobStats(10, 1, 1, 1) : e.getValue();
        }
    }

    public static long expNeed(int level) {
        if (level >= maxLevel) return Long.MAX_VALUE;
        return Math.max(1L, Math.round(expBase * Math.pow(level, expPower) + expLinear * level));
    }

    private static final Gson GSON = new GsonBuilder().create();

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent e) {
        e.addListener(new Listener());
    }

    static class Listener extends SimpleJsonResourceReloadListener {
        Listener() { super(GSON, "rpg_balance"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
            loadMobs(files.get(RpgCore.id("mobs")));
            JsonElement sk = files.get(RpgCore.id("skills")), pa = files.get(RpgCore.id("passives"));
            rpg.core.skill.Defs.parse(sk == null ? "{}" : sk.toString(), pa == null ? "{}" : pa.toString());
            JsonElement eqj = files.get(RpgCore.id("equipment"));
            rpg.core.equip.GearDefs.parse(eqj == null ? "{}" : eqj.toString());
            JsonElement dr = files.get(RpgCore.id("drops"));
            rpg.core.loot.Drops.parse(dr == null ? "{}" : dr.toString());
            JsonElement sh = files.get(RpgCore.id("shops"));
            rpg.core.shop.Shops.parse(sh == null ? "{}" : sh.toString());
            JsonElement cr = files.get(RpgCore.id("crafts"));
            rpg.core.craft.Crafts.parse(cr == null ? "{}" : cr.toString());
            JsonElement dq = files.get(RpgCore.id("dailies"));
            rpg.core.quest.Dailies.parse(dq == null ? "{}" : dq.toString());
            JsonElement ac = files.get(RpgCore.id("achievements"));
            rpg.core.quest.Achievements.parse(ac == null ? "{}" : ac.toString());
            JsonElement qu = files.get(RpgCore.id("quests"));
            rpg.core.quest.Quests.parse(qu == null ? "{}" : qu.toString());
            JsonElement el = files.get(RpgCore.id("player"));
            if (el == null || !el.isJsonObject()) {
                RpgCore.LOG.warn("[rpgcore] rpg_balance/player.json 없음 — 기본값 사용");
                return;
            }
            JsonObject o = el.getAsJsonObject();
            startStat = i(o, "startStat", startStat);
            startStamina = i(o, "startStamina", startStamina); staminaPerPoint = d(o, "staminaPerPoint", staminaPerPoint);
            statPointsPerLevel = i(o, "statPointsPerLevel", statPointsPerLevel);
            maxLevel = i(o, "maxLevel", maxLevel);
            hpBase = d(o, "hpBase", hpBase); hpPerLevel = d(o, "hpPerLevel", hpPerLevel); hpPerVit = d(o, "hpPerVit", hpPerVit);
            mpBase = d(o, "mpBase", mpBase); mpPerLevel = d(o, "mpPerLevel", mpPerLevel); mpPerInt = d(o, "mpPerInt", mpPerInt);
            hpRegenPct = d(o, "hpRegenPercent", hpRegenPct); mpRegenPct = d(o, "mpRegenPercent", mpRegenPct);
            mageMpRegenPct = d(o, "mageMpRegenPercent", mageMpRegenPct); bossPotionCooldown = d(o, "bossPotionCooldown", bossPotionCooldown);
            if (o.has("mpPerLevelByJob")) { var a = o.getAsJsonArray("mpPerLevelByJob"); for (int i = 0; i < Math.min(5, a.size()); i++) mpPerLevelByJob[i] = a.get(i).getAsDouble(); }
            regenInterval = d(o, "regenIntervalSec", regenInterval);
            critBase = d(o, "critBase", critBase); critPerDex = d(o, "critPerDex", critPerDex);
            healPerInt = d(o, "healPerInt", healPerInt); dropPerLuk = d(o, "dropPerLuk", dropPerLuk); goldPerLuk = d(o, "goldPerLuk", goldPerLuk);
            expBase = d(o, "expBase", expBase); expPower = d(o, "expPower", expPower); expLinear = d(o, "expLinear", expLinear);
            if (o.has("masteryByJob")) { var a = o.getAsJsonArray("masteryByJob"); for (int i = 0; i < Math.min(5, a.size()); i++) masteryByJob[i] = a.get(i).getAsDouble(); }
            if (o.has("basicAttackPct")) { var a = o.getAsJsonArray("basicAttackPct"); for (int i = 0; i < Math.min(5, a.size()); i++) basicAttackPct[i] = a.get(i).getAsDouble(); }
            mastery = d(o, "mastery", mastery); critDamage = d(o, "critDamage", critDamage); fistAttack = d(o, "fistAttack", fistAttack);
            skillPointsPerLevel = i(o, "skillPointsPerLevel", skillPointsPerLevel);
            passivePointsPerLevel = i(o, "passivePointsPerLevel", passivePointsPerLevel);
            dodgeCooldown = d(o, "dodgeCooldown", dodgeCooldown); dodgePower = d(o, "dodgePower", dodgePower);
            dodgeIframes = i(o, "dodgeIframes", dodgeIframes);
            jobLevel1 = i(o, "jobLevel1", jobLevel1); jobRewardPotions = i(o, "jobRewardPotions", jobRewardPotions);
            jobLevel2 = i(o, "jobLevel2", jobLevel2); jobReward2Potions = i(o, "jobReward2Potions", jobReward2Potions);
            if (o.has("potions") && o.get("potions").isJsonObject()) {
                POTIONS.clear();
                for (var pe : o.getAsJsonObject("potions").entrySet()) {
                    if (!pe.getValue().isJsonObject()) continue;
                    JsonObject po = pe.getValue().getAsJsonObject();
                    POTIONS.put(pe.getKey(), new PotionDef(d(po, "hp", 0), d(po, "mp", 0), d(po, "hpPct", 0), d(po, "mpPct", 0), d(po, "cooldown", 0)));
                }
            }
            potionCooldown = d(o, "potionCooldown", potionCooldown); rangedRange = d(o, "rangedRange", rangedRange);
            partyMax = i(o, "partyMax", partyMax); partyRange = d(o, "partyRange", partyRange);
            partyBonusPerMember = d(o, "partyBonusPerMember", partyBonusPerMember);
            deathExpLossPct = d(o, "deathExpLossPct", deathExpLossPct);
            downEverywhere = o.has("downEverywhere") ? o.get("downEverywhere").getAsBoolean() : downEverywhere;
            downSeconds = d(o, "downSeconds", downSeconds); reviveSeconds = d(o, "reviveSeconds", reviveSeconds);
            reviveHpPct = d(o, "reviveHpPct", reviveHpPct);
            defPerVit = d(o, "defPerVit", defPerVit); defBase = d(o, "defBase", defBase); defPerMobLevel = d(o, "defPerMobLevel", defPerMobLevel);
            huntingDimensions = new java.util.HashSet<>();
            if (o.has("huntingDimensions")) o.getAsJsonArray("huntingDimensions").forEach(x -> huntingDimensions.add(x.getAsString()));
            RpgCore.LOG.info("[rpgcore] 밸런스 수치 읽음");
        }

        private static void loadMobs(JsonElement el) {
            MOBS.clear();
            MOB_DEF.clear();
            rpg.core.combat.Boss.clear();
            if (el == null || !el.isJsonObject()) {
                RpgCore.LOG.warn("[rpgcore] rpg_balance/mobs.json 없음");
                return;
            }
            JsonObject root = el.getAsJsonObject();
            GROUNDS.clear();
            if (root.has("_grounds")) for (var g : root.getAsJsonObject("_grounds").entrySet()) {
                if (!g.getValue().isJsonObject()) continue;
                JsonObject o = g.getValue().getAsJsonObject();
                java.util.List<String> ms = new java.util.ArrayList<>();
                o.getAsJsonArray("mobs").forEach(x -> ms.add(x.getAsString()));
                GROUNDS.put(g.getKey(), new Ground(o.get("name").getAsString(), ms, (int) d(o, "min", 1), (int) d(o, "max", 1)));
            }
            if (root.has("_stun")) {
                JsonObject o = root.getAsJsonObject("_stun");
                stunShortMin = d(o, "shortMin", 0.5); stunLongMin = d(o, "longMin", 1.4); eliteStunArmor = d(o, "eliteArmor", 0.8);
                stunShortTicks = (int) d(o, "shortTicks", 6); stunLongTicks = (int) d(o, "longTicks", 12);
                stunChain = (int) d(o, "chain", 4); stunChainWindow = (int) d(o, "chainWindow", 40); stunImmuneTicks = (int) d(o, "immuneTicks", 30);
            }
            if (root.has("_elite")) {
                JsonObject o = root.getAsJsonObject("_elite");
                eliteChance = d(o, "chance", 5); eliteHp = d(o, "hp", 5); eliteAtk = d(o, "atk", 1.5);
                eliteReward = d(o, "reward", 5); eliteGear = d(o, "gear", 3);
            }
            for (var e : root.entrySet()) {
                if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) continue;
                JsonObject m = e.getValue().getAsJsonObject();
                var levels = new java.util.TreeMap<Integer, MobStats>();
                if (m.has("levels")) {
                    for (var lv : m.getAsJsonObject("levels").entrySet()) {
                        JsonObject r = lv.getValue().getAsJsonObject();
                        try {
                            levels.put(Integer.parseInt(lv.getKey()), new MobStats(d(r, "hp", 10), d(r, "atk", 1),
                                    (long) d(r, "exp", 1), (long) d(r, "gold", 0)));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                String ent = m.has("entity") ? m.get("entity").getAsString() : "minecraft:zombie";
                String name = m.has("name") ? m.get("name").getAsString() : e.getKey();
                MobLook look = new MobLook(m.has("head") ? m.get("head").getAsString() : "",
                        m.has("baby") && m.get("baby").getAsBoolean(), m.has("passive") && m.get("passive").getAsBoolean(),
                        m.has("chase") && m.get("chase").getAsBoolean());
                java.util.List<MobSkill> skills = new java.util.ArrayList<>();
                if (m.has("skills")) for (var sk : m.getAsJsonArray("skills")) {
                    JsonObject o = sk.getAsJsonObject();
                    skills.add(new MobSkill(o.has("type") ? o.get("type").getAsString() : "slam", d(o, "range", 4), d(o, "cd", 6),
                            d(o, "delay", 1), d(o, "pct", 100), d(o, "r", 3), d(o, "len", 8), d(o, "w", 1.5), d(o, "angle", 90),
                            (int) d(o, "hits", 1), o.has("effect") ? o.get("effect").getAsString() : "",
                            d(o, "hpBelow", 0), (int) d(o, "count", 1)));
                }
                java.util.List<MobAttack> attacks = new java.util.ArrayList<>();
                if (m.has("attacks")) for (var at : m.getAsJsonArray("attacks")) {
                    JsonObject o = at.getAsJsonObject();
                    attacks.add(new MobAttack(o.has("anim") ? o.get("anim").getAsString() : "attack",
                            (int) d(o, "start", 4), (int) d(o, "end", 7), (int) d(o, "length", 12),
                            d(o, "range", 0.8), d(o, "width", 1), d(o, "angle", 0), d(o, "knock", 0), d(o, "lunge", 0.35)));
                }
                if (m.has("defRate")) MOB_DEF.put(e.getKey(), m.get("defRate").getAsDouble());
                MOBS.put(e.getKey(), new MobDef(e.getKey(), ent, name, levels, look, skills, attacks, d(m, "stunArmor", 0)));
                if (m.has("boss")) rpg.core.combat.Boss.define(e.getKey(), m.getAsJsonObject("boss"));   // 보스 패턴·보상
            }
            RpgCore.LOG.info("[rpgcore] 몹 {}종 읽음", MOBS.size());
        }

        private static int i(JsonObject o, String k, int def) { return o.has(k) ? o.get(k).getAsInt() : def; }
        private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }
    }
}
