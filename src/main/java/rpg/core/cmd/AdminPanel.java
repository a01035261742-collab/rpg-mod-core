package rpg.core.cmd;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/**
 * 관리자 패널 (OP 전용): /rpg 관리 → 채팅에 누르는 버튼. ▶ 는 다음 줄에 고르는 버튼.
 * /rpg npc 삭제 = 5칸 안 가장 가까운 우리 NPC 지우기 (퀘스트 NPC 면 위치 기록도)
 */
public final class AdminPanel {
    private AdminPanel() {}

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        for (String n : new String[]{"관리", "admin"}) {
            root.then(Commands.literal(n).requires(s -> s.hasPermission(2))
                    .executes(c -> { main(c.getSource()); return 1; })
                    .then(Commands.literal("npc").executes(c -> { npcMenu(c.getSource()); return 1; }))
                    .then(Commands.literal("quest").executes(c -> { questMenu(c.getSource()); return 1; }))
                    .then(Commands.literal("boss").executes(c -> { bossMenu(c.getSource()); return 1; }))
                    .then(Commands.literal("gear").executes(c -> { gearMenu(c.getSource()); return 1; })));
        }
        for (String n : new String[]{"삭제", "remove"})
            root.then(Commands.literal("npc").requires(s -> s.hasPermission(2))
                    .then(Commands.literal(n).executes(c -> removeNpc(c.getSource().getPlayerOrException()))));
    }

    // ───────── 버튼 ─────────
    private static MutableComponent btn(String label, String cmd, ChatFormatting col) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(col)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cmd).withStyle(ChatFormatting.GRAY))));
    }

    private static MutableComponent row(String head, MutableComponent... bs) {
        MutableComponent r = Component.literal(head + "  ").withStyle(ChatFormatting.GRAY);
        for (MutableComponent b : bs) r.append(b).append(" ");
        return r;
    }

    private static void say(CommandSourceStack src, Component c) { src.sendSystemMessage(c); }

    private static void title(CommandSourceStack src, String t) {
        say(src, Component.literal("━━━━━ " + t + " ━━━━━").withStyle(ChatFormatting.GOLD));
    }

    static void main(CommandSourceStack src) {
        ChatFormatting a = ChatFormatting.AQUA, y = ChatFormatting.YELLOW, g = ChatFormatting.GREEN, r = ChatFormatting.RED, l = ChatFormatting.LIGHT_PURPLE;
        title(src, "RPG 관리자 패널");
        say(src, row("캐릭터", btn("Lv.10", "/rpg level 10", a), btn("Lv.30", "/rpg level 30", a), btn("Lv.40", "/rpg level 40", a),
                btn("골드 +100만", "/rpg gold 1000000", y), btn("회복", "/rpg heal", g), btn("스탯 +50", "/rpg sp 50", a), btn("초기화 주문서", "/rpg resetscroll", a)));
        say(src, row("월드", btn("지점 보기", "/rpg 보기", l), btn("여기를 스폰으로", "/rpg 스폰설정", l), btn("문·창고 받기", "/rpg 포탈", l),
                btn("사냥터 목록", "/rpg zone list", g), btn("사냥터 지팡이", "/rpg wand", g)));
        say(src, row("NPC", btn("세우기 ▶", "/rpg 관리 npc", g), btn("퀘스트 NPC ▶", "/rpg 관리 quest", g), btn("삭제 (5칸 안)", "/rpg npc 삭제", r)));
        say(src, row("보스·레이드", btn("보스 ▶", "/rpg 관리 boss", r), btn("레이드 목록", "/rpg 레이드 목록", l),
                btn("일일 초기화", "/rpg daily reset", y), btn("일일 완료", "/rpg daily complete", y)));
        say(src, row("테스트", btn("장비 받기 ▶", "/rpg 관리 gear", a), btn("몹 목록", "/rpg mobs", a), btn("명령어 전체", "/rpg help admin", ChatFormatting.WHITE)));
    }

    static void npcMenu(CommandSourceStack src) {
        title(src, "NPC 세우기 (지금 자리)");
        MutableComponent shops = Component.literal("");
        rpg.core.shop.Shops.SHOPS.forEach((id, s) -> shops.append(btn(s.name() + "(" + id + ")", "/rpg npc shop " + id, ChatFormatting.AQUA)).append(" "));
        say(src, row("상점", shops));
        MutableComponent crafts = Component.literal("");
        rpg.core.craft.Crafts.STATIONS.forEach((id, s) -> crafts.append(btn(s.name(), "/rpg npc craft " + id, ChatFormatting.GOLD)).append(" "));
        say(src, row("제작", crafts));
        say(src, row("기타", btn("창고지기", "/rpg npc storage", ChatFormatting.YELLOW),
                btn("전사 교관", "/rpg npc job 1", ChatFormatting.RED), btn("궁수 교관", "/rpg npc job 2", ChatFormatting.GREEN),
                btn("마법사 교관", "/rpg npc job 3", ChatFormatting.BLUE), btn("도적 교관", "/rpg npc job 4", ChatFormatting.DARK_PURPLE)));
        say(src, row("", btn("퀘스트 NPC ▶", "/rpg 관리 quest", ChatFormatting.GREEN), btn("◀ 패널", "/rpg 관리", ChatFormatting.WHITE)));
    }

    static void questMenu(CommandSourceStack src) {
        title(src, "퀘스트 NPC 세우기 (지금 자리)");
        MutableComponent r = Component.literal("");
        for (String id : rpg.core.quest.Quests.NPCS.keySet())
            r.append(btn(rpg.core.quest.Quests.npcName(id), "/rpg npc quest " + id, ChatFormatting.GREEN)).append(" ");
        say(src, r);
        say(src, row("", btn("◀ 패널", "/rpg 관리", ChatFormatting.WHITE)));
    }

    static void bossMenu(CommandSourceStack src) {
        title(src, "보스");
        MutableComponent spawn = Component.literal(""), point = Component.literal("");
        for (var e : rpg.core.combat.Boss.DEFS.keySet()) {
            String nm = rpg.core.balance.Balance.MOBS.containsKey(e) ? rpg.core.balance.Balance.MOBS.get(e).name() : e;
            spawn.append(btn(nm, "/rpg boss spawn " + e, ChatFormatting.RED)).append(" ");
            point.append(btn(nm, "/rpg boss point add " + e, ChatFormatting.GOLD)).append(" ");
        }
        say(src, row("테스트 소환", spawn));
        say(src, row("경기장 등록 (발밑)", point));
        say(src, row("", btn("경기장 목록", "/rpg boss point list", ChatFormatting.YELLOW), btn("재등장 초기화", "/rpg boss respawn", ChatFormatting.YELLOW),
                btn("◀ 패널", "/rpg 관리", ChatFormatting.WHITE)));
    }

    static void gearMenu(CommandSourceStack src) {
        title(src, "장비 받기 (그 레벨 전 부위)");
        say(src, row("레벨", btn("1", "/rpg gear 1", ChatFormatting.AQUA), btn("10", "/rpg gear 10", ChatFormatting.AQUA), btn("20", "/rpg gear 20", ChatFormatting.AQUA),
                btn("30", "/rpg gear 30", ChatFormatting.AQUA), btn("40", "/rpg gear 40", ChatFormatting.AQUA), btn("◀ 패널", "/rpg 관리", ChatFormatting.WHITE)));
    }

    // ───────── NPC 삭제 ─────────
    static boolean ours(Entity e) {
        return e instanceof Villager && (rpg.core.shop.Shops.isNpc(e) || rpg.core.shop.Storage.isNpc(e) || !rpg.core.craft.Crafts.npcStation(e).isEmpty()
                || rpg.core.job.Jobs.npcFamily(e) > 0 || !rpg.core.quest.QuestEvents.npcOf(e).isEmpty());
    }

    static int removeNpc(ServerPlayer p) {
        List<Entity> near = p.level().getEntities(p, p.getBoundingBox().inflate(5), AdminPanel::ours);
        Entity best = null;
        for (Entity e : near) if (best == null || e.distanceToSqr(p) < best.distanceToSqr(p)) best = e;
        if (best == null) { p.sendSystemMessage(Component.literal("[RPG] 5칸 안에 우리 NPC가 없어요").withStyle(ChatFormatting.RED)); return 0; }
        String name = best.getName().getString();
        String q = rpg.core.quest.QuestEvents.npcOf(best);
        if (!q.isEmpty()) {   // 퀘스트 NPC: 위치 기록도 지움
            var pl = rpg.core.quest.QuestPlaces.get(p.server);
            var list = pl.npcs.get(q);
            String dim = p.level().dimension().location().toString();
            Entity b = best;
            if (list != null) {
                list.removeIf(x -> x.dim().equals(dim) && x.pos().distSqr(b.blockPosition()) <= 9);
                if (list.isEmpty()) pl.npcs.remove(q);
                pl.setDirty();
            }
        }
        best.discard();
        p.sendSystemMessage(Component.literal("[RPG] NPC 삭제: " + name).withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
