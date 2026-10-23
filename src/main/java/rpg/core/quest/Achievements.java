package rpg.core.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 업적 (achievements.json): 조건을 채우면 한 번만 골드·훈장(칭호) 지급. 창은 없음 — /rpg ach 로 진행도 확인.
 * 저장: PERSISTED_NBT "rpgAch" {done{id:true}, kills, destroy, bestStar, boss{key:true}}
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Achievements {
    private Achievements() {}

    public record Ach(String id, String name, String type, long need, long gold, String medal) {}
    public static final Map<String, Ach> ACHS = new LinkedHashMap<>();

    public static void parse(String text) {
        ACHS.clear();
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            for (var e : o.entrySet()) {
                if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) continue;
                JsonObject a = e.getValue().getAsJsonObject();
                ACHS.put(e.getKey(), new Ach(e.getKey(), a.has("name") ? a.get("name").getAsString() : e.getKey(),
                        a.get("type").getAsString(), a.has("need") ? a.get("need").getAsLong() : 1,
                        a.has("gold") ? a.get("gold").getAsLong() : 0, a.has("medal") ? a.get("medal").getAsString() : ""));
            }
        } catch (Exception ex) {
            RpgCore.LOG.warn("[rpgcore] achievements.json 읽기 실패: {}", ex.toString());
        }
    }

    // ───────── 저장 ─────────
    private static CompoundTag data(ServerPlayer p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("rpgAch");
    }

    private static void save(ServerPlayer p, CompoundTag a) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        keep.put("rpgAch", a);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
    }

    /** 현재 진행값 */
    public static long value(ServerPlayer p, String type) {
        CompoundTag a = data(p);
        PlayerData d = RpgData.get(p);
        return switch (type) {
            case "level" -> d.level;
            case "kills" -> a.getLong("kills");
            case "star" -> a.getInt("bestStar");
            case "destroy" -> a.getInt("destroy");
            case "bossKinds" -> a.getCompound("boss").size();
            case "gold" -> d.gold;
            default -> 0;
        };
    }

    // ───────── 이벤트 ─────────
    public static void onKill(ServerPlayer p) {
        CompoundTag a = data(p);
        a.putLong("kills", a.getLong("kills") + 1);
        save(p, a);
        check(p, "kills");
    }

    public static void onStar(ServerPlayer p, int star) {
        CompoundTag a = data(p);
        if (star <= a.getInt("bestStar")) return;
        a.putInt("bestStar", star);
        save(p, a);
        check(p, "star");
    }

    public static void onDestroy(ServerPlayer p) {
        CompoundTag a = data(p);
        a.putInt("destroy", a.getInt("destroy") + 1);
        save(p, a);
        check(p, "destroy");
    }

    public static void onBoss(ServerPlayer p, String key) {
        CompoundTag a = data(p);
        CompoundTag b = a.getCompound("boss");
        if (b.getBoolean(key)) return;
        b.putBoolean(key, true);
        a.put("boss", b);
        save(p, a);
        check(p, "bossKinds");
    }

    /** 레벨 · 골드: 5초마다 확인 */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 100 != 0) return;
        check(p, "level");
        check(p, "gold");
    }

    private static void check(ServerPlayer p, String type) {
        for (Ach ac : ACHS.values()) {
            if (!ac.type().equals(type)) continue;
            CompoundTag a = data(p);
            CompoundTag done = a.getCompound("done");
            if (done.getBoolean(ac.id()) || value(p, type) < ac.need()) continue;
            done.putBoolean(ac.id(), true);
            a.put("done", done);
            save(p, a);
            grant(p, ac);
        }
    }

    private static void grant(ServerPlayer p, Ach ac) {
        StringBuilder sb = new StringBuilder("[업적 달성] " + ac.name());
        if (ac.gold() > 0) {
            RpgData.get(p).gold += ac.gold();
            RpgData.sync(p);
            sb.append(" · ").append(String.format("%,d", ac.gold())).append(" G");
        }
        if (!ac.medal().isEmpty()) {
            ItemStack medal = rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + ac.medal(), 1);
            if (!medal.isEmpty()) {
                rpg.core.equip.Gear.rollPlain(medal);
                sb.append(" · ").append(medal.getHoverName().getString());
                if (!p.getInventory().add(medal)) p.drop(medal, false);
            }
        }
        p.sendSystemMessage(Component.literal(sb.toString()).withStyle(ChatFormatting.GOLD));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7f, 1f);
        if (p.getServer() != null)
            p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(p.getGameProfile().getName() + " 님이 업적 [" + ac.name() + "] 달성!")
                    .withStyle(ChatFormatting.YELLOW), false);
    }

    /** /rpg ach: 진행도 */
    public static void show(ServerPlayer p) {
        CompoundTag done = data(p).getCompound("done");
        p.sendSystemMessage(Component.literal("── 업적 ──").withStyle(ChatFormatting.YELLOW));
        for (Ach ac : ACHS.values()) {
            boolean ok = done.getBoolean(ac.id());
            long v = Math.min(value(p, ac.type()), ac.need());
            String rw = (ac.gold() > 0 ? String.format("%,d G", ac.gold()) : "")
                    + (ac.medal().isEmpty() ? "" : rpg.core.item.ItemIds.stack(RpgCore.MODID + ":" + ac.medal(), 1).getHoverName().getString());
            p.sendSystemMessage(Component.literal((ok ? "§a✔ " : "§7· ") + ac.name() + " §f" + String.format("%,d/%,d", v, ac.need()) + " §8(" + rw + ")"));
        }
    }
}
