package rpg.core.equip;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import rpg.core.RpgCore;
import rpg.core.net.Net;

/** 칭호: 훈장 칸에 칭호 있는 훈장을 끼고 착용 레벨이 되면 머리 위에 《 칭호 》 (모든 플레이어에게 보임) */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Titles {
    private Titles() {}

    private static final Map<UUID, Net.TitleSync> CUR = new HashMap<>();

    /** 장비 다시 계산할 때 부름 (EquipEvents) */
    public static void update(ServerPlayer p, ItemStack medal) {
        GearDefs.GearDef d = Gear.def(medal);
        String t = d != null && "medal".equals(d.slot()) && Gear.canEquip(p, medal) ? d.title() : "";
        int col = d == null ? 0 : d.titleColor();
        Net.TitleSync old = CUR.get(p.getUUID());
        String was = old == null ? "" : old.title();
        if (was.equals(t)) return;
        Net.TitleSync now = new Net.TitleSync(p.getUUID(), t, col);
        if (t.isEmpty()) CUR.remove(p.getUUID()); else CUR.put(p.getUUID(), now);
        Net.CH.send(PacketDistributor.ALL.noArg(), now);
        if (!t.isEmpty()) p.sendSystemMessage(Component.literal("칭호 《" + t + "》 을(를) 달았어요").withStyle(s -> s.withColor(col)));
        else if (!was.isEmpty()) p.sendSystemMessage(Component.literal("칭호를 뗐어요").withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        for (Net.TitleSync t : CUR.values()) Net.CH.send(PacketDistributor.PLAYER.with(() -> sp), t);
        Net.TitleSync mine = CUR.get(sp.getUUID());
        if (mine != null) sp.sendSystemMessage(Component.literal("칭호 《" + mine.title() + "》").withStyle(s -> s.withColor(mine.titleColor())));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (CUR.remove(e.getEntity().getUUID()) != null)
            Net.CH.send(PacketDistributor.ALL.noArg(), new Net.TitleSync(e.getEntity().getUUID(), "", 0));
    }
}
