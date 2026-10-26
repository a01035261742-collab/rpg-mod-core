package rpg.core.shop;

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
import rpg.core.item.Material;
import rpg.core.net.Net;

/** 상점 NPC (shops.json): 우클릭 → 상점 창. 사기 = 상점 목록, 팔기 = sell 가격표에 있는 것만 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Shops {
    private Shops() {}

    public static final String NPC_TAG = "rpgShop";
    public record Offer(String item, long price) {}
    public record Shop(String id, String name, List<Offer> offers) {}

    public static final Map<String, Shop> SHOPS = new LinkedHashMap<>();
    public static final Map<String, Long> SELL = new LinkedHashMap<>();

    public static void parse(String text) {
        SHOPS.clear();
        SELL.clear();
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (o.has("sell")) for (var e : o.getAsJsonObject("sell").entrySet()) {
                if (!e.getKey().startsWith("_")) SELL.put(full(e.getKey()), e.getValue().getAsLong());
            }
            if (o.has("shops")) for (var e : o.getAsJsonObject("shops").entrySet()) {
                JsonObject s = e.getValue().getAsJsonObject();
                List<Offer> list = new ArrayList<>();
                if (s.has("items")) for (var x : s.getAsJsonArray("items")) {
                    var a = x.getAsJsonArray();
                    list.add(new Offer(full(a.get(0).getAsString()), a.get(1).getAsLong()));
                }
                SHOPS.put(e.getKey(), new Shop(e.getKey(), s.has("name") ? s.get("name").getAsString() : e.getKey(), list));
            }
            // 상점에서 파는 물건: 상점가 절반에 팔림 (물약·귀환 주문서는 1/10). 주문서(#)는 sell 표 그대로
            Map<String, Long> rule = new LinkedHashMap<>();
            for (Shop sh : SHOPS.values()) for (Offer of : sh.offers()) {
                if (of.item().contains("#")) continue;
                Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(of.item()));
                boolean cheap = it instanceof rpg.core.item.Potion || it instanceof rpg.core.item.ReturnScroll;
                rule.merge(of.item(), cheap ? of.price() / 10 : of.price() / 2, Math::min);
            }
            SELL.putAll(rule);
            RpgCore.LOG.info("[rpgcore] 상점 {}개, 판매가 {}개 읽음", SHOPS.size(), SELL.size());
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] shops.json 읽기 실패: {}", ex.toString());
        }
    }

    private static String full(String id) {
        return id.contains(":") ? id : RpgCore.MODID + ":" + id;
    }

    public static String idOf(ItemStack s) {
        return rpg.core.item.ItemIds.sellKey(s);
    }

    public static long sellPrice(ItemStack s) {
        return s.isEmpty() ? 0 : SELL.getOrDefault(idOf(s), 0L);
    }

    // ───────── NPC ─────────
    public static String npcShop(Entity e) {
        return e instanceof Villager ? e.getPersistentData().getString(NPC_TAG) : "";
    }

    public static boolean isNpc(Entity e) {
        return !npcShop(e).isEmpty();
    }

    public static void spawn(ServerPlayer p, String shopId) {
        Shop s = SHOPS.get(shopId);
        Villager v = new Villager(EntityType.VILLAGER, p.level());
        v.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, 0);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistenceRequired();
        v.getPersistentData().putString(NPC_TAG, shopId);
        v.setCustomName(Component.literal(s == null ? shopId : s.name()).withStyle(ChatFormatting.AQUA));
        v.setCustomNameVisible(true);
        p.level().addFreshEntity(v);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void talk(PlayerInteractEvent.EntityInteract e) {
        String id = npcShop(e.getTarget());
        if (id.isEmpty()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Shop s = SHOPS.get(id);
        if (s == null) {
            sp.sendSystemMessage(Component.literal("[상점] shops.json 에 '" + id + "' 상점이 없어요").withStyle(ChatFormatting.RED));
            return;
        }
        Net.openShop(sp, e.getTarget().getId(), s);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void invulnerable(LivingAttackEvent e) {
        if (isNpc(e.getEntity())) e.setCanceled(true);
    }

    /** 상점 NPC 근처(8칸)인지 확인하고 상점 반환 */
    private static Shop near(ServerPlayer p, int npcId) {
        Entity e = p.level().getEntity(npcId);
        if (e == null || !isNpc(e) || e.distanceToSqr(p) > 64) return null;
        return SHOPS.get(npcShop(e));
    }

    // ───────── 사기 ─────────
    public static void buy(ServerPlayer p, int npcId, int index, int count) {
        Shop s = near(p, npcId);
        if (s == null || index < 0 || index >= s.offers().size()) return;
        Offer o = s.offers().get(index);
        ItemStack st = rpg.core.item.ItemIds.stack(o.item(), 1);
        if (st.isEmpty()) return;
        boolean gear = Gear.def(st) != null;
        count = gear ? 1 : Math.max(1, Math.min(count, st.getMaxStackSize()));
        long cost = o.price() * count;
        PlayerData d = RpgData.get(p);
        if (d.gold < cost) {
            msg(p, "골드가 부족해요 (" + String.format("%,d", cost) + " G 필요)", ChatFormatting.RED);
            return;
        }
        if (gear) Gear.rollPlain(st);   // 상점 일반 장비: 추가 옵션 없음
        st.setCount(count);
        d.gold -= cost;
        if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
        RpgData.sync(p);
        msg(p, st.getHoverName().getString() + " x" + count + " 구매  -" + String.format("%,d", cost) + " G", ChatFormatting.YELLOW);
        p.level().playSound(null, p.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 0.5f, 1.2f);
    }

    // ───────── 팔기 ─────────
    /** slot = 인벤 칸 (0~35), all = 한 묶음 전부 */
    public static void sell(ServerPlayer p, int npcId, int slot, boolean all) {
        if (near(p, npcId) == null || slot < 0 || slot >= p.getInventory().items.size()) return;
        ItemStack st = p.getInventory().items.get(slot);
        long price = sellPrice(st);
        if (price <= 0) {
            msg(p, "이 아이템은 팔 수 없어요", ChatFormatting.RED);
            return;
        }
        int n = all ? st.getCount() : 1;
        String name = st.getHoverName().getString();
        st.shrink(n);
        give(p, price * n, name + " x" + n + " 판매");
    }

    /** 몹 재료 전부 팔기 */
    public static void sellMaterials(ServerPlayer p, int npcId) {
        if (near(p, npcId) == null) return;
        long total = 0;
        int n = 0;
        for (ItemStack st : p.getInventory().items) {
            if (st.getItem() instanceof Material m && m.mob) {
                long price = sellPrice(st);
                if (price <= 0) continue;
                total += price * st.getCount();
                n += st.getCount();
                st.setCount(0);
            }
        }
        if (n == 0) {
            msg(p, "팔 재료가 없어요", ChatFormatting.GRAY);
            return;
        }
        give(p, total, "재료 " + n + "개 판매");
    }

    private static void give(ServerPlayer p, long gold, String what) {
        PlayerData d = RpgData.get(p);
        d.gold += gold;
        RpgData.sync(p);
        p.inventoryMenu.broadcastChanges();
        Net.reward(p, 0, gold);
        msg(p, what + "  +" + String.format("%,d", gold) + " G", ChatFormatting.GOLD);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.4f);
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }
}
