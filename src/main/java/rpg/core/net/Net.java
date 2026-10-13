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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import rpg.core.combat.Combat;
import rpg.core.combat.MobData;
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
        CH.messageBuilder(MobInfo.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MobInfo::encode).decoder(MobInfo::new).consumerMainThread(MobInfo::handle).add();
        CH.messageBuilder(DamageText.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DamageText::encode).decoder(DamageText::new).consumerMainThread(DamageText::handle).add();
        CH.messageBuilder(Reward.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Reward::encode).decoder(Reward::new).consumerMainThread(Reward::handle).add();
        CH.messageBuilder(UseSkill.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UseSkill::encode).decoder(UseSkill::new).consumerMainThread(UseSkill::handle).add();
        CH.messageBuilder(Dodge.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Dodge::encode).decoder(Dodge::new).consumerMainThread(Dodge::handle).add();
        CH.messageBuilder(Learn.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Learn::encode).decoder(Learn::new).consumerMainThread(Learn::handle).add();
        CH.messageBuilder(DefsSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DefsSync::encode).decoder(DefsSync::new).consumerMainThread(DefsSync::handle).add();
    }

    public static void defsTo(ServerPlayer p) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new DefsSync(rpg.core.skill.Defs.skillsJson, rpg.core.skill.Defs.passivesJson));
    }

    // ───────── 클라 → 서버: 스킬 키 (0~5 = Q E R F Z X) ─────────
    public record UseSkill(int slot) {
        UseSkill(FriendlyByteBuf b) { this(b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(slot); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) rpg.core.skill.Skills.use(p, slot);
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 구르기 (이동 방향) ─────────
    public record Dodge(float dx, float dz) {
        Dodge(FriendlyByteBuf b) { this(b.readFloat(), b.readFloat()); }
        void encode(FriendlyByteBuf b) { b.writeFloat(dx); b.writeFloat(dz); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) rpg.core.skill.Skills.dodge(p, dx, dz);
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 스킬 레벨업(kind 0) / 패시브 찍기(kind 1) ─────────
    public record Learn(int kind, String id) {
        Learn(FriendlyByteBuf b) { this(b.readVarInt(), b.readUtf(64)); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(kind); b.writeUtf(id, 64); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                if (kind == 0) rpg.core.skill.Skills.learn(p, id);
                else rpg.core.skill.Skills.passiveUp(p, id);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 스킬·패시브 정의 원문 ─────────
    public record DefsSync(String skills, String passives) {
        DefsSync(FriendlyByteBuf b) { this(b.readUtf(262144), b.readUtf(262144)); }
        void encode(FriendlyByteBuf b) { b.writeUtf(skills, 262144); b.writeUtf(passives, 262144); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.skill.Defs.parse(skills, passives));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void syncTo(ServerPlayer p, PlayerData d) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new SyncData(p, d));
    }

    public static void mobInfo(Mob mob, MobData m) {
        CH.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new MobInfo(mob.getId(), m.level, m.hp, m.maxHp, m.name));
    }

    public static void mobInfoTo(ServerPlayer p, Mob mob, MobData m) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new MobInfo(mob.getId(), m.level, m.hp, m.maxHp, m.name));
    }

    public static void damageText(Mob mob, double amount, boolean crit) {
        CH.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new DamageText(mob.getId(), amount, crit));
    }

    public static void reward(ServerPlayer p, long exp, long gold) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new Reward(exp, gold));
    }

    // ───────── 서버 → 클라: 몹 레벨·체력 ─────────
    public record MobInfo(int id, int level, double hp, double maxHp, String name) {
        MobInfo(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble(), b.readUtf(64)); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(id); b.writeVarInt(level); b.writeDouble(hp); b.writeDouble(maxHp); b.writeUtf(name, 64); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.ClientMobs.update(id, level, hp, maxHp, name));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 데미지 숫자 ─────────
    public record DamageText(int id, double amount, boolean crit) {
        DamageText(FriendlyByteBuf b) { this(b.readVarInt(), b.readDouble(), b.readBoolean()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(id); b.writeDouble(amount); b.writeBoolean(crit); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.WorldText.addDamage(id, amount, crit));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 사냥 보상 알림 ─────────
    public record Reward(long exp, long gold) {
        Reward(FriendlyByteBuf b) { this(b.readVarLong(), b.readVarLong()); }
        void encode(FriendlyByteBuf b) { b.writeVarLong(exp); b.writeVarLong(gold); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.RpgHud.addReward(exp, gold));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 내 데이터 ─────────
    public static class SyncData {
        public final FriendlyByteBuf buf;

        SyncData(Player p, PlayerData d) {
            buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            d.write(buf, Combat.statAttack(p, d), Combat.defReducePct(d), p.level().getGameTime());
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
