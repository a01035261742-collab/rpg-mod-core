package rpg.core.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.RpgCore;
import rpg.core.combat.MobData;
import rpg.core.data.PlayerData;
import rpg.core.equip.Gear;
import rpg.core.equip.GearDefs;
import rpg.core.item.ModItems;

/** 몹 드롭 (drops.json). 확률은 % 이고 운(LUK) 드롭률 보너스가 곱해짐. 골드 주머니는 보너스 없이 항상 */
public final class Drops {
    private Drops() {}

    public record Entry(String item, double chance) {}
    public record MobDrops(List<Entry> items, double potion, double gear, double enhance) {}

    public static double ownerSeconds = 30;
    public static double goldChance = 100;
    public static double potionChance = 8, potionRed = 70;
    public static double gearChance = 2;
    public static int gearLevelRange = 10;
    public static double enhanceChance = 1.5;
    public static final Map<String, Double> ENHANCE = new LinkedHashMap<>();
    public static final Map<String, MobDrops> MOBS = new HashMap<>();

    public static void parse(String text) {
        MOBS.clear();
        ENHANCE.clear();
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            ownerSeconds = d(o, "ownerSeconds", 30);
            goldChance = d(o, "goldChance", 100);
            if (o.has("potion")) { JsonObject p = o.getAsJsonObject("potion"); potionChance = d(p, "chance", 8); potionRed = d(p, "redRatio", 70); }
            if (o.has("gear")) { JsonObject g = o.getAsJsonObject("gear"); gearChance = d(g, "chance", 2); gearLevelRange = (int) d(g, "levelRange", 10); }
            if (o.has("enhance")) {
                JsonObject en = o.getAsJsonObject("enhance");
                enhanceChance = d(en, "chance", 1.5);
                if (en.has("items")) for (var e : en.getAsJsonObject("items").entrySet()) ENHANCE.put(e.getKey(), e.getValue().getAsDouble());
            }
            if (o.has("mobs")) for (var e : o.getAsJsonObject("mobs").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject m = e.getValue().getAsJsonObject();
                List<Entry> list = new ArrayList<>();
                if (m.has("items")) for (var x : m.getAsJsonArray("items")) {
                    JsonArray a = x.getAsJsonArray();
                    list.add(new Entry(a.get(0).getAsString(), a.get(1).getAsDouble()));
                }
                MOBS.put(e.getKey(), new MobDrops(list, d(m, "potion", -1), d(m, "gear", -1), d(m, "enhance", -1)));
            }
            RpgCore.LOG.info("[rpgcore] 드롭표 {}종 읽음", MOBS.size());
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] drops.json 읽기 실패: {}", ex.toString());
        }
    }

    private static double d(JsonObject o, String k, double def) {
        return o.has(k) ? o.get(k).getAsDouble() : def;
    }

    /** 처치 시 막타 플레이어 기준으로 굴림 */
    public static void roll(Mob mob, MobData m, ServerPlayer killer, PlayerData d) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double mul = 1.0 + d.dropBonus() / 100.0;
        List<ItemStack> out = new ArrayList<>();

        // 골드 주머니
        long gold = Math.round(m.gold * (1.0 + d.goldBonus() / 100.0) * (0.85 + r.nextDouble() * 0.3));
        if (gold > 0 && r.nextDouble() * 100 < goldChance) out.add(GoldPouch.of(gold));

        MobDrops md = MOBS.get(m.key);
        int rolls = m.elite ? 2 : 1;   // 엘리트는 재료 두 번 굴림
        for (int k = 0; k < rolls; k++) if (md != null) for (Entry e : md.items()) if (hit(r, e.chance() * mul)) add(out, e.item());

        double pc = md != null && md.potion() >= 0 ? md.potion() : potionChance;
        if (hit(r, pc * mul)) out.add(new ItemStack(r.nextDouble() * 100 < potionRed ? ModItems.RED_POTION.get() : ModItems.BLUE_POTION.get()));

        double gc = (md != null && md.gear() >= 0 ? md.gear() : gearChance) * (m.elite ? rpg.core.balance.Balance.eliteGear : 1);
        if (hit(r, gc * mul)) {
            ItemStack g = randomGear(m.level, r);
            if (!g.isEmpty()) out.add(g);
        }

        double ec = md != null && md.enhance() >= 0 ? md.enhance() : enhanceChance;
        if (!ENHANCE.isEmpty() && hit(r, ec * mul)) add(out, weighted(ENHANCE, r));

        for (ItemStack s : out) spawn(mob, s, killer);
    }

    private static boolean hit(ThreadLocalRandom r, double pct) {
        return r.nextDouble() * 100 < pct;
    }

    private static void add(List<ItemStack> out, String id) {
        ResourceLocation rl = id.contains(":") ? new ResourceLocation(id) : RpgCore.id(id);
        Item it = ForgeRegistries.ITEMS.getValue(rl);
        if (it != null && it != net.minecraft.world.item.Items.AIR) out.add(new ItemStack(it));
        else RpgCore.LOG.warn("[rpgcore] 드롭 아이템 없음: {}", id);
    }

    private static String weighted(Map<String, Double> w, ThreadLocalRandom r) {
        double total = 0;
        for (double v : w.values()) total += v;
        double pick = r.nextDouble() * total;
        for (var e : w.entrySet()) { pick -= e.getValue(); if (pick < 0) return e.getKey(); }
        return w.keySet().iterator().next();
    }

    /** 몹 레벨 이하, levelRange 안쪽 장비 중 무작위 (추가 옵션 굴림) */
    private static ItemStack randomGear(int lv, ThreadLocalRandom r) {
        List<String> pool = new ArrayList<>();
        for (GearDefs.GearDef g : GearDefs.ITEMS.values()) if (g.level() <= lv && g.level() > lv - gearLevelRange) pool.add(g.id());
        if (pool.isEmpty()) for (GearDefs.GearDef g : GearDefs.ITEMS.values()) if (g.level() <= lv) pool.add(g.id());
        if (pool.isEmpty()) return ItemStack.EMPTY;
        Item it = ForgeRegistries.ITEMS.getValue(RpgCore.id(pool.get(r.nextInt(pool.size()))));
        if (it == null) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(it);
        Gear.roll(s);
        return s;
    }

    private static void spawn(Mob mob, ItemStack s, ServerPlayer owner) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ItemEntity ie = new ItemEntity(mob.level(), mob.getX(), mob.getY() + 0.5, mob.getZ(), s,
                (r.nextDouble() - 0.5) * 0.25, 0.25, (r.nextDouble() - 0.5) * 0.25);
        ie.setPickUpDelay(10);
        Loot.claim(ie, owner, ownerSeconds);
        mob.level().addFreshEntity(ie);
    }

    public static Component ownerTag(ServerPlayer p) {
        return Component.literal("◆ " + p.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW);
    }
}
