package rpg.core.quest;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.RpgCore;
import rpg.core.combat.MobData;
import rpg.core.data.PlayerData;
import rpg.core.data.Progress;
import rpg.core.data.RpgData;
import rpg.core.net.Net;

/** 퀘스트 NPC (주민, 태그 rpgQuest) · 수락 · 완료 · 포기 · 처치 진행도 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class QuestEvents {
    private QuestEvents() {}

    public static final String NPC_TAG = "rpgQuest";

    public static String npcOf(Entity e) {
        return e instanceof Villager ? e.getPersistentData().getString(NPC_TAG) : "";
    }

    public static void spawn(ServerPlayer p, String npcId) {
        Villager v = new Villager(EntityType.VILLAGER, p.level());
        v.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot() + 180, 0);
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistenceRequired();
        v.getPersistentData().putString(NPC_TAG, npcId);
        v.setCustomName(Component.literal(Quests.npcName(npcId)).withStyle(ChatFormatting.GREEN));
        v.setCustomNameVisible(true);
        p.level().addFreshEntity(v);
        QuestPlaces pl = QuestPlaces.get(p.server);
        pl.npcs.computeIfAbsent(npcId, k -> new java.util.ArrayList<>())
                .add(new QuestPlaces.Place(p.level().dimension().location().toString(), p.blockPosition()));
        pl.setDirty();
    }

    // ───────── 지점 ─────────
    public static void setPoint(ServerPlayer p, String id) {
        QuestPlaces pl = QuestPlaces.get(p.server);
        pl.points.put(id, new QuestPlaces.Place(p.level().dimension().location().toString(), p.blockPosition()));
        pl.setDirty();
        Net.questPointsAll(p.server);
    }

    public static boolean removePoint(net.minecraft.server.MinecraftServer server, String id) {
        QuestPlaces pl = QuestPlaces.get(server);
        boolean had = pl.points.remove(id) != null;
        pl.setDirty();
        Net.questPointsAll(server);
        return had;
    }

    /** 배치 점검: NPC·지점 중 안 세운 것 / 세운 것 좌표 */
    public static List<Component> check(net.minecraft.server.MinecraftServer server) {
        QuestPlaces pl = QuestPlaces.get(server);
        List<Component> out = new java.util.ArrayList<>();
        out.add(Component.literal("── 퀘스트 NPC ──").withStyle(ChatFormatting.GOLD));
        for (Quests.Npc n : Quests.NPCS.values()) {
            var ps = pl.npcs.get(n.id());
            if (ps == null || ps.isEmpty()) out.add(Component.literal(" ✘ " + n.name() + " (" + n.id() + ")  → /rpg npc quest " + n.id()).withStyle(ChatFormatting.RED));
            else out.add(Component.literal(" ✔ " + n.name() + " (" + n.id() + ")  " + ps.get(ps.size() - 1).text() + (ps.size() > 1 ? "  외 " + (ps.size() - 1) + "곳" : "")).withStyle(ChatFormatting.GREEN));
        }
        out.add(Component.literal("── 퀘스트 지점 ──").withStyle(ChatFormatting.GOLD));
        for (Quests.Point pt : Quests.POINTS.values()) {
            var place = pl.points.get(pt.id());
            if (place == null) out.add(Component.literal(" ✘ " + pt.name() + " (" + pt.id() + ")  → /rpg quest point add " + pt.id()).withStyle(ChatFormatting.RED));
            else out.add(Component.literal(" ✔ " + pt.name() + " (" + pt.id() + ")  " + place.text()).withStyle(ChatFormatting.GREEN));
        }
        return out;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void talk(PlayerInteractEvent.EntityInteract e) {
        String id = npcOf(e.getTarget());
        if (id.isEmpty()) return;
        e.setCanceled(true);
        if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!Quests.NPCS.containsKey(id)) {
            sp.sendSystemMessage(Component.literal("[퀘스트] quests.json 에 NPC '" + id + "' 가 없어요").withStyle(ChatFormatting.RED));
            return;
        }
        Net.questSync(sp);
        Net.questTalk(sp, e.getTarget().getId(), id);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void invulnerable(LivingAttackEvent e) {
        if (!npcOf(e.getEntity()).isEmpty()) e.setCanceled(true);
    }

    /** NPC 근처(8칸)면 그 NPC id, 아니면 null */
    private static String near(ServerPlayer p, int entity) {
        Entity e = p.level().getEntity(entity);
        if (e == null || e.distanceToSqr(p) > 64) return null;
        String id = npcOf(e);
        return id.isEmpty() ? null : id;
    }

    public static int count(ServerPlayer p, String item) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(item));
        if (it == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(it)) n += s.getCount();
        return n;
    }

    // ───────── 수락 ─────────
    public static void accept(ServerPlayer p, int entity, String qid) {
        Quests.Quest q = Quests.QUESTS.get(qid);
        String npc = near(p, entity);
        if (q == null || npc == null || !npc.equals(q.npc())) return;
        PlayerData d = RpgData.get(p);
        if (!Quests.available(q, d.level, d.questActive, d.questDone)) return;
        d.questActive.put(qid, new int[q.goals().size() + 1]);   // 맨 끝 칸 = 가져오기 주웠는지
        for (String[] it : q.give()) give(p, it[0], Integer.parseInt(it[1]));
        p.inventoryMenu.broadcastChanges();
        Net.questSync(p);
        msg(p, "퀘스트 시작: " + q.name(), ChatFormatting.YELLOW);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.0f);
        // 목표 없는 배달 퀘스트인데 받은 NPC = 완료 NPC 면 바로 완료 가능 (보통은 다른 NPC)
    }

    // ───────── 완료 ─────────
    public static void complete(ServerPlayer p, int entity, String qid) {
        Quests.Quest q = Quests.QUESTS.get(qid);
        String npc = near(p, entity);
        if (q == null || npc == null || !npc.equals(q.turnIn())) return;
        PlayerData d = RpgData.get(p);
        int[] prog = d.questActive.get(qid);
        if (prog == null) return;
        if (!Quests.complete(q, prog, it -> count(p, it))) {
            msg(p, "아직 목표를 다 못 채웠어요", ChatFormatting.RED);
            return;
        }
        finish(p, q);
    }

    /** 완료 처리 (NPC 대화 또는 지점 도착) */
    private static void finish(ServerPlayer p, Quests.Quest q) {
        String qid = q.id();
        PlayerData d = RpgData.get(p);
        // 수집 재료·배달 물건 가져가기
        for (Quests.Goal g : q.goals()) if (g.type().equals("item")) take(p, g.target(), g.count());
        d.questActive.remove(qid);
        d.questDone.add(qid);
        // 보상
        Quests.Reward r = q.reward();
        if (r.gold() > 0) d.gold += r.gold();
        for (String[] it : r.items()) give(p, it[0], Integer.parseInt(it[1]));
        Net.reward(p, r.exp(), r.gold());
        if (r.exp() > 0) Progress.giveExp(p, r.exp());
        RpgData.sync(p);
        p.inventoryMenu.broadcastChanges();
        Net.questSync(p);
        p.sendSystemMessage(Component.literal("[퀘스트 완료] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(q.name()).withStyle(ChatFormatting.YELLOW)));
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
    }

    // ───────── 포기 ─────────
    public static void abandon(ServerPlayer p, String qid) {
        PlayerData d = RpgData.get(p);
        Quests.Quest q = Quests.QUESTS.get(qid);
        if (d.questActive.remove(qid) == null) return;
        if (q != null) {   // 배달·가져오기 물건 회수
            for (String[] it : q.give()) take(p, it[0], Integer.parseInt(it[1]));
            if (q.fetch()) take(p, q.fetchItem(), 1);
            p.inventoryMenu.broadcastChanges();
        }
        Net.questSync(p);
        msg(p, "퀘스트 포기: " + (q == null ? qid : q.name()), ChatFormatting.GRAY);
    }

    // ───────── 지점 도착 (0.5초마다) ─────────
    @SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
        if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        PlayerData d = RpgData.get(p);
        if (d.questActive.isEmpty()) return;
        QuestPlaces pl = null;
        String dim = p.level().dimension().location().toString();
        for (var en : new java.util.ArrayList<>(d.questActive.entrySet())) {
            Quests.Quest q = Quests.QUESTS.get(en.getKey());
            if (q == null || (!q.toPoint() && !q.fetch())) continue;
            if (pl == null) pl = QuestPlaces.get(p.server);
            int[] prog = en.getValue();
            if (q.fetch() && count(p, q.fetchItem()) == 0 && near(pl.points.get(q.fetchPoint()), dim, p)) {   // 잃어버렸으면 다시 주움
                if (prog.length <= q.goals().size()) { prog = java.util.Arrays.copyOf(prog, q.goals().size() + 1); en.setValue(prog); }
                prog[q.goals().size()] = 1;
                give(p, q.fetchItem(), 1);
                p.inventoryMenu.broadcastChanges();
                title(p, q.fetchText().isEmpty() ? "찾았다!" : q.fetchText());
                p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.8f);
                Net.questSync(p);
            }
            if (q.toPoint() && near(pl.points.get(q.point()), dim, p) && Quests.complete(q, prog, it -> count(p, it))) {
                if (!q.done().isEmpty()) title(p, q.done().get(0));
                finish(p, q);
            }
        }
    }

    private static boolean near(QuestPlaces.Place place, String dim, ServerPlayer p) {
        return place != null && place.dim().equals(dim) && place.pos().distToCenterSqr(p.position()) <= 3.5 * 3.5;
    }

    private static void title(ServerPlayer p, String text) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.empty()));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
    }

    // ───────── 처치 (근처 파티원 모두) ─────────
    public static void onKill(List<ServerPlayer> members, MobData m) {
        if (m == null || m.key.isEmpty()) return;
        Dailies.onKill(members, m);   // 일일 퀘스트
        for (ServerPlayer p : members) {
            PlayerData d = RpgData.get(p);
            boolean changed = false;
            for (var en : d.questActive.entrySet()) {
                Quests.Quest q = Quests.QUESTS.get(en.getKey());
                if (q == null) continue;
                int[] prog = en.getValue();
                if (prog.length < q.goals().size()) { prog = java.util.Arrays.copyOf(prog, q.goals().size()); en.setValue(prog); }
                for (int i = 0; i < q.goals().size(); i++) {
                    Quests.Goal g = q.goals().get(i);
                    if (!g.type().equals("kill") || prog[i] >= g.count()) continue;
                    if (!g.target().equals(m.key) && !g.target().equals("*")) continue;
                    if (g.elite() && !m.elite) continue;
                    prog[i]++;
                    changed = true;
                    String label = g.label().isEmpty() ? m.name.replace("★", "").trim() : g.label();
                    Net.itemNote(p, "§e" + label + " " + prog[i] + "/" + g.count() + (prog[i] >= g.count() ? " §a완료" : ""));
                }
            }
            if (changed) Net.questSync(p);
        }
    }

    private static void take(ServerPlayer p, String item, int n) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(item));
        if (it == null) return;
        for (ItemStack s : p.getInventory().items) {
            if (n <= 0) break;
            if (!s.is(it)) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
    }

    private static void give(ServerPlayer p, String item, int n) {
        ItemStack st = rpg.core.item.ItemIds.stack(item, n);
        if (st.isEmpty()) return;
        if (rpg.core.equip.Gear.def(st) != null) { st.setCount(1); rpg.core.equip.Gear.roll(st); }
        String name = st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
        if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
        Net.itemNote(p, "§b" + name);
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }
}
