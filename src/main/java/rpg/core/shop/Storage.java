package rpg.core.shop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 개인 창고: 창고지기 NPC 우클릭 → 상자 창 (플레이어마다 따로, 죽어도 유지).
 * 기본 18칸, Shift+우클릭을 두 번 (5초 안) 하면 골드로 9칸 확장 (최대 54칸).
 * 저장: 플레이어 영구 데이터 PERSISTED_NBT "rpgStorage" {size, items}
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Storage {
    private Storage() {}

    public static final String NPC_TAG = "rpgStorage";
    public static final int BASE = 18, MAX = 54;
    /** 확장 비용: 18→27, 27→36, 36→45, 45→54 */
    public static final long[] COST = {5000, 20000, 50000, 100000};
    private static final Map<UUID, Long> CONFIRM = new HashMap<>();

    public static void spawn(ServerPlayer p) {
        Villager v = new Villager(EntityType.VILLAGER, p.level());
        v.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, 0);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistenceRequired();
        v.getPersistentData().putBoolean(NPC_TAG, true);
        v.setCustomName(Component.literal("창고지기").withStyle(ChatFormatting.GOLD));
        v.setCustomNameVisible(true);
        p.level().addFreshEntity(v);
    }

    public static boolean isNpc(Entity e) {
        return e instanceof Villager && e.getPersistentData().getBoolean(NPC_TAG);
    }

    private static CompoundTag data(Player p) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return keep.getCompound("rpgStorage");
    }

    private static void saveData(Player p, CompoundTag d) {
        CompoundTag keep = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        keep.put("rpgStorage", d);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, keep);
    }

    public static int size(Player p) {
        int s = data(p).getInt("size");
        return s <= 0 ? BASE : Math.min(MAX, s);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void talk(PlayerInteractEvent.EntityInteract e) {
        if (!isNpc(e.getTarget())) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.isShiftKeyDown()) expand(sp);
        else open(sp);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void invulnerable(LivingAttackEvent e) {
        if (isNpc(e.getEntity())) e.setCanceled(true);
    }

    /** 확장: Shift+우클릭 한 번 = 안내, 5초 안에 한 번 더 = 결제 */
    private static void expand(ServerPlayer sp) {
        int size = size(sp);
        if (size >= MAX) { sp.sendSystemMessage(Component.literal("[창고] 이미 최대 " + MAX + "칸이에요").withStyle(ChatFormatting.GRAY)); return; }
        long cost = COST[Math.min(COST.length - 1, (size - BASE) / 9)];
        long now = sp.level().getGameTime();
        Long until = CONFIRM.get(sp.getUUID());
        if (until == null || now > until) {
            CONFIRM.put(sp.getUUID(), now + 100);
            sp.sendSystemMessage(Component.literal("[창고] " + size + "칸 → " + (size + 9) + "칸 확장: " + String.format("%,d", cost)
                    + " G. 5초 안에 다시 Shift+우클릭하면 확장해요").withStyle(ChatFormatting.YELLOW));
            return;
        }
        CONFIRM.remove(sp.getUUID());
        PlayerData d = RpgData.get(sp);
        if (d.gold < cost) { sp.sendSystemMessage(Component.literal("[창고] 골드가 부족해요 (" + String.format("%,d", cost) + " G 필요)").withStyle(ChatFormatting.RED)); return; }
        d.gold -= cost;
        CompoundTag dt = data(sp);
        dt.putInt("size", size + 9);
        saveData(sp, dt);
        RpgData.sync(sp);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
        sp.sendSystemMessage(Component.literal("[창고] " + (size + 9) + "칸으로 늘었어요 (-" + String.format("%,d", cost) + " G)").withStyle(ChatFormatting.GOLD));
    }

    /** 창고 열기: 저장된 아이템을 상자 창에 넣고, 닫을 때 다시 저장 */
    public static void open(ServerPlayer sp) {
        int size = size(sp);
        int rows = size / 9;
        SimpleContainer box = new SimpleContainer(size) {
            @Override
            public void stopOpen(Player p) {
                super.stopOpen(p);
                CompoundTag dt = data(p);
                ListTag list = new ListTag();
                for (int i = 0; i < getContainerSize(); i++) {
                    ItemStack s = getItem(i);
                    if (s.isEmpty()) continue;
                    CompoundTag t = s.save(new CompoundTag());
                    t.putByte("Slot", (byte) i);
                    list.add(t);
                }
                dt.put("items", list);
                dt.putInt("size", getContainerSize());
                saveData(p, dt);
            }
        };
        ListTag list = data(sp).getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < size) box.setItem(slot, ItemStack.of(t));
        }
        MenuType<ChestMenu> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1; case 2 -> MenuType.GENERIC_9x2; case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4; case 5 -> MenuType.GENERIC_9x5; default -> MenuType.GENERIC_9x6;
        };
        sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(type, id, inv, box, rows),
                Component.literal("창고 (" + size + "/" + MAX + "칸) · Shift+우클릭으로 확장")));
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.0f);
    }
}
