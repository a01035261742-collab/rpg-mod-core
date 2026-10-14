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
        d.questActive.put(qid, new int[q.goals().size()]);
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
        // 수집 재료 가져가기
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
        Net.questSync(p);
        msg(p, "퀘스트 포기: " + (q == null ? qid : q.name()), ChatFormatting.GRAY);
    }

    // ───────── 처치 (근처 파티원 모두) ─────────
    public static void onKill(List<ServerPlayer> members, MobData m) {
        if (m == null || m.key.isEmpty()) return;
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
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(item));
        if (it == null) return;
        ItemStack st = new ItemStack(it, Math.max(1, n));
        if (rpg.core.equip.Gear.def(st) != null) { st.setCount(1); rpg.core.equip.Gear.roll(st); }
        String name = st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
        if (!p.getInventory().add(st) && !st.isEmpty()) p.drop(st, false);
        Net.itemNote(p, "§b" + name);
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }
}
