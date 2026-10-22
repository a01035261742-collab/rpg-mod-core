package rpg.core.equip;

import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 장비 칸 붙이기·저장, 스탯 반영, 공격 속도, 장비 창 열기 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class EquipEvents {
    private EquipEvents() {}

    private static final java.util.Map<UUID, ItemStack> LAST_HAND = new java.util.HashMap<>();
    private static final UUID SPEED_ID = UUID.fromString("6f1f0d2e-7a51-4c1e-9b0a-52d0c1a1e002");

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player && !e.getObject().getCapability(PlayerEquip.Provider.CAP).isPresent()) {
            e.addCapability(RpgCore.id("equip"), new PlayerEquip.Provider());
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        PlayerEquip old = PlayerEquip.get(e.getOriginal()), now = PlayerEquip.get(e.getEntity());
        for (int i = 0; i < old.items.getSlots(); i++) now.items.setStackInSlot(i, old.items.getStackInSlot(i).copy());
        e.getOriginal().invalidateCaps();
    }

    public static void open(ServerPlayer p) {
        NetworkHooks.openScreen(p, new SimpleMenuProvider((id, inv, pl) -> new EquipMenu(id, inv), Component.literal("장비")));
    }

    /** 갑옷 칸이 바뀌어도 다시 계산 */
    @SubscribeEvent
    public static void armorChange(LivingEquipmentChangeEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) PlayerEquip.get(sp).dirty = true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer sp)) return;
        PlayerEquip eq = PlayerEquip.get(sp);
        // 예전 무기 칸에 남은 무기는 인벤으로
        ItemStack old = eq.items.getStackInSlot(0);
        if (!old.isEmpty()) {
            eq.items.setStackInSlot(0, ItemStack.EMPTY);
            if (!sp.getInventory().add(old)) sp.drop(old, false);
        }
        // 손에 든 무기가 바뀌면 다시 계산
        ItemStack hand = PlayerEquip.weapon(sp);
        ItemStack last = LAST_HAND.getOrDefault(sp.getUUID(), ItemStack.EMPTY);
        if (!ItemStack.isSameItemSameTags(hand, last)) {
            LAST_HAND.put(sp.getUUID(), hand.copy());
            eq.dirty = true;
        }
        if (!eq.dirty && sp.tickCount % 40 != 0) return;
        eq.dirty = false;
        PlayerData d = RpgData.get(sp);
        Map<String, Double> sum = eq.sum(sp);
        Titles.update(sp, eq.items.getStackInSlot(10));   // 훈장 칸 칭호
        boolean changed = !sum.equals(d.equip);
        d.equip.clear();
        d.equip.putAll(sum);
        if (changed) {
            d.recalc();
            RpgData.sync(sp);
        }
        // 공격 속도: 손에 든 무기 기준 (맨손 4.0)
        GearDefs.GearDef w = Gear.canEquip(sp, hand) ? Gear.def(hand) : null;
        double speed = w != null && w.speed() > 0 ? w.speed() : 4.0;
        AttributeInstance inst = sp.getAttribute(Attributes.ATTACK_SPEED);
        if (inst != null) {
            AttributeModifier cur = inst.getModifier(SPEED_ID);
            double want = speed - 4.0;
            if (cur == null || Math.abs(cur.getAmount() - want) > 1e-6) {
                if (cur != null) inst.removeModifier(SPEED_ID);
                inst.addTransientModifier(new AttributeModifier(SPEED_ID, "rpg weapon speed", want, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    /** RPG 서버는 죽어도 아이템 유지 (equipment.json keepInventory) */
    @SubscribeEvent
    public static void serverStarted(ServerStartedEvent e) {
        if (GearDefs.keepInventory) e.getServer().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, e.getServer());
    }
}
