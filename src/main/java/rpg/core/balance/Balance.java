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
    public static double hpRegenPct = 1.0, mpRegenPct = 2.0;
    public static double critBase = 5.0, critPerDex = 0.1, healPerInt = 0.5, dropPerLuk = 0.2, goldPerLuk = 0.3;
    public static double expBase = 15, expPower = 2.0, expLinear = 30;
    public static int skillPointsPerLevel = 2, passivePointsPerLevel = 1;
    public static int jobLevel1 = 10, jobRewardPotions = 10;
    public static double redPotionHp = 60, bluePotionMp = 40, potionCooldown = 1.0, rangedRange = 20;
    public static int partyMax = 6;
    public static double partyRange = 40, partyBonusPerMember = 10, deathExpLossPct = 30;
    public static boolean downEverywhere = true;
    public static double downSeconds = 30, reviveSeconds = 3, reviveHpPct = 30;
    public static double dodgeCooldown = 3.0, dodgePower = 1.1;
    public static int dodgeIframes = 8;
    public static double mastery = 0.8, critDamage = 1.5, fistAttack = 5, defPerVit = 1.0, defConstant = 100;
    public static java.util.Set<String> huntingDimensions = new java.util.HashSet<>();

    /** 몹 표: 키 → 몹 정의 */
    public static final Map<String, MobDef> MOBS = new java.util.LinkedHashMap<>();

    public record MobStats(double hp, double atk, long exp, long gold) {}

    public record MobDef(String key, String entity, String name, java.util.TreeMap<Integer, MobStats> levels) {
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
            hpRegenPct = d(o, "hpRegenPercentPerSec", hpRegenPct); mpRegenPct = d(o, "mpRegenPercentPerSec", mpRegenPct);
            critBase = d(o, "critBase", critBase); critPerDex = d(o, "critPerDex", critPerDex);
            healPerInt = d(o, "healPerInt", healPerInt); dropPerLuk = d(o, "dropPerLuk", dropPerLuk); goldPerLuk = d(o, "goldPerLuk", goldPerLuk);
            expBase = d(o, "expBase", expBase); expPower = d(o, "expPower", expPower); expLinear = d(o, "expLinear", expLinear);
            mastery = d(o, "mastery", mastery); critDamage = d(o, "critDamage", critDamage); fistAttack = d(o, "fistAttack", fistAttack);
            skillPointsPerLevel = i(o, "skillPointsPerLevel", skillPointsPerLevel);
            passivePointsPerLevel = i(o, "passivePointsPerLevel", passivePointsPerLevel);
            dodgeCooldown = d(o, "dodgeCooldown", dodgeCooldown); dodgePower = d(o, "dodgePower", dodgePower);
            dodgeIframes = i(o, "dodgeIframes", dodgeIframes);
            jobLevel1 = i(o, "jobLevel1", jobLevel1); jobRewardPotions = i(o, "jobRewardPotions", jobRewardPotions);
            redPotionHp = d(o, "redPotionHp", redPotionHp); bluePotionMp = d(o, "bluePotionMp", bluePotionMp);
            potionCooldown = d(o, "potionCooldown", potionCooldown); rangedRange = d(o, "rangedRange", rangedRange);
            partyMax = i(o, "partyMax", partyMax); partyRange = d(o, "partyRange", partyRange);
            partyBonusPerMember = d(o, "partyBonusPerMember", partyBonusPerMember);
            deathExpLossPct = d(o, "deathExpLossPct", deathExpLossPct);
            downEverywhere = o.has("downEverywhere") ? o.get("downEverywhere").getAsBoolean() : downEverywhere;
            downSeconds = d(o, "downSeconds", downSeconds); reviveSeconds = d(o, "reviveSeconds", reviveSeconds);
            reviveHpPct = d(o, "reviveHpPct", reviveHpPct);
            defPerVit = d(o, "defPerVit", defPerVit); defConstant = d(o, "defConstant", defConstant);
            huntingDimensions = new java.util.HashSet<>();
            if (o.has("huntingDimensions")) o.getAsJsonArray("huntingDimensions").forEach(x -> huntingDimensions.add(x.getAsString()));
            RpgCore.LOG.info("[rpgcore] 밸런스 수치 읽음");
        }

        private static void loadMobs(JsonElement el) {
            MOBS.clear();
            if (el == null || !el.isJsonObject()) {
                RpgCore.LOG.warn("[rpgcore] rpg_balance/mobs.json 없음");
                return;
            }
            for (var e : el.getAsJsonObject().entrySet()) {
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
                MOBS.put(e.getKey(), new MobDef(e.getKey(), ent, name, levels));
            }
            RpgCore.LOG.info("[rpgcore] 몹 {}종 읽음", MOBS.size());
        }

        private static int i(JsonObject o, String k, int def) { return o.has(k) ? o.get(k).getAsInt() : def; }
        private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }
    }
}
