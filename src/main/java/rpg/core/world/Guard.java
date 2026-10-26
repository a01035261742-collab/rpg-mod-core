package rpg.core.world;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.equip.Gear;
import rpg.core.equip.PlayerEquip;

/**
 * 악용 막기 모음.
 * - 접속 끊길 때: 저장 전에 열린 창(창고·공유 창고·거래)을 먼저 닫음 → 복사·증발 막기
 * - 쓰러진 상태 / 몹한테 맞은 지 10초 안에 접속 끊으면 사망 처리 (경험치 손실 + RPG 스폰)
 * - RPG 장비는 모루에 못 올림, 붙어 있는 마법은 지움
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Guard {
    private Guard() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        // 1) 창 먼저 닫기 (바닐라는 저장한 뒤에 닫아서, 창고에서 꺼낸 물건이 양쪽에 남음)
        if (p.containerMenu != p.inventoryMenu) p.doCloseContainer();
        // 2) 도망 접속 종료 = 사망
        if (!RpgWorld.in(p) || p.getServer() == null) return;
        PlayerData d = RpgData.get(p);
        long now = p.level().getGameTime();
        boolean combat = d.lastHitAt > 0 && now - d.lastHitAt < 200;
        if (!d.isDown() && !combat) return;
        long loss = Math.round(Balance.expNeed(d.level) * Balance.deathExpLossPct / 100.0);
        long lost = Math.min(d.exp, loss);
        d.exp -= lost;
        d.downEnd = 0;
        d.reviver = null;
        d.hp = d.maxHp;
        d.mp = d.maxMp;
        ServerLevel rl = RpgWorld.rpgLevel(p.getServer());
        if (rl != null) {
            BlockPos sp = RpgWorld.data(p.getServer()).rpgSpawn;
            p.teleportTo(rl, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYRot(), p.getXRot());
        }
        CompoundTag k = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        k.putLong("rpgLogoutDeath", lost);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag k = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!k.contains("rpgLogoutDeath")) return;
        long lost = k.getLong("rpgLogoutDeath");
        k.remove("rpgLogoutDeath");
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
        p.sendSystemMessage(Component.literal("[RPG] 전투 중(또는 쓰러진 상태)에 접속이 끊겨서 사망 처리됐어요. 경험치 " + String.format("%,d", lost) + " 잃음")
                .withStyle(ChatFormatting.RED));
    }

    /** RPG 장비는 모루 금지 (마법 부여·이름 바꾸기·합치기) */
    @SubscribeEvent
    public static void anvil(AnvilUpdateEvent e) {
        if (isGear(e.getLeft()) || isGear(e.getRight())) e.setCanceled(true);
    }

    private static boolean isGear(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof Gear.GearItem;
    }

    /** 2초마다: 들고 있는 RPG 장비에 붙은 마법 지우기 (예전에 붙인 것 포함) */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
        boolean any = false;
        for (ItemStack s : p.getInventory().items) any |= strip(s);
        for (ItemStack s : p.getInventory().armor) any |= strip(s);
        for (ItemStack s : p.getInventory().offhand) any |= strip(s);
        var eq = PlayerEquip.get(p).items;
        for (int i = 0; i < eq.getSlots(); i++) any |= strip(eq.getStackInSlot(i));
        if (any) p.displayClientMessage(Component.literal("RPG 장비에는 마법을 붙일 수 없어서 지웠어요").withStyle(ChatFormatting.GRAY), true);
    }

    private static boolean strip(ItemStack s) {
        if (!isGear(s) || !s.hasTag() || !s.getTag().contains("Enchantments")) return false;
        s.removeTagKey("Enchantments");
        return true;
    }
}
