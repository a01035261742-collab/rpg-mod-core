package rpg.core.party;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.net.Net;

/**
 * /party invite <플레이어> · accept · decline · leave · kick <플레이어> · list  (누구나 사용)
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class PartyCommand {
    private PartyCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("party")
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> run(c, 0, EntityArgument.getPlayer(c, "player").getGameProfile().getName()))))
                .then(Commands.literal("accept").executes(c -> run(c, 1, "")))
                .then(Commands.literal("decline").executes(c -> run(c, 2, "")))
                .then(Commands.literal("leave").executes(c -> run(c, 3, "")))
                .then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> run(c, 4, EntityArgument.getPlayer(c, "player").getGameProfile().getName()))))
                .then(Commands.literal("list").executes(PartyCommand::list)));
    }

    private static int run(CommandContext<CommandSourceStack> c, int action, String name) throws CommandSyntaxException {
        act(c.getSource().getPlayerOrException(), action, name);
        return 1;
    }

    /** 명령어·파티 창 공통 처리 */
    public static void act(ServerPlayer p, int action, String name) {
        String err = null;
        ServerPlayer target = name.isEmpty() || p.getServer() == null ? null : p.getServer().getPlayerList().getPlayerByName(name);
        switch (action) {
            case 0 -> {
                if (target == null) err = "접속 중인 플레이어가 아니에요: " + name;
                else {
                    err = Parties.invite(p, target);
                    if (err == null) msg(p, target.getGameProfile().getName() + "님을 초대했어요");
                }
            }
            case 1 -> err = Parties.accept(p);
            case 2 -> { Parties.decline(p); msg(p, "초대를 거절했어요"); }
            case 3 -> {
                if (Parties.of(p) == null) err = "파티에 없어요";
                else Parties.leave(p);
            }
            case 4 -> err = target == null ? "접속 중인 플레이어가 아니에요: " + name : Parties.kick(p, target);
            default -> {}
        }
        if (err != null) p.sendSystemMessage(Component.literal("[파티] " + err).withStyle(ChatFormatting.RED));
        Net.partyTo(p);
        if (target != null) Net.partyTo(target);
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Party party = Parties.of(p);
        if (party == null) { msg(p, "파티에 없어요 (P키 또는 /party invite <이름>)"); return 1; }
        StringBuilder sb = new StringBuilder("파티원: ");
        for (ServerPlayer m : Parties.online(p.getServer(), party)) {
            sb.append(party.leader.equals(m.getUUID()) ? "★" : "").append(m.getGameProfile().getName()).append(" ");
        }
        msg(p, sb.toString());
        return 1;
    }

    private static void msg(ServerPlayer p, String s) {
        p.sendSystemMessage(Component.literal("[파티] " + s).withStyle(ChatFormatting.AQUA));
    }
}
