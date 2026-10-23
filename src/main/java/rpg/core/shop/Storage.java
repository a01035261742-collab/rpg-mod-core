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
    /** 창고 1개 = 상자 1개 (27칸), 최대 5개 */
    public static final int PAGE = 27, MAX_PAGES = 5;
    /** 2·3·4·5번째 창고 비용 */
    public static final long[] COST = {10000, 50000, 100000, 300000};
    public static final net.minecraftforge.registries.RegistryObject<MenuType<StorageMenu>> TYPE = rpg.core.equip.EquipMenu.MENUS.register("storage",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create((id, inv, buf) -> new StorageMenu(id, inv, null)));

    public static void init() {}

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

    public static int pages(Player p) {
        CompoundTag d = data(p);
        int n = d.getInt("pages");
        if (n <= 0) n = 1;   // 예전 18칸 창고도 1번 창고로
        return Math.min(MAX_PAGES, n);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void talk(PlayerInteractEvent.EntityInteract e) {
        if (!isNpc(e.getTarget())) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp)) return;
        open(sp);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void invulnerable(LivingAttackEvent e) {
        if (isNpc(e.getEntity())) e.setCanceled(true);
    }

    public static void open(ServerPlayer sp) {
        sp.openMenu(new SimpleMenuProvider((id, inv, pl) -> new StorageMenu(id, inv, sp), Component.literal("창고")));
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.5f, 1.0f);
    }

    /** 모든 창고 아이템 (칸 번호 = 창고 번호 × 27 + 칸) */
    static ItemStack[] loadAll(Player p) {
        ItemStack[] all = new ItemStack[PAGE * MAX_PAGES];
        java.util.Arrays.fill(all, ItemStack.EMPTY);
        ListTag list = data(p).getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int slot = t.contains("S") ? t.getInt("S") : t.getByte("Slot") & 255;
            if (slot >= 0 && slot < all.length) all[slot] = ItemStack.of(t);
        }
        return all;
    }

    static void saveAll(Player p, ItemStack[] all, int pages) {
        CompoundTag d = data(p);
        ListTag list = new ListTag();
        for (int i = 0; i < all.length; i++) {
            if (all[i].isEmpty()) continue;
            CompoundTag t = all[i].save(new CompoundTag());
            t.putInt("S", i);
            list.add(t);
        }
        d.put("items", list);
        d.putInt("pages", pages);
        d.remove("size");
        saveData(p, d);
    }

    /** 창고 창: 27칸 (한 번에 창고 하나), 버튼 0~4 = 창고 번호, 9 = 확장 (두 번 눌러야) */
    public static class StorageMenu extends ChestMenu {
        final ServerPlayer owner;
        final SimpleContainer box;
        final ItemStack[] all;
        int page, pages;
        long confirmUntil;
        /** 0 지금 창고 · 1 창고 수 · 2 확장 확인 대기(1) */
        public final net.minecraft.world.inventory.ContainerData info;

        public StorageMenu(int id, net.minecraft.world.entity.player.Inventory inv, ServerPlayer owner) {
            this(id, inv, owner, new SimpleContainer(PAGE));
        }

        private StorageMenu(int id, net.minecraft.world.entity.player.Inventory inv, ServerPlayer owner, SimpleContainer box) {
            super(TYPE.get(), id, inv, box, 3);
            this.owner = owner;
            this.box = box;
            this.all = owner == null ? null : loadAll(owner);
            this.pages = owner == null ? 1 : pages(owner);
            if (owner != null) show(0);
            info = owner == null ? new net.minecraft.world.inventory.SimpleContainerData(3) : new net.minecraft.world.inventory.ContainerData() {
                @Override public int get(int i) { return i == 0 ? page : i == 1 ? pages : (owner.level().getGameTime() <= confirmUntil ? 1 : 0); }
                @Override public void set(int i, int v) {}
                @Override public int getCount() { return 3; }
            };
            addDataSlots(info);
        }

        private void keep() {   // 지금 창고 내용을 전체 배열에 반영
            for (int i = 0; i < PAGE; i++) all[page * PAGE + i] = box.getItem(i).copy();
        }

        private void show(int p) {
            page = p;
            for (int i = 0; i < PAGE; i++) box.setItem(i, all[p * PAGE + i].copy());
        }

        @Override
        public boolean clickMenuButton(Player p, int id) {
            if (owner == null || p != owner) return false;
            if (id >= 0 && id < pages) {
                keep();
                show(id);
                saveAll(owner, all, pages);
                return true;
            }
            if (id == 9) {
                if (pages >= MAX_PAGES) return true;
                long cost = COST[pages - 1];
                long now = owner.level().getGameTime();
                if (now > confirmUntil) {
                    confirmUntil = now + 100;
                    owner.displayClientMessage(Component.literal("[창고] " + (pages + 1) + "번째 창고: " + String.format("%,d", cost) + " G — 5초 안에 한 번 더 누르면 확장").withStyle(ChatFormatting.YELLOW), true);
                    return true;
                }
                confirmUntil = 0;
                PlayerData d = RpgData.get(owner);
                if (d.gold < cost) { owner.displayClientMessage(Component.literal("[창고] 골드가 부족해요 (" + String.format("%,d", cost) + " G)").withStyle(ChatFormatting.RED), true); return true; }
                d.gold -= cost;
                pages++;
                keep();
                saveAll(owner, all, pages);
                RpgData.sync(owner);
                owner.level().playSound(null, owner.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
                owner.displayClientMessage(Component.literal("[창고] " + pages + "번째 창고가 생겼어요").withStyle(ChatFormatting.GOLD), true);
                return true;
            }
            return false;
        }

        @Override
        public void removed(Player p) {
            super.removed(p);
            if (owner != null && p == owner) { keep(); saveAll(owner, all, pages); }
        }
    }
}
