package rpg.core.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import rpg.core.balance.Balance;

/** 파티 관리: 초대·수락·탈퇴·추방, 근처 파티원 찾기 */
public final class Parties {
    private Parties() {}

    private static final Map<UUID, Party> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, UUID> INVITES = new HashMap<>();   // 초대받은 사람 → 초대한 파티장

    public static Party of(ServerPlayer p) {
        return BY_PLAYER.get(p.getUUID());
    }

    public static Party of(UUID id) {
        return BY_PLAYER.get(id);
    }

    public static UUID pendingInvite(ServerPlayer p) {
        return INVITES.get(p.getUUID());
    }

    public static String invite(ServerPlayer from, ServerPlayer to) {
        if (from == to) return "자기 자신은 초대할 수 없어요";
        Party pf = of(from);
        if (pf != null && !pf.leader.equals(from.getUUID())) return "파티장만 초대할 수 있어요";
        if (pf != null && pf.members.size() >= Balance.partyMax) return "파티가 가득 찼어요 (최대 " + Balance.partyMax + "명)";
        if (of(to) != null) return to.getGameProfile().getName() + "님은 이미 파티에 있어요";
        INVITES.put(to.getUUID(), from.getUUID());
        Component accept = Component.literal("[수락]").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("파티에 들어가기"))));
        Component decline = Component.literal("[거절]").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party decline")));
        to.sendSystemMessage(Component.literal("[파티] " + from.getGameProfile().getName() + "님이 파티에 초대했어요 ")
                .withStyle(ChatFormatting.AQUA).append(accept).append(" ").append(decline));
        return null;
    }

    public static String accept(ServerPlayer p) {
        UUID leaderId = INVITES.remove(p.getUUID());
        if (leaderId == null) return "받은 초대가 없어요";
        if (of(p) != null) return "이미 파티에 있어요";
        MinecraftServer server = p.getServer();
        ServerPlayer leader = server == null ? null : server.getPlayerList().getPlayer(leaderId);
        if (leader == null) return "초대한 사람이 접속해 있지 않아요";
        Party party = of(leader);
        if (party == null) {
            party = new Party(leaderId);
            BY_PLAYER.put(leaderId, party);
        }
        if (party.members.size() >= Balance.partyMax) return "파티가 가득 찼어요";
        party.members.add(p.getUUID());
        BY_PLAYER.put(p.getUUID(), party);
        broadcast(server, party, p.getGameProfile().getName() + "님이 파티에 들어왔어요");
        return null;
    }

    public static void decline(ServerPlayer p) {
        INVITES.remove(p.getUUID());
    }

    public static void leave(ServerPlayer p) {
        Party party = of(p);
        if (party == null) return;
        remove(p.getServer(), party, p.getUUID(), p.getGameProfile().getName() + "님이 파티를 나갔어요");
    }

    public static String kick(ServerPlayer leader, ServerPlayer target) {
        Party party = of(leader);
        if (party == null || !party.leader.equals(leader.getUUID())) return "파티장만 추방할 수 있어요";
        if (!party.members.contains(target.getUUID()) || target == leader) return "그 사람은 파티원이 아니에요";
        remove(leader.getServer(), party, target.getUUID(), target.getGameProfile().getName() + "님을 파티에서 내보냈어요");
        return null;
    }

    private static void remove(MinecraftServer server, Party party, UUID id, String msg) {
        broadcast(server, party, msg);
        party.members.remove(id);
        BY_PLAYER.remove(id);
        if (party.members.size() <= 1) {
            party.members.forEach(BY_PLAYER::remove);
            party.members.clear();
            return;
        }
        if (party.leader.equals(id)) party.leader = party.members.get(0);
    }

    /** 근처(같은 월드, partyRange 안)의 살아있는 파티원 (자기 포함). 파티 없으면 자기만 */
    public static List<ServerPlayer> nearby(ServerPlayer p) {
        List<ServerPlayer> out = new ArrayList<>();
        Party party = of(p);
        if (party == null || p.getServer() == null) { out.add(p); return out; }
        double r2 = Balance.partyRange * Balance.partyRange;
        for (UUID id : party.members) {
            ServerPlayer m = p.getServer().getPlayerList().getPlayer(id);
            if (m != null && m.isAlive() && m.level() == p.level() && m.distanceToSqr(p) <= r2) out.add(m);
        }
        if (!out.contains(p)) out.add(p);
        return out;
    }

    public static List<ServerPlayer> online(MinecraftServer server, Party party) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : party.members) {
            ServerPlayer m = server.getPlayerList().getPlayer(id);
            if (m != null) out.add(m);
        }
        return out;
    }

    public static void broadcast(MinecraftServer server, Party party, String msg) {
        if (server == null) return;
        for (ServerPlayer m : online(server, party)) m.sendSystemMessage(Component.literal("[파티] " + msg).withStyle(ChatFormatting.AQUA));
    }

    /** 접속 끊기면 파티에서 빠짐 */
    public static void logout(ServerPlayer p) {
        INVITES.remove(p.getUUID());
        leave(p);
    }
}
