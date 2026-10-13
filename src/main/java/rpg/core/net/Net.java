package rpg.core.net;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import rpg.core.RpgCore;
import rpg.core.data.PlayerData;
import rpg.core.data.Progress;

/** 서버↔클라이언트 패킷 */
public final class Net {
    private Net() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            RpgCore.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CH.messageBuilder(SyncData.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncData::encode).decoder(SyncData::new).consumerMainThread(SyncData::handle).add();
        CH.messageBuilder(AllocStat.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AllocStat::encode).decoder(AllocStat::new).consumerMainThread(AllocStat::handle).add();
    }

    public static void syncTo(ServerPlayer p, PlayerData d) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new SyncData(d));
    }

    // ───────── 서버 → 클라: 내 데이터 ─────────
    public static class SyncData {
        public final FriendlyByteBuf buf;

        SyncData(PlayerData d) {
            buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            d.write(buf);
        }

        SyncData(FriendlyByteBuf in) {
            buf = new FriendlyByteBuf(in.copy());
            in.skipBytes(in.readableBytes());
        }

        void encode(FriendlyByteBuf out) {
            out.writeBytes(buf, buf.readerIndex(), buf.readableBytes());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.ClientData.read(buf));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 스탯 분배 ─────────
    public record AllocStat(int stat, int amount) {
        AllocStat(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt()); }

        void encode(FriendlyByteBuf b) { b.writeVarInt(stat); b.writeVarInt(amount); }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) Progress.allocStat(p, stat, amount);
            ctx.get().setPacketHandled(true);
        }
    }
}
