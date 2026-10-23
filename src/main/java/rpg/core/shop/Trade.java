package rpg.core.shop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegistryObject;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 거래: 상대를 Shift+우클릭 → 요청 (15초), 상대도 나를 Shift+우클릭하면 거래창.
 * 양쪽 9칸 + 골드. 둘 다 [확인] → 둘 다 [거래] 하면 완료. 물건·골드를 바꾸면 확인이 풀림. 창을 닫으면 취소 (물건은 주인에게).
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Trade {
    private Trade() {}

    public static final RegistryObject<MenuType<TradeMenu>> TYPE = rpg.core.equip.EquipMenu.MENUS.register("trade",
            () -> IForgeMenuType.create((id, inv, buf) -> new TradeMenu(id, inv, null, true)));

    public static void init() {}

    public static final double RANGE = 8;
    private record Req(UUID to, long until) {}
    private static final Map<UUID, Req> REQ = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** 거래 한 건: 양쪽 물건 칸·골드·확인 상태 */
    public static final class Session {
        final ServerPlayer a, b;
        final SimpleContainer ca = new SimpleContainer(9), cb = new SimpleContainer(9);
        long goldA, goldB;
        boolean okA, okB, doneA, doneB, finished;

        Session(ServerPlayer a, ServerPlayer b) {
            this.a = a; this.b = b;
            ca.addListener(c -> changed());
            cb.addListener(c -> changed());
        }

        void changed() { okA = okB = doneA = doneB = false; }
        boolean isA(Player p) { return p.getUUID().equals(a.getUUID()); }
        ServerPlayer other(Player p) { return isA(p) ? b : a; }
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer me) || !(e.getTarget() instanceof ServerPlayer other) || !me.isShiftKeyDown()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        if (SESSIONS.containsKey(me.getUUID()) || SESSIONS.containsKey(other.getUUID())) { msg(me, "이미 거래 중이에요", ChatFormatting.GRAY); return; }
        long now = me.level().getGameTime();
        Req r = REQ.get(other.getUUID());
        if (r != null && r.to().equals(me.getUUID()) && now <= r.until()) {   // 상대가 먼저 요청함 → 수락
            REQ.remove(other.getUUID());
            open(other, me);
            return;
        }
        REQ.put(me.getUUID(), new Req(other.getUUID(), now + 300));
        msg(me, other.getGameProfile().getName() + "님에게 거래를 요청했어요 (15초)", ChatFormatting.YELLOW);
        other.sendSystemMessage(Component.literal("[거래] " + me.getGameProfile().getName() + "님이 거래를 요청했어요. 15초 안에 그 사람을 Shift+우클릭하면 수락")
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void open(ServerPlayer a, ServerPlayer b) {
        Session s = new Session(a, b);
        SESSIONS.put(a.getUUID(), s);
        SESSIONS.put(b.getUUID(), s);
        a.openMenu(new SimpleMenuProvider((id, inv, pl) -> new TradeMenu(id, inv, s, true), Component.literal("거래: " + b.getGameProfile().getName())));
        b.openMenu(new SimpleMenuProvider((id, inv, pl) -> new TradeMenu(id, inv, s, false), Component.literal("거래: " + a.getGameProfile().getName())));
        a.level().playSound(null, a.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }

    private static void giveBack(ServerPlayer p, SimpleContainer c) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.removeItemNoUpdate(i);
            if (s.isEmpty()) continue;
            if (!p.getInventory().add(s)) p.drop(s, false);
        }
    }

    /** 취소: 물건은 주인에게, 상대 창도 닫음 */
    static void cancel(Session s, String why) { cancel(s, why, null); }

    static void cancel(Session s, String why, Player closing) {
        if (s.finished) return;
        s.finished = true;
        SESSIONS.remove(s.a.getUUID());
        SESSIONS.remove(s.b.getUUID());
        giveBack(s.a, s.ca);
        giveBack(s.b, s.cb);
        for (ServerPlayer p : new ServerPlayer[]{s.a, s.b}) {
            if (p != closing && p.containerMenu instanceof TradeMenu) p.closeContainer();
            p.sendSystemMessage(Component.literal("[거래] " + why).withStyle(ChatFormatting.GRAY));
        }
    }

    /** 둘 다 [거래] → 교환 */
    static void complete(Session s) {
        PlayerData da = RpgData.get(s.a), db = RpgData.get(s.b);
        if (da.gold < s.goldA || db.gold < s.goldB) { cancel(s, "골드가 모자라서 거래가 취소됐어요"); return; }
        s.finished = true;
        SESSIONS.remove(s.a.getUUID());
        SESSIONS.remove(s.b.getUUID());
        da.gold += s.goldB - s.goldA;
        db.gold += s.goldA - s.goldB;
        giveBack(s.b, s.ca);   // a 의 물건 → b
        giveBack(s.a, s.cb);
        RpgData.sync(s.a);
        RpgData.sync(s.b);
        for (ServerPlayer p : new ServerPlayer[]{s.a, s.b}) {
            if (p.containerMenu instanceof TradeMenu) p.closeContainer();
            p.sendSystemMessage(Component.literal("[거래] 거래 완료!").withStyle(ChatFormatting.GREEN));
            p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.6f);
        }
    }

    // ───────── 거래창 메뉴 (내 9칸 · 상대 9칸 · 내 인벤) ─────────
    public static class TradeMenu extends AbstractContainerMenu {
        /** 버튼: 0 확인 · 1 거래 · 2 +1천 · 3 +1만 · 4 +10만 · 5 골드 0 */
        public static final long[] ADD = {1000, 10000, 100000};
        final Session s;
        final boolean mineIsA;
        /** 0~1 내 골드 · 2~3 상대 골드 (15비트씩) · 4 내 확인 · 5 상대 확인 · 6 내 거래 · 7 상대 거래 */
        public final ContainerData data;

        public TradeMenu(int id, Inventory inv, Session s, boolean mineIsA) {
            super(TYPE.get(), id);
            this.s = s;
            this.mineIsA = mineIsA;
            SimpleContainer mine = s == null ? new SimpleContainer(9) : mineIsA ? s.ca : s.cb;
            SimpleContainer theirs = s == null ? new SimpleContainer(9) : mineIsA ? s.cb : s.ca;
            for (int i = 0; i < 9; i++) addSlot(new Slot(mine, i, 8 + (i % 3) * 18, 28 + (i / 3) * 18));
            for (int i = 0; i < 9; i++) addSlot(new Slot(theirs, i, 116 + (i % 3) * 18, 28 + (i / 3) * 18) {
                @Override public boolean mayPlace(ItemStack st) { return false; }
                @Override public boolean mayPickup(Player p) { return false; }
            });
            for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, 8 + c * 18, 100 + r * 18));
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 158));
            data = s == null ? new net.minecraft.world.inventory.SimpleContainerData(8) : new ContainerData() {
                @Override public int get(int i) {
                    long mg = mineIsA ? s.goldA : s.goldB, tg = mineIsA ? s.goldB : s.goldA;
                    return switch (i) {
                        case 0 -> (int) (mg & 0x7FFF); case 1 -> (int) (mg >> 15 & 0x7FFF);
                        case 2 -> (int) (tg & 0x7FFF); case 3 -> (int) (tg >> 15 & 0x7FFF);
                        case 4 -> (mineIsA ? s.okA : s.okB) ? 1 : 0; case 5 -> (mineIsA ? s.okB : s.okA) ? 1 : 0;
                        case 6 -> (mineIsA ? s.doneA : s.doneB) ? 1 : 0; default -> (mineIsA ? s.doneB : s.doneA) ? 1 : 0;
                    };
                }
                @Override public void set(int i, int v) {}
                @Override public int getCount() { return 8; }
            };
            addDataSlots(data);
        }

        public long myGold() { return data.get(0) | (long) data.get(1) << 15; }
        public long theirGold() { return data.get(2) | (long) data.get(3) << 15; }

        @Override
        public boolean clickMenuButton(Player p, int id) {
            if (s == null || s.finished || !(p instanceof ServerPlayer sp)) return false;
            if (s.a.distanceTo(s.b) > RANGE) { cancel(s, "너무 멀어서 거래가 취소됐어요"); return true; }
            boolean a = s.isA(p);
            switch (id) {
                case 0 -> { if (a) s.okA = !s.okA; else s.okB = !s.okB; if (!s.okA || !s.okB) { s.doneA = s.doneB = false; } }
                case 1 -> {
                    if (!s.okA || !s.okB) { msg(sp, "둘 다 [확인]을 눌러야 해요", ChatFormatting.GRAY); return true; }
                    if (a) s.doneA = true; else s.doneB = true;
                    if (s.doneA && s.doneB) complete(s);
                }
                case 2, 3, 4 -> {
                    long have = RpgData.get(sp).gold;
                    long cur = a ? s.goldA : s.goldB;
                    long next = Math.min(Math.min(have, (1L << 30) - 1), cur + ADD[id - 2]);
                    if (a) s.goldA = next; else s.goldB = next;
                    s.changed();
                }
                case 5 -> { if (a) s.goldA = 0; else s.goldB = 0; s.changed(); }
                default -> { return false; }
            }
            return true;
        }

        @Override
        public ItemStack quickMoveStack(Player p, int index) {
            Slot slot = slots.get(index);
            if (!slot.hasItem() || (index >= 9 && index < 18)) return ItemStack.EMPTY;
            ItemStack st = slot.getItem(), copy = st.copy();
            if (index < 9) { if (!moveItemStackTo(st, 18, 54, true)) return ItemStack.EMPTY; }
            else if (!moveItemStackTo(st, 0, 9, false)) return ItemStack.EMPTY;
            if (st.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            return copy;
        }

        @Override
        public boolean stillValid(Player p) {
            return s == null || (!s.finished && s.a.isAlive() && s.b.isAlive() && s.a.distanceTo(s.b) <= RANGE + 4);
        }

        @Override
        public void removed(Player p) {
            super.removed(p);
            if (s != null && !s.finished && !p.level().isClientSide) cancel(s, p.getGameProfile().getName() + "님이 거래창을 닫아서 취소됐어요", p);
        }
    }
}
