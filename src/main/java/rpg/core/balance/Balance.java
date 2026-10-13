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
    public static int startStat = 4;
    public static int statPointsPerLevel = 5;
    public static int maxLevel = 200;
    public static double hpBase = 50, hpPerLevel = 10, hpPerVit = 5;
    public static double mpBase = 30, mpPerLevel = 4, mpPerInt = 2;
    public static double hpRegenPct = 1.0, mpRegenPct = 2.0;
    public static double critBase = 5.0, critPerDex = 0.1, healPerInt = 0.5, dropPerLuk = 0.2, goldPerLuk = 0.3;
    public static double expBase = 15, expPower = 2.0, expLinear = 30;

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
            JsonElement el = files.get(RpgCore.id("player"));
            if (el == null || !el.isJsonObject()) {
                RpgCore.LOG.warn("[rpgcore] rpg_balance/player.json 없음 — 기본값 사용");
                return;
            }
            JsonObject o = el.getAsJsonObject();
            startStat = i(o, "startStat", startStat);
            statPointsPerLevel = i(o, "statPointsPerLevel", statPointsPerLevel);
            maxLevel = i(o, "maxLevel", maxLevel);
            hpBase = d(o, "hpBase", hpBase); hpPerLevel = d(o, "hpPerLevel", hpPerLevel); hpPerVit = d(o, "hpPerVit", hpPerVit);
            mpBase = d(o, "mpBase", mpBase); mpPerLevel = d(o, "mpPerLevel", mpPerLevel); mpPerInt = d(o, "mpPerInt", mpPerInt);
            hpRegenPct = d(o, "hpRegenPercentPerSec", hpRegenPct); mpRegenPct = d(o, "mpRegenPercentPerSec", mpRegenPct);
            critBase = d(o, "critBase", critBase); critPerDex = d(o, "critPerDex", critPerDex);
            healPerInt = d(o, "healPerInt", healPerInt); dropPerLuk = d(o, "dropPerLuk", dropPerLuk); goldPerLuk = d(o, "goldPerLuk", goldPerLuk);
            expBase = d(o, "expBase", expBase); expPower = d(o, "expPower", expPower); expLinear = d(o, "expLinear", expLinear);
            RpgCore.LOG.info("[rpgcore] 밸런스 수치 읽음");
        }

        private static int i(JsonObject o, String k, int def) { return o.has(k) ? o.get(k).getAsInt() : def; }
        private static double d(JsonObject o, String k, double def) { return o.has(k) ? o.get(k).getAsDouble() : def; }
    }
}
