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

    /** 조준점 기억 (멀리 떨어진 엉뚱한 값은 무시) */
    static void aim(ServerPlayer p, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        var v = new net.minecraft.world.phys.Vec3(x, y, z);
        if (v.distanceToSqr(p.getEyePosition()) > 80 * 80) return;
        var d = rpg.core.data.RpgData.get(p);
        d.aim = v;
        d.aimTick = p.level().getGameTime();
    }

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
        CH.messageBuilder(PartySync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PartySync::encode).decoder(PartySync::new).consumerMainThread(PartySync::handle).add();
        CH.messageBuilder(PartyAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PartyAction::encode).decoder(PartyAction::new).consumerMainThread(PartyAction::handle).add();
        CH.messageBuilder(OpenEquip.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenEquip::encode).decoder(OpenEquip::new).consumerMainThread(OpenEquip::handle).add();
        CH.messageBuilder(OpenJob.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenJob::encode).decoder(OpenJob::new).consumerMainThread(OpenJob::handle).add();
        CH.messageBuilder(JobAdvance.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(JobAdvance::encode).decoder(JobAdvance::new).consumerMainThread(JobAdvance::handle).add();
        CH.messageBuilder(Basic.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Basic::encode).decoder(Basic::new).consumerMainThread(Basic::handle).add();
        CH.messageBuilder(Telegraph.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Telegraph::encode).decoder(Telegraph::new).consumerMainThread(Telegraph::handle).add();
        CH.messageBuilder(SkillRelease.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SkillRelease::encode).decoder(SkillRelease::new).consumerMainThread(SkillRelease::handle).add();
        CH.messageBuilder(SetSlot.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetSlot::encode).decoder(SetSlot::new).consumerMainThread(SetSlot::handle).add();
        CH.messageBuilder(LevelUp.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LevelUp::encode).decoder(LevelUp::new).consumerMainThread(LevelUp::handle).add();
        CH.messageBuilder(ItemNote.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ItemNote::encode).decoder(ItemNote::new).consumerMainThread(ItemNote::handle).add();
        CH.messageBuilder(OpenShop.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenShop::encode).decoder(OpenShop::new).consumerMainThread(OpenShop::handle).add();
        CH.messageBuilder(ShopAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ShopAction::encode).decoder(ShopAction::new).consumerMainThread(ShopAction::handle).add();
        CH.messageBuilder(QuestDefs.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestDefs::encode).decoder(QuestDefs::new).consumerMainThread(QuestDefs::handle).add();
        CH.messageBuilder(QuestSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestSync::encode).decoder(QuestSync::new).consumerMainThread(QuestSync::handle).add();
        CH.messageBuilder(QuestTalk.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestTalk::encode).decoder(QuestTalk::new).consumerMainThread(QuestTalk::handle).add();
        CH.messageBuilder(QuestAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(QuestAction::encode).decoder(QuestAction::new).consumerMainThread(QuestAction::handle).add();
        CH.messageBuilder(QuestPoints.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestPoints::encode).decoder(QuestPoints::new).consumerMainThread(QuestPoints::handle).add();
        CH.messageBuilder(OpenCraft.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenCraft::encode).decoder(OpenCraft::new).consumerMainThread(OpenCraft::handle).add();
        CH.messageBuilder(Rolled.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Rolled::encode).decoder(Rolled::new).consumerMainThread(Rolled::handle).add();
        CH.messageBuilder(CraftAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CraftAction::encode).decoder(CraftAction::new).consumerMainThread(CraftAction::handle).add();
        CH.messageBuilder(OpenEnhance.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenEnhance::encode).decoder(OpenEnhance::new).consumerMainThread(OpenEnhance::handle).add();
        CH.messageBuilder(EnhanceAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EnhanceAction::encode).decoder(EnhanceAction::new).consumerMainThread(EnhanceAction::handle).add();
        CH.messageBuilder(Track.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Track::encode).decoder(Track::new).consumerMainThread(Track::handle).add();
        CH.messageBuilder(TitleSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TitleSync::encode).decoder(TitleSync::new).consumerMainThread(TitleSync::handle).add();
        CH.messageBuilder(DailySync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DailySync::encode).decoder(DailySync::new).consumerMainThread(DailySync::handle).add();
        CH.messageBuilder(DailyAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DailyAction::encode).decoder(DailyAction::new).consumerMainThread(DailyAction::handle).add();
        CH.messageBuilder(AimLine.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AimLine::encode).decoder(AimLine::new).consumerMainThread(AimLine::handle).add();
        CH.messageBuilder(WorldChoice.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorldChoice::encode).decoder(WorldChoice::new).consumerMainThread(WorldChoice::handle).add();
        CH.messageBuilder(ChooseWorld.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChooseWorld::encode).decoder(ChooseWorld::new).consumerMainThread(ChooseWorld::handle).add();
        CH.messageBuilder(PowerReq.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PowerReq::encode).decoder(PowerReq::new).consumerMainThread(PowerReq::handle).add();
        CH.messageBuilder(PowerRes.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PowerRes::encode).decoder(PowerRes::new).consumerMainThread(PowerRes::handle).add();
    }

    // ───────── 첫 접속: 야생 / RPG 고르기 ─────────
    public record WorldChoice() {
        WorldChoice(FriendlyByteBuf b) { this(); }
        void encode(FriendlyByteBuf b) {}
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.minecraft.client.Minecraft.getInstance().setScreen(new rpg.core.client.WorldChoiceScreen()));
            ctx.get().setPacketHandled(true);
        }
    }

    public record ChooseWorld(boolean toRpg) {
        ChooseWorld(FriendlyByteBuf b) { this(b.readBoolean()); }
        void encode(FriendlyByteBuf b) { b.writeBoolean(toRpg); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().setPacketHandled(true);
            if (p != null) rpg.core.world.RpgWorld.choose(p, toRpg);
        }
    }

    public static void worldChoice(ServerPlayer p) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new WorldChoice());
    }

    // ───────── 툴팁 전투력 비교 (마우스 올릴 때만) ─────────
    private static final java.util.Map<java.util.UUID, long[]> POWER_RATE = new java.util.HashMap<>();

    public record PowerReq(int key, net.minecraft.world.item.ItemStack stack) {
        PowerReq(FriendlyByteBuf b) { this(b.readInt(), b.readItem()); }
        void encode(FriendlyByteBuf b) { b.writeInt(key); b.writeItem(stack); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            ctx.get().setPacketHandled(true);
            if (p == null || rpg.core.equip.Gear.def(stack) == null) return;
            long[] r = POWER_RATE.computeIfAbsent(p.getUUID(), k -> new long[2]);   // 1초에 최대 20번
            long sec = p.level().getGameTime() / 20;
            if (r[0] != sec) { r[0] = sec; r[1] = 0; }
            if (++r[1] > 20) return;
            var c = rpg.core.equip.Power.compare(p, stack);
            CH.send(PacketDistributor.PLAYER.with(() -> p), new PowerRes(key, c.delta(), c.state(), c.why()));
        }
    }

    public record PowerRes(int key, long delta, int state, String why) {
        PowerRes(FriendlyByteBuf b) { this(b.readInt(), b.readLong(), b.readByte(), b.readUtf()); }
        void encode(FriendlyByteBuf b) { b.writeInt(key); b.writeLong(delta); b.writeByte(state); b.writeUtf(why); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.RpgTooltip.result(key, delta, state, why));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 스나이핑 조준선 ─────────
    public record AimLine(int id, int ticks) {
        AimLine(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(id); b.writeVarInt(ticks); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.AimLaser.start(id, ticks));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void aimLine(ServerPlayer p, int ticks) {
        CH.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new AimLine(p.getId(), ticks));
    }

    // ───────── 일일 퀘스트 ─────────
    public record DailyRow(String label, int prog, int need, boolean done) {}

    public record DailySync(java.util.List<DailyRow> rows, int rerolls, boolean bonus, int minutes) {
        DailySync(FriendlyByteBuf b) {
            this(b.readList(x -> new DailyRow(x.readUtf(), x.readVarInt(), x.readVarInt(), x.readBoolean())), b.readVarInt(), b.readBoolean(), b.readVarInt());
        }
        void encode(FriendlyByteBuf b) {
            b.writeCollection(rows, (x, r) -> { x.writeUtf(r.label()); x.writeVarInt(r.prog()); x.writeVarInt(r.need()); x.writeBoolean(r.done()); });
            b.writeVarInt(rerolls); b.writeBoolean(bonus); b.writeVarInt(minutes);
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.DailyScreen.receive(this));
            ctx.get().setPacketHandled(true);
        }
    }

    /** op 0 열기 · 1 완료(idx) · 2 다시 뽑기(idx) */
    public record DailyAction(int op, int idx) {
        DailyAction(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(op); b.writeVarInt(idx); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) rpg.core.quest.Dailies.action(p, op, idx);
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 플레이어 칭호 (빈 문자열 = 없음) ─────────
    public record TitleSync(java.util.UUID player, String title, int titleColor) {
        TitleSync(FriendlyByteBuf b) { this(b.readUUID(), b.readUtf(), b.readInt()); }
        void encode(FriendlyByteBuf b) { b.writeUUID(player); b.writeUtf(title); b.writeInt(titleColor); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.WorldText.title(this));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 추적 예고 (노랑, 클라가 매 프레임 대상 위치를 따라 그림) ─────────
    /** owner = 보스 엔티티, follow = 따라갈 플레이어 (-1 이면 owner 의 추적 예고 전부 지우기). 원: follow 발밑 / 직선: owner → follow 방향 */
    public static void track(net.minecraft.server.level.ServerLevel level, int owner, int follow, int shape, double a, double b, int ticks) {
        var e = level.getEntity(owner);
        if (e == null) return;
        Track t = new Track(owner, follow, shape, (float) a, (float) b, ticks);
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(e) < 64 * 64) CH.send(PacketDistributor.PLAYER.with(() -> p), t);
    }

    public record Track(int owner, int follow, int shape, float a, float b, int ticks) {
        Track(FriendlyByteBuf f) { this(f.readVarInt(), f.readInt(), f.readByte(), f.readFloat(), f.readFloat(), f.readVarInt()); }
        void encode(FriendlyByteBuf f) {
            f.writeVarInt(owner); f.writeInt(follow); f.writeByte(shape); f.writeFloat(a); f.writeFloat(b); f.writeVarInt(ticks);
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.Telegraphs.track(this));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 장비 강화 ─────────
    /** 강화 창 내용 보내기 (open = 새로 열기, 아니면 열린 창만 갱신) */
    public static void sendEnhance(ServerPlayer p, boolean open) {
        java.util.List<Integer> locs = rpg.core.equip.Enhance.gearLocs(p);
        java.util.List<net.minecraft.world.item.ItemStack> gear = new java.util.ArrayList<>();
        for (int l : locs) {
            var orig = rpg.core.equip.Enhance.at(p, l);
            var cp = orig.copy();
            // 전투력 변화 (클라 표시용 임시 태그)
            if (rpg.core.equip.Potential.pending(orig))
                cp.getOrCreateTag().putLong("rpgPowPot", rpg.core.equip.Power.delta(p, orig, "rpgPot", orig.getTag().getCompound("rpgPot").getCompound("new").copy()));
            if (rpg.core.equip.Flame.pending(orig))
                cp.getOrCreateTag().putLong("rpgPowFlame", rpg.core.equip.Power.delta(p, orig, "rpgOpts", rpg.core.equip.Flame.pendingTag(orig).getCompound("o").copy()));
            gear.add(cp);
        }
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        java.util.List<net.minecraft.world.item.ItemStack> scrolls = new java.util.ArrayList<>();
        for (int i = 0; i < 36; i++) {
            var s = p.getInventory().items.get(i);
            if (!rpg.core.item.Scroll.kind(s).isEmpty()) { slots.add(i); scrolls.add(s.copy()); }
        }
        int frags = rpg.core.equip.Enhance.count(p, rpg.core.item.ModItems.SCROLL_FRAGMENT.get());
        int[] ce = rpg.core.equip.Potential.ceilCounts(p);
        java.util.List<Integer> extra = java.util.List.of(rpg.core.equip.Enhance.count(p, rpg.core.item.ModItems.CUBE.get()),
                rpg.core.equip.Enhance.count(p, rpg.core.item.ModItems.POTENTIAL_SCROLL.get()),
                rpg.core.equip.Enhance.count(p, rpg.core.item.ModItems.EPIC_POTENTIAL_SCROLL.get()), ce[0], ce[1], ce[2],
                rpg.core.equip.Enhance.count(p, rpg.core.item.ModItems.POWERFUL_FLAME.get()), rpg.core.equip.Enhance.count(p, rpg.core.item.ModItems.ETERNAL_FLAME.get()),
                rpg.core.equip.Enhance.count(p, rpg.core.item.ModItems.BLACK_FLAME.get()), (int) Math.min(Integer.MAX_VALUE, rpg.core.equip.Power.of(p)));
        CH.send(PacketDistributor.PLAYER.with(() -> p), new OpenEnhance(open, locs, gear, slots, scrolls, frags, extra));
    }

    /** extra: 큐브 · 부여 주문서 · 에픽 부여 주문서 · 천장 카운트 레어·에픽·유니크 · 강력한·영원한·검은 불꽃 */
    public record OpenEnhance(boolean open, java.util.List<Integer> locs, java.util.List<net.minecraft.world.item.ItemStack> gear,
                              java.util.List<Integer> slots, java.util.List<net.minecraft.world.item.ItemStack> scrolls, int frags,
                              java.util.List<Integer> extra) {
        OpenEnhance(FriendlyByteBuf b) {
            this(b.readBoolean(), b.readList(FriendlyByteBuf::readVarInt), b.readList(FriendlyByteBuf::readItem),
                    b.readList(FriendlyByteBuf::readVarInt), b.readList(FriendlyByteBuf::readItem), b.readVarInt(), b.readList(FriendlyByteBuf::readVarInt));
        }
        void encode(FriendlyByteBuf b) {
            b.writeBoolean(open);
            b.writeCollection(locs, FriendlyByteBuf::writeVarInt);
            b.writeCollection(gear, (x, s) -> x.writeItem(s));
            b.writeCollection(slots, FriendlyByteBuf::writeVarInt);
            b.writeCollection(scrolls, (x, s) -> x.writeItem(s));
            b.writeVarInt(frags);
            b.writeCollection(extra, FriendlyByteBuf::writeVarInt);
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.EnhanceScreen.receive(open, locs, gear, slots, scrolls, frags, extra));
            ctx.get().setPacketHandled(true);
        }
    }

    /** op: 0 열기 · 1 스타포스(arg 1 = 파괴 방지) · 2 주문서(arg = 주문서 인벤 칸) · 3 복구(arg = 재료 인벤 칸) · 4 조각 합성 */
    public record EnhanceAction(int op, int loc, int arg) {
        EnhanceAction(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt(), b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(op); b.writeVarInt(loc); b.writeVarInt(arg); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                switch (op) {
                    case 1 -> rpg.core.equip.Enhance.starforce(p, loc, arg == 1);
                    case 2 -> rpg.core.equip.Enhance.applyScroll(p, loc, arg);
                    case 3 -> rpg.core.equip.Enhance.restore(p, loc, arg);
                    case 4 -> rpg.core.equip.Enhance.synth(p);
                    case 5 -> rpg.core.equip.Potential.grant(p, rpg.core.equip.Enhance.at(p, loc), arg == 1);
                    case 6 -> rpg.core.equip.Potential.reset(p, rpg.core.equip.Enhance.at(p, loc), false);
                    case 7 -> rpg.core.equip.Potential.reset(p, rpg.core.equip.Enhance.at(p, loc), true);
                    case 8 -> rpg.core.equip.Potential.choose(p, rpg.core.equip.Enhance.at(p, loc), false, false);
                    case 9 -> rpg.core.equip.Potential.choose(p, rpg.core.equip.Enhance.at(p, loc), true, arg == 1);
                    case 10 -> rpg.core.equip.Flame.use(p, rpg.core.equip.Enhance.at(p, loc), arg == 1 ? "eternal" : arg == 2 ? "black" : "powerful");
                    case 11 -> rpg.core.equip.Flame.choose(p, rpg.core.equip.Enhance.at(p, loc), arg == 1);
                    case 12 -> rpg.core.equip.Potential.unlock(p, rpg.core.equip.Enhance.at(p, loc));
                    default -> {}
                }
                rpg.core.equip.PlayerEquip.get(p).dirty = true;   // 능력치 다시 계산
                p.getInventory().setChanged();
                sendEnhance(p, op == 0);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 제작 NPC ─────────
    /** 레시피 한 줄 (클라 표시용) */
    public record CraftLine(String tab, String result, int count, long gold, java.util.List<String> mats, java.util.List<Integer> counts) {}

    public static void openCraft(ServerPlayer p, int npcId, rpg.core.craft.Crafts.Station s) {
        java.util.List<CraftLine> lines = new java.util.ArrayList<>();
        for (var r : s.recipes()) {
            java.util.List<String> m = new java.util.ArrayList<>();
            java.util.List<Integer> c = new java.util.ArrayList<>();
            for (var x : r.mats()) { m.add(x.item()); c.add(x.count()); }
            lines.add(new CraftLine(r.tab(), r.result(), r.count(), r.gold(), m, c));
        }
        CH.send(PacketDistributor.PLAYER.with(() -> p), new OpenCraft(npcId, s.name(), s.tabs(), lines));
    }

    // ───────── 서버 → 클라: 제작 창 ─────────
    public record OpenCraft(int npc, String name, java.util.List<String> tabs, java.util.List<CraftLine> lines) {
        OpenCraft(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(), b.readList(FriendlyByteBuf::readUtf), b.readList(x -> new CraftLine(x.readUtf(), x.readUtf(),
                    x.readVarInt(), x.readVarLong(), x.readList(FriendlyByteBuf::readUtf), x.readList(FriendlyByteBuf::readVarInt))));
        }
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(npc); b.writeUtf(name);
            b.writeCollection(tabs, FriendlyByteBuf::writeUtf);
            b.writeCollection(lines, (x, l) -> {
                x.writeUtf(l.tab()); x.writeUtf(l.result()); x.writeVarInt(l.count()); x.writeVarLong(l.gold());
                x.writeCollection(l.mats(), FriendlyByteBuf::writeUtf);
                x.writeCollection(l.counts(), FriendlyByteBuf::writeVarInt);
            });
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.CraftScreen.open(npc, name, tabs, lines));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 제작 (index 번 레시피를 times 번) ─────────
    public record CraftAction(int npc, int index, int times) {
        CraftAction(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt(), b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(npc); b.writeVarInt(index); b.writeVarInt(times); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) rpg.core.craft.Crafts.craft(p, npc, index, times);
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 퀘스트 ─────────
    public static void questSync(ServerPlayer p) {
        PlayerData d = rpg.core.data.RpgData.get(p);
        CH.send(PacketDistributor.PLAYER.with(() -> p), new QuestSync(new java.util.LinkedHashMap<>(d.questActive), new java.util.ArrayList<>(d.questDone)));
    }

    /** 퀘스트 지점 위치 (id → "차원|x|y|z") */
    public static QuestPoints questPoints(net.minecraft.server.MinecraftServer server) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        rpg.core.quest.QuestPlaces.get(server).points.forEach((k, v) ->
                m.put(k, v.dim() + "|" + v.pos().getX() + "|" + v.pos().getY() + "|" + v.pos().getZ()));
        return new QuestPoints(m);
    }

    public static void questPointsAll(net.minecraft.server.MinecraftServer server) {
        CH.send(PacketDistributor.ALL.noArg(), questPoints(server));
    }

    public record QuestPoints(java.util.Map<String, String> points) {
        QuestPoints(FriendlyByteBuf b) { this(b.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf)); }
        void encode(FriendlyByteBuf b) { b.writeMap(points, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.ClientQuests.setPoints(points));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void questTalk(ServerPlayer p, int entity, String npc) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new QuestTalk(entity, npc));
    }

    /** 서버 → 클라: quests.json 원문 */
    public record QuestDefs(String json) {
        QuestDefs(FriendlyByteBuf b) { this(b.readUtf(1048576)); }
        void encode(FriendlyByteBuf b) { b.writeUtf(json, 1048576); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.quest.Quests.parse(json));
            ctx.get().setPacketHandled(true);
        }
    }

    /** 서버 → 클라: 내 퀘스트 (진행 중 + 처치 수, 완료 목록) */
    public record QuestSync(java.util.Map<String, int[]> active, java.util.List<String> done) {
        QuestSync(FriendlyByteBuf b) { this(b.readMap(java.util.LinkedHashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarIntArray), b.readList(FriendlyByteBuf::readUtf)); }
        void encode(FriendlyByteBuf b) {
            b.writeMap(active, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarIntArray);
            b.writeCollection(done, FriendlyByteBuf::writeUtf);
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.ClientQuests.set(active, done));
            ctx.get().setPacketHandled(true);
        }
    }

    /** 서버 → 클라: NPC 대화 창 열기 */
    public record QuestTalk(int entity, String npc) {
        QuestTalk(FriendlyByteBuf b) { this(b.readVarInt(), b.readUtf()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(entity); b.writeUtf(npc); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.QuestTalkScreen.open(entity, npc));
            ctx.get().setPacketHandled(true);
        }
    }

    /** 클라 → 서버: 퀘스트 (kind 0 수락 · 1 완료 · 2 포기) */
    public record QuestAction(int kind, String quest, int entity) {
        public QuestAction(FriendlyByteBuf b) { this(b.readVarInt(), b.readUtf(), b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(kind); b.writeUtf(quest); b.writeVarInt(entity); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) switch (kind) {
                case 0 -> rpg.core.quest.QuestEvents.accept(p, entity, quest);
                case 1 -> rpg.core.quest.QuestEvents.complete(p, entity, quest);
                case 2 -> rpg.core.quest.QuestEvents.abandon(p, quest);
                default -> {}
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public static void levelUp(ServerPlayer p, int level) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new LevelUp(level));
    }

    public static void itemNote(ServerPlayer p, String text) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new ItemNote(text));
    }

    /** 바닥 예고를 근처(48칸) 플레이어에게 */
    public static void telegraph(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 pos,
                                 net.minecraft.world.phys.Vec3 dir, int shape, double a, double b, int ticks) {
        Telegraph t = new Telegraph(shape, pos.x, pos.y, pos.z, dir.x, dir.z, (float) a, (float) b, ticks);
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(pos) < 48 * 48) CH.send(PacketDistributor.PLAYER.with(() -> p), t);
    }

    // ───────── 서버 → 클라: 바닥 예고 (0 원 a=반경 · 1 직선 a=길이 b=폭 · 2 부채꼴 a=반경 b=각도) ─────────
    public record Telegraph(int shape, double x, double y, double z, double dx, double dz, float a, float b, int ticks) {
        Telegraph(FriendlyByteBuf f) {
            this(f.readByte(), f.readDouble(), f.readDouble(), f.readDouble(), f.readDouble(), f.readDouble(), f.readFloat(), f.readFloat(), f.readVarInt());
        }
        void encode(FriendlyByteBuf f) {
            f.writeByte(shape); f.writeDouble(x); f.writeDouble(y); f.writeDouble(z); f.writeDouble(dx); f.writeDouble(dz);
            f.writeFloat(a); f.writeFloat(b); f.writeVarInt(ticks);
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.Telegraphs.add(this));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 차지 스킬 키 뗌 ─────────
    public record SkillRelease(int slot, double ax, double ay, double az) {
        SkillRelease(FriendlyByteBuf b) { this(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(slot); b.writeDouble(ax); b.writeDouble(ay); b.writeDouble(az); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                aim(p, ax, ay, az);
                rpg.core.skill.Skills.release(p);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 스킬을 키 칸에 넣기 (id 빈칸 = 빼기) ─────────
    public record SetSlot(int slot, String id) {
        SetSlot(FriendlyByteBuf b) { this(b.readVarInt(), b.readUtf()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(slot); b.writeUtf(id); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) rpg.core.skill.Skills.setSlot(p, slot, id);
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 레벨업 연출 ─────────
    public record LevelUp(int level) {
        LevelUp(FriendlyByteBuf b) { this(b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(level); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.RpgHud.levelUp(level));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 아이템 획득 알림 ─────────
    public record ItemNote(String text) {
        ItemNote(FriendlyByteBuf b) { this(b.readUtf()); }
        void encode(FriendlyByteBuf b) { b.writeUtf(text); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.RpgHud.addNote(text, 0xFFFFFFFF));
            ctx.get().setPacketHandled(true);
        }
    }

    public static void openShop(ServerPlayer p, int npcId, rpg.core.shop.Shops.Shop s) {
        java.util.List<String> items = new java.util.ArrayList<>();
        java.util.List<Long> prices = new java.util.ArrayList<>();
        for (var o : s.offers()) { items.add(o.item()); prices.add(o.price()); }
        CH.send(PacketDistributor.PLAYER.with(() -> p), new OpenShop(npcId, s.name(), items, prices,
                new java.util.LinkedHashMap<>(rpg.core.shop.Shops.SELL)));
    }

    // ───────── 서버 → 클라: 상점 창 (사기 목록 + 판매가표) ─────────
    public record OpenShop(int npc, String name, java.util.List<String> items, java.util.List<Long> prices,
                           java.util.Map<String, Long> sell) {
        OpenShop(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readUtf(), b.readList(FriendlyByteBuf::readUtf), b.readList(FriendlyByteBuf::readVarLong),
                    b.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarLong));
        }
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(npc); b.writeUtf(name);
            b.writeCollection(items, FriendlyByteBuf::writeUtf);
            b.writeCollection(prices, FriendlyByteBuf::writeVarLong);
            b.writeMap(sell, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarLong);
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.ShopScreen.open(npc, name, items, prices, sell));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 상점 (kind 0 사기 index·count / 1 팔기 slot·all(count=1) / 2 재료 전부 팔기) ─────────
    public record ShopAction(int npc, int kind, int index, int count) {
        ShopAction(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(npc); b.writeVarInt(kind); b.writeVarInt(index); b.writeVarInt(count); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) switch (kind) {
                case 0 -> rpg.core.shop.Shops.buy(p, npc, index, count);
                case 1 -> rpg.core.shop.Shops.sell(p, npc, index, count == 1);
                case 2 -> rpg.core.shop.Shops.sellMaterials(p, npc);
                default -> {}
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public static void openJob(ServerPlayer p, int family, int state, int pending) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new OpenJob(family, state, pending));
    }

    // ───────── 서버 → 클라: 전직 창 (state 는 Jobs.state, pending = 고른 갈래) ─────────
    public record OpenJob(int family, int state, int pending) {
        OpenJob(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt(), b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(family); b.writeVarInt(state); b.writeVarInt(pending); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.JobScreen.open(family, state, pending));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 전직하기 ─────────
    /** action: 0 1차 전직 · 1 갈래 고르기(arg) · 2 갈래 포기 · 3 시련 시작 · 4 2차 전직 완료 */
    public record JobAdvance(int family, int action, int arg) {
        JobAdvance(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt(), b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(family); b.writeVarInt(action); b.writeVarInt(arg); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                String err = rpg.core.job.Jobs.action(p, family, action, arg);
                if (err != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("[전직] " + err)
                        .withStyle(net.minecraft.ChatFormatting.RED));
                else if (action == 1 || action == 2) {   // 창 새로고침
                    var d = rpg.core.data.RpgData.get(p);
                    openJob(p, family, rpg.core.job.Jobs.state(d, family), d.pendingBranch);
                }
            }
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 원거리 평타 (궁수·마법사) ─────────
    public record Basic(int dummy) {
        Basic(FriendlyByteBuf b) { this(b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(dummy); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) rpg.core.job.BasicAttack.fire(p);
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 장비 창 열기 (인벤토리 키) ─────────
    public record OpenEquip(int dummy) {
        OpenEquip(FriendlyByteBuf b) { this(b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(dummy); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null && !p.isCreative()) rpg.core.equip.EquipEvents.open(p);
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 서버 → 클라: 파티 정보 ─────────
    public record Member(String name, int level, float hp, float maxHp, boolean down, boolean leader, boolean near) {}

    public static void partyTo(ServerPlayer p) {
        java.util.List<Member> list = new java.util.ArrayList<>();
        var party = rpg.core.party.Parties.of(p);
        if (party != null && p.getServer() != null) {
            for (java.util.UUID id : party.members) {
                ServerPlayer m = p.getServer().getPlayerList().getPlayer(id);
                if (m == null) continue;
                PlayerData d = rpg.core.data.RpgData.get(m);
                boolean near = m.level() == p.level() && m.distanceToSqr(p) <= rpg.core.balance.Balance.partyRange * rpg.core.balance.Balance.partyRange;
                list.add(new Member(m.getGameProfile().getName(), d.level, (float) d.hp, (float) d.maxHp, d.isDown(), party.leader.equals(id), near));
            }
        }
        java.util.UUID inv = rpg.core.party.Parties.pendingInvite(p);
        String inviter = "";
        if (inv != null && p.getServer() != null) {
            ServerPlayer ip = p.getServer().getPlayerList().getPlayer(inv);
            if (ip != null) inviter = ip.getGameProfile().getName();
        }
        CH.send(PacketDistributor.PLAYER.with(() -> p), new PartySync(list, inviter));
    }

    public record PartySync(java.util.List<Member> members, String inviter) {
        PartySync(FriendlyByteBuf b) { this(readMembers(b), b.readUtf(32)); }
        private static java.util.List<Member> readMembers(FriendlyByteBuf b) {
            int n = b.readVarInt();
            java.util.List<Member> l = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) l.add(new Member(b.readUtf(32), b.readVarInt(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
            return l;
        }
        void encode(FriendlyByteBuf b) {
            b.writeVarInt(members.size());
            for (Member m : members) {
                b.writeUtf(m.name(), 32); b.writeVarInt(m.level()); b.writeFloat(m.hp()); b.writeFloat(m.maxHp());
                b.writeBoolean(m.down()); b.writeBoolean(m.leader()); b.writeBoolean(m.near());
            }
            b.writeUtf(inviter, 32);
        }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.ClientParty.set(members, inviter));
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 파티 창 버튼 (0 초대 1 수락 2 거절 3 나가기 4 추방) ─────────
    public record PartyAction(int action, String name) {
        PartyAction(FriendlyByteBuf b) { this(b.readVarInt(), b.readUtf(32)); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(action); b.writeUtf(name, 32); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) rpg.core.party.PartyCommand.act(p, action, name);
            ctx.get().setPacketHandled(true);
        }
    }

    public static void defsTo(ServerPlayer p) {
        CH.send(PacketDistributor.PLAYER.with(() -> p), new DefsSync(rpg.core.skill.Defs.skillsJson, rpg.core.skill.Defs.passivesJson, rpg.core.equip.GearDefs.json));
        CH.send(PacketDistributor.PLAYER.with(() -> p), new QuestDefs(rpg.core.quest.Quests.json));
        CH.send(PacketDistributor.PLAYER.with(() -> p), questPoints(p.server));
        questSync(p);
    }

    // ───────── 클라 → 서버: 스킬 키 (0~5 = Q E R F Z X) ─────────
    /** ax ay az = 화면 가운데(조준점)가 가리키는 곳 (3인칭·숄더 시점에서도 조준점으로 쏘게) */
    /** mx mz = 누르고 있는 방향키를 월드 방향으로 바꾼 것 (안 누르면 0, 0) — 대쉬 방향용 */
    public record UseSkill(int slot, double ax, double ay, double az, float mx, float mz) {
        UseSkill(FriendlyByteBuf b) { this(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(slot); b.writeDouble(ax); b.writeDouble(ay); b.writeDouble(az); b.writeFloat(mx); b.writeFloat(mz); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                aim(p, ax, ay, az);
                var d = rpg.core.data.RpgData.get(p);
                d.moveX = Float.isFinite(mx) ? mx : 0;
                d.moveZ = Float.isFinite(mz) ? mz : 0;
                rpg.core.skill.Skills.use(p, slot);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    // ───────── 클라 → 서버: 구르기 (이동 방향) ─────────
    // ───────── 클라 → 서버: Epic Fight 구르기 키를 눌렀음 (회피 본능) ─────────
    public record Rolled(int dummy) {
        Rolled(FriendlyByteBuf b) { this(b.readVarInt()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(dummy); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) rpg.core.skill.Skills.rolled(p);
            ctx.get().setPacketHandled(true);
        }
    }

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
    public record DefsSync(String skills, String passives, String gear) {
        DefsSync(FriendlyByteBuf b) { this(b.readUtf(262144), b.readUtf(262144), b.readUtf(262144)); }
        void encode(FriendlyByteBuf b) { b.writeUtf(skills, 262144); b.writeUtf(passives, 262144); b.writeUtf(gear, 262144); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                rpg.core.skill.Defs.parse(skills, passives);
                rpg.core.equip.GearDefs.parse(gear);
            });
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

    public static void damageText(Mob mob, double amount, int type) {
        CH.send(PacketDistributor.TRACKING_ENTITY.with(() -> mob), new DamageText(mob.getId(), amount, type));
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
    /** type 0 보통 · 1 치명타 · 2 독 (초록) · 3 출혈 (빨강) · 4 화상 (주황), 지속 피해는 작게 */
    public record DamageText(int id, double amount, int type) {
        DamageText(FriendlyByteBuf b) { this(b.readVarInt(), b.readDouble(), b.readByte()); }
        void encode(FriendlyByteBuf b) { b.writeVarInt(id); b.writeDouble(amount); b.writeByte(type); }
        void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> rpg.core.client.WorldText.addDamage(id, amount, type));
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
            d.write(buf, Combat.statAttack(p, d), Combat.defense(d), p.level().getGameTime());
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
