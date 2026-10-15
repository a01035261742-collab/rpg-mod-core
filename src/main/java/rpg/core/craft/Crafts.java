package rpg.core.craft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.equip.Gear;
import rpg.core.net.Net;

/**
 * NPC 제작 (crafts.json): 제작 NPC 우클릭 → 제작 창. 재료(인벤토리) + 골드 → 결과.
 * 장비는 추가 옵션 고정 줄 수(제작·드롭 장비 규칙)로 나옴. 작업대 레시피는 없음.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Crafts {
    private Crafts() {}

    public static final String NPC_TAG = "rpgCraft";
    public record Mat(String item, int count) {}
    public record Recipe(String tab, String result, int count, long gold, List<Mat> mats) {}
    public record Station(String id, String name, List<String> tabs, List<Recipe> recipes) {}

    public static final Map<String, Station> STATIONS = new LinkedHashMap<>();

    public static void parse(String text) {
        STATIONS.clear();
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (o.has("stations")) for (var e : o.getAsJsonObject("stations").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                JsonObject s = e.getValue().getAsJsonObject();
                List<String> tabs = new ArrayList<>();
                if (s.has("tabs")) s.getAsJsonArray("tabs").forEach(x -> tabs.add(x.getAsString()));
                STATIONS.put(e.getKey(), new Station(e.getKey(), s.has("name") ? s.get("name").getAsString() : e.getKey(), tabs, new ArrayList<>()));
            }
            int n = 0;
            if (o.has("recipes")) for (var x : o.getAsJsonArray("recipes")) {
                JsonObject r = x.getAsJsonObject();
                Station st = STATIONS.get(r.get("station").getAsString());
                if (st == null) continue;
                List<Mat> mats = new ArrayList<>();
                for (var m : r.getAsJsonArray("mats")) {
                    var a = m.getAsJsonArray();
                    mats.add(new Mat(full(a.get(0).getAsString()), a.get(1).getAsInt()));
                }
                String tab = r.has("tab") ? r.get("tab").getAsString() : (st.tabs().isEmpty() ? "" : st.tabs().get(0));
                st.recipes().add(new Recipe(tab, full(r.get("result").getAsString()), r.has("count") ? r.get("count").getAsInt() : 1,
                        r.has("gold") ? r.get("gold").getAsLong() : 0, mats));
                n++;
            }
            RpgCore.LOG.info("[rpgcore] 제작 NPC {}종, 레시피 {}개 읽음", STATIONS.size(), n);
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] crafts.json 읽기 실패: {}", ex.toString());
        }
    }

    private static String full(String id) {
        return id.contains(":") ? id : RpgCore.MODID + ":" + id;
    }

    private static Item item(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    // ───────── NPC ─────────
    public static String npcStation(Entity e) {
        return e instanceof Villager ? e.getPersistentData().getString(NPC_TAG) : "";
    }

    public static void spawn(ServerPlayer p, String id) {
        Station s = STATIONS.get(id);
        Villager v = new Villager(EntityType.VILLAGER, p.level());
        v.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, 0);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistenceRequired();
        v.getPersistentData().putString(NPC_TAG, id);
        v.setCustomName(Component.literal(s == null ? id : s.name()).withStyle(ChatFormatting.GOLD));
        v.setCustomNameVisible(true);
        p.level().addFreshEntity(v);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void talk(PlayerInteractEvent.EntityInteract e) {
        String id = npcStation(e.getTarget());
        if (id.isEmpty()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Station s = STATIONS.get(id);
        if (s == null) {
            sp.sendSystemMessage(Component.literal("[제작] crafts.json 에 '" + id + "' 이(가) 없어요").withStyle(ChatFormatting.RED));
            return;
        }
        Net.openCraft(sp, e.getTarget().getId(), s);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void invulnerable(LivingAttackEvent e) {
        if (!npcStation(e.getEntity()).isEmpty()) e.setCanceled(true);
    }

    // ───────── 제작 ─────────
    private static int have(ServerPlayer p, Item it) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
        return n;
    }

    private static void take(ServerPlayer p, Item it, int n) {
        for (ItemStack s : p.getInventory().items) {
            if (n <= 0) return;
            if (!s.is(it)) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
    }

    /** times 번 만들기 (재료·골드가 모자라면 거기서 멈춤). 장비는 한 번에 1개 */
    public static void craft(ServerPlayer p, int npcId, int index, int times) {
        Entity e = p.level().getEntity(npcId);
        if (e == null || e.distanceToSqr(p) > 64) return;
        Station st = STATIONS.get(npcStation(e));
        if (st == null || index < 0 || index >= st.recipes().size()) return;
        Recipe r = st.recipes().get(index);
        Item out = item(r.result());
        if (out == null) return;
        boolean gear = Gear.def(new ItemStack(out)) != null;
        if (gear) times = 1;
        times = Math.max(1, Math.min(times, 64));
        PlayerData d = RpgData.get(p);
        int made = 0;
        String fail = null;
        for (int t = 0; t < times; t++) {
            for (Mat m : r.mats()) {
                Item it = item(m.item());
                if (it == null || have(p, it) < m.count()) { fail = "재료가 부족해요"; break; }
            }
            if (fail != null) break;
            if (d.gold < r.gold()) { fail = "골드가 부족해요 (" + String.format("%,d", r.gold()) + " G 필요)"; break; }
            if (gear && p.getInventory().getFreeSlot() < 0) { fail = "인벤토리에 빈칸이 없어요"; break; }
            for (Mat m : r.mats()) take(p, item(m.item()), m.count());
            d.gold -= r.gold();
            ItemStack res = new ItemStack(out, r.count());
            if (gear) Gear.roll(res);   // 제작 장비: 추가 옵션 고정 줄 수
            if (!p.getInventory().add(res) && !res.isEmpty()) p.drop(res, false);
            made++;
        }
        RpgData.sync(p);
        p.inventoryMenu.broadcastChanges();
        if (made > 0) {
            String name = out.getDescription().getString();
            msg(p, name + (r.count() * made > 1 ? " x" + r.count() * made : "") + " 제작" + (r.gold() > 0 ? "  -" + String.format("%,d", r.gold() * made) + " G" : ""),
                    ChatFormatting.YELLOW);
            p.level().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.6f, 1.1f);
        } else if (fail != null) {
            msg(p, fail, ChatFormatting.RED);
        }
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }
}
