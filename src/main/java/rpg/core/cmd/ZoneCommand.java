package rpg.core.cmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import rpg.core.balance.Balance;
import rpg.core.combat.Combat;
import rpg.core.combat.MobData;
import rpg.core.combat.Mobs;
import rpg.core.item.ModItems;
import rpg.core.zone.Zone;
import rpg.core.zone.ZoneData;
import rpg.core.zone.ZoneWand;

/**
 * 사냥터·몹 명령어 (관리자)
 *  /rpg wand                                  사냥터 지팡이 받기
 *  /rpg zone create <이름> <몹> <레벨> <최대> [리젠초]   지팡이로 찍은 두 점으로 사냥터 만들기
 *  /rpg zone list | remove <이름> | show <이름> | clear <이름>
 *  /rpg spawn <몹> <레벨> [마리]                 바로 앞에 소환 (테스트)
 *  /rpg mobs                                  몹 목록 (mobs.json)
 *  /rpg gear <1|10>                           테스트 장비 한 벌 받기
 *  /rpg npc job <1~4>                         전직 교관 세우기 (1 전사 2 궁수 3 마법사 4 도적)
 *  /rpg npc shop <general|gear|gear2>         상점 NPC 세우기 (shops.json)
 *  /rpg npc craft <smith|jeweler|alchemist>   제작 NPC 세우기 (crafts.json)
 *  /rpg npc quest <npc>                       퀘스트 NPC 세우기 (quests.json)
 *  /rpg quest reset                           내 퀘스트 전부 초기화 (테스트용)
 *  /rpg quest point add|remove <지점>          퀘스트 지점(두고 오기·가져오기) 위치 정하기 / 지우기
 *  /rpg quest check                           NPC·지점 배치 점검 (안 세운 것 빨간색)
 *  /rpg job <0~4>                             직업 바로 바꾸기 (테스트용)
 *  /rpg town add <이름> · remove <이름> · list     마을 등록 (귀환 주문서 도착지)
 *  /rpg npc storage                           창고지기 세우기
 *  /rpg potions                               물약 받기 (빨강·파랑 30개, 나머지 10개씩)
 *  /rpg boss point add <보스> [반경] · remove · list   보스 경기장 (발밑이 중앙), /rpg boss spawn <보스> 테스트 소환, /rpg boss respawn
 *  /rpg scrolls                               강화 테스트 (주문서 전 종류·순백·조각·골드 10만)
 */
public final class ZoneCommand {
    private ZoneCommand() {}

    private static final String[] HELP = {
            "§e── 키 ──",
            "§fK 스탯 · L 스킬 · N 패시브 · J 퀘스트 · P 파티 · M 메뉴(강화 등) · Tab 장비 · Q E R F Z X 스킬 · Alt 구르기",
            "§e── 상호작용 ──",
            "§fNPC 우클릭: 상점·제작·퀘스트·전직·창고 (창고 창 위 탭으로 넘기기, [확장] 두 번 = 창고 추가)",
            "§f다른 플레이어 Shift+우클릭: 거래 요청 (상대도 나를 Shift+우클릭하면 거래창)",
            "§f귀환 주문서 우클릭: 3초 뒤 가장 가까운 마을",
            "§f일일 퀘스트: M 메뉴 → 일일 퀘스트 (매일 오전 6시 초기화)",
            "§e── 명령어 ──",
            "§f/rpg info  내 능력치 · /rpg ach  업적 진행도 · /rpg help  이 도움말"};
    private static final String[] HELP_ADMIN = {
            "§c── 관리자 ──",
            "§f/rpg level|exp|sp|gold|heal|damage|reset|resetscroll  캐릭터 조작",
            "§f/rpg zone ...  사냥터 · /rpg wand  사냥터 지팡이 · /rpg ground <번호>  사냥터 프리셋 · /rpg spawn|spawnelite <몹>  · /rpg mobs",
            "§f/rpg npc job|shop|craft|quest|storage  NPC 세우기",
            "§f/rpg town add|remove|list  마을 (귀환 도착지)",
            "§f/rpg daily reset|complete [플레이어]  일일 퀘스트 새로 뽑기 · 진행도 채우기",
            "§f/rpg boss point add|remove|list · /rpg boss spawn <보스> · /rpg boss respawn",
            "§f/rpg trial point add  시련장 · /rpg quest ...  퀘스트 테스트",
            "§f/rpg gear <레벨> · /rpg potions · /rpg scrolls  테스트 아이템",
            "§f/rpg anim list [글자] · /rpg anim play <모션> · /rpg anim set <스킬> <모션|NONE|reset> · /rpg anim spin <간격틱> <첫타틱> · /rpg anim speed <배수>  EF 모션 테스트"};

    private static int daily(CommandContext<CommandSourceStack> c, ServerPlayer p, boolean fill) {
        if (fill) rpg.core.quest.Dailies.fill(p); else rpg.core.quest.Dailies.reset(p);
        return ok(c, p.getGameProfile().getName() + " 일일 퀘스트 " + (fill ? "진행도 채움 (M → 일일 퀘스트에서 완료)" : "새로 뽑음"));
    }

    private static int animList(CommandContext<CommandSourceStack> c, String filter) {
        var names = rpg.core.compat.EpicFightCompat.animNames(filter);
        if (names.isEmpty()) return ok(c, "모션 없음 (Epic Fight 없음 또는 필터 결과 없음)");
        c.getSource().sendSystemMessage(Component.literal("§e모션 " + names.size() + "개: §f" + String.join(", ", names)));
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> c, boolean admin) {
        for (String l : admin ? HELP_ADMIN : HELP) c.getSource().sendSystemMessage(Component.literal(l));
        if (!admin && c.getSource().hasPermission(2)) c.getSource().sendSystemMessage(Component.literal("§7관리자 명령어: /rpg help admin"));
        return 1;
    }

    private static int bossAdd(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, String key, int radius) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var def = rpg.core.combat.Boss.DEFS.get(key);
        if (def == null) return ok(c, "보스 없음: " + key);
        var p = c.getSource().getPlayerOrException();
        double r = radius > 0 ? radius : def.arena();
        var pt = rpg.core.combat.BossPoints.get(c.getSource().getLevel()).add(key, p.blockPosition(), r);
        return ok(c, "보스 경기장 추가: " + pt.id + " (중앙 = 발밑, 반경 " + (int) r + ") — 곧 등장");
    }

    private static final SuggestionProvider<CommandSourceStack> MOB_KEYS =
            (c, b) -> SharedSuggestionProvider.suggest(Balance.MOBS.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES =
            (c, b) -> SharedSuggestionProvider.suggest(ZoneData.get(c.getSource().getLevel()).zones.keySet(), b);

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("wand").requires(s -> s.hasPermission(2)).executes(c -> {
            c.getSource().getPlayerOrException().getInventory().add(ModItems.ZONE_WAND.get().getDefaultInstance());
            return ok(c, "사냥터 지팡이 지급 (좌클릭·우클릭으로 두 점)");
        }));
        root.then(Commands.literal("gear").requires(s -> s.hasPermission(2))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 40))
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"1", "10", "20", "30", "40"}, b)).executes(c -> {
                    int lv = IntegerArgumentType.getInteger(c, "level");
                    var p = c.getSource().getPlayerOrException();
                    int n = 0;
                    for (var g : ModItems.GEAR) {
                        String id = ((rpg.core.equip.Gear.GearItem) g.get()).gearId();
                        if (!id.endsWith("_" + lv)) continue;
                        int copies = id.startsWith("ring") ? 4 : 1;
                        for (int k = 0; k < copies; k++) {
                            var st = g.get().getDefaultInstance();
                            rpg.core.equip.Gear.roll(st);
                            if (!p.getInventory().add(st)) p.drop(st, false);
                            n++;
                        }
                    }
                    return ok(c, "Lv." + lv + " 테스트 장비 " + n + "개 지급 (추가 옵션 무작위)");
                })));
        // ── 일일 퀘스트 (관리자 테스트) ──
        root.then(Commands.literal("daily").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reset").executes(c -> daily(c, c.getSource().getPlayerOrException(), false))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(c -> daily(c, net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player"), false))))
                .then(Commands.literal("complete").executes(c -> daily(c, c.getSource().getPlayerOrException(), true))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(c -> daily(c, net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player"), true)))));
        // ── EF 모션 테스트 (관리자): 목록 · 재생 · 스킬에 바꿔 끼우기 ──
        root.then(Commands.literal("anim").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> animList(c, ""))
                        .then(Commands.argument("filter", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(c -> animList(c, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "filter")))))
                .then(Commands.literal("play").then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(rpg.core.compat.EpicFightCompat.animNames(""), b))
                        .executes(c -> {
                            String n = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name");
                            boolean ok = rpg.core.compat.EpicFightCompat.playAnim(c.getSource().getPlayerOrException(), n);
                            return ok(c, ok ? "모션 재생: " + n : "없는 모션: " + n);
                        })))
                .then(Commands.literal("info").executes(c -> {
                    var x = rpg.core.compat.EpicFightCompat.class;
                    c.getSource().sendSystemMessage(Component.literal("§e버전 §f" + net.minecraftforge.fml.ModList.get().getModContainerById(rpg.core.RpgCore.MODID)
                            .map(m -> m.getModInfo().getVersion().toString()).orElse("?")
                            + " §e속도(서버) §f" + rpg.core.compat.EpicFightCompat.speedStatus
                            + " §e속도(클라) §f" + rpg.core.compat.EpicFightCompat.clientSpeedStatus
                            + " §e마지막 재생 §f" + rpg.core.compat.EpicFightCompat.LAST.getOrDefault("played", "-")
                            + " §7(시도 " + rpg.core.compat.EpicFightCompat.LAST.getOrDefault("try", "-") + ")"
                            + " §e회전 간격 §f" + rpg.core.skill.WarriorSkills.SPIN_GAP + "틱"));
                    return 1;
                }))
                .then(Commands.literal("speed").then(Commands.argument("mul", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.5f, 5f)).executes(c -> {
                    float mul = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "mul");
                    String r = rpg.core.compat.EpicFightCompat.setSpeed("STEEL_WHIRLWIND", mul) + " · " + rpg.core.compat.EpicFightCompat.setSpeed("GIANT_WHIRLWIND", mul);
                    rpg.core.compat.EpicFightCompat.speedStatus = r;
                    return ok(c, "회전 모션 속도: " + r + " (싱글에서 바로 적용, 마음에 들면 알려주세요)");
                })))
                .then(Commands.literal("spin").then(Commands.argument("gap", IntegerArgumentType.integer(1, 20))
                        .then(Commands.argument("first", IntegerArgumentType.integer(0, 20)).executes(c -> {
                            rpg.core.skill.WarriorSkills.SPIN_GAP = IntegerArgumentType.getInteger(c, "gap");
                            rpg.core.skill.WarriorSkills.SPIN_FIRST = IntegerArgumentType.getInteger(c, "first");
                            return ok(c, "피의 회전 타이밍: 첫 타 " + rpg.core.skill.WarriorSkills.SPIN_FIRST + "틱 뒤, 간격 " + rpg.core.skill.WarriorSkills.SPIN_GAP + "틱 (20틱 = 1초)");
                        }))))
                .then(Commands.literal("set").then(Commands.argument("skill", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"power_strike", "charge_rush", "leap_slam", "blood_spin", "combo_slash_1", "combo_slash_2", "combo_slash_3"}, b))
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(rpg.core.compat.EpicFightCompat.animNames(""), b))
                                .executes(c -> {
                                    String sk = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "skill");
                                    String n = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name");
                                    if (n.equalsIgnoreCase("reset")) rpg.core.compat.EpicFightCompat.OVERRIDE.remove(sk);
                                    else rpg.core.compat.EpicFightCompat.OVERRIDE.put(sk, n);
                                    return ok(c, sk + " 모션 → " + n + " (서버 끄면 초기화, 마음에 들면 알려주세요)");
                                })))));
        // ── 업적 ──
        root.then(Commands.literal("ach").executes(c -> { rpg.core.quest.Achievements.show(c.getSource().getPlayerOrException()); return 1; }));
        // ── 도움말 ──
        root.then(Commands.literal("help").executes(c -> help(c, false))
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2)).executes(c -> help(c, true))));
        // ── 마을 (귀환 주문서 도착지) ──
        root.then(Commands.literal("town").requires(s -> s.hasPermission(2))
                .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    var t = rpg.core.town.Towns.get(c.getSource().getLevel());
                    t.towns.put(n, c.getSource().getPlayerOrException().blockPosition());
                    t.setDirty();
                    return ok(c, "마을 등록: " + n + " (지금 위치 = 귀환 주문서 도착 지점)");
                })))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(rpg.core.town.Towns.get(c.getSource().getLevel()).towns.keySet(), b)).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    var t = rpg.core.town.Towns.get(c.getSource().getLevel());
                    boolean had = t.towns.remove(n) != null;
                    t.setDirty();
                    return ok(c, had ? "마을 삭제: " + n : "없는 마을: " + n);
                })))
                .then(Commands.literal("list").executes(c -> {
                    var t = rpg.core.town.Towns.get(c.getSource().getLevel());
                    if (t.towns.isEmpty()) return ok(c, "등록된 마을 없음");
                    StringBuilder sb = new StringBuilder("마을: ");
                    t.towns.forEach((k, v) -> sb.append(k).append(" (").append(v.toShortString()).append(")  "));
                    return ok(c, sb.toString());
                })));
        root.then(Commands.literal("npc").requires(s -> s.hasPermission(2))
                .then(Commands.literal("storage").executes(c -> {
                    rpg.core.shop.Storage.spawn(c.getSource().getPlayerOrException());
                    return ok(c, "창고지기를 세웠어요");
                }))
                .then(Commands.literal("job").then(Commands.argument("family", IntegerArgumentType.integer(1, 4)).executes(c -> {
                    int f = IntegerArgumentType.getInteger(c, "family");
                    rpg.core.job.JobEvents.spawn(c.getSource().getPlayerOrException(), f);
                    return ok(c, rpg.core.job.Jobs.name(f) + " 교관을 세웠어요 (1 전사 · 2 궁수 · 3 마법사 · 4 도적)");
                })))
                .then(Commands.literal("shop").then(Commands.argument("shop", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(rpg.core.shop.Shops.SHOPS.keySet(), b)).executes(c -> {
                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "shop");
                    if (!rpg.core.shop.Shops.SHOPS.containsKey(id)) {
                        c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("없는 상점: " + id + " (shops.json)"));
                        return 0;
                    }
                    rpg.core.shop.Shops.spawn(c.getSource().getPlayerOrException(), id);
                    return ok(c, rpg.core.shop.Shops.SHOPS.get(id).name() + " 을(를) 세웠어요");
                })))
                .then(Commands.literal("craft").then(Commands.argument("station", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(rpg.core.craft.Crafts.STATIONS.keySet(), b)).executes(c -> {
                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "station");
                    if (!rpg.core.craft.Crafts.STATIONS.containsKey(id)) {
                        c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("없는 제작 NPC: " + id + " (crafts.json)"));
                        return 0;
                    }
                    rpg.core.craft.Crafts.spawn(c.getSource().getPlayerOrException(), id);
                    return ok(c, rpg.core.craft.Crafts.STATIONS.get(id).name() + " 을(를) 세웠어요");
                })))
                .then(Commands.literal("quest").then(Commands.argument("npc", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(rpg.core.quest.Quests.NPCS.keySet(), b)).executes(c -> {
                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "npc");
                    if (!rpg.core.quest.Quests.NPCS.containsKey(id)) {
                        c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("없는 퀘스트 NPC: " + id + " (quests.json)"));
                        return 0;
                    }
                    rpg.core.quest.QuestEvents.spawn(c.getSource().getPlayerOrException(), id);
                    return ok(c, rpg.core.quest.Quests.npcName(id) + " 을(를) 세웠어요");
                }))));
        root.then(Commands.literal("quest").requires(s -> s.hasPermission(2))
                .then(Commands.literal("check").executes(c -> {
                    for (var line : rpg.core.quest.QuestEvents.check(c.getSource().getServer())) c.getSource().sendSystemMessage(line);
                    return 1;
                }))
                .then(Commands.literal("point")
                        .then(Commands.literal("add").then(Commands.argument("point", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(rpg.core.quest.Quests.POINTS.keySet(), b)).executes(c -> {
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "point");
                            if (!rpg.core.quest.Quests.POINTS.containsKey(id)) {
                                c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("없는 지점: " + id + " (quests.json points)"));
                                return 0;
                            }
                            rpg.core.quest.QuestEvents.setPoint(c.getSource().getPlayerOrException(), id);
                            return ok(c, rpg.core.quest.Quests.pointName(id) + " 지점을 여기로 정했어요");
                        })))
                        .then(Commands.literal("remove").then(Commands.argument("point", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(rpg.core.quest.Quests.POINTS.keySet(), b)).executes(c -> {
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "point");
                            boolean had = rpg.core.quest.QuestEvents.removePoint(c.getSource().getServer(), id);
                            return ok(c, had ? rpg.core.quest.Quests.pointName(id) + " 지점을 지웠어요" : "정해진 지점이 없어요: " + id);
                        }))))
                .then(Commands.literal("reset").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    var d = rpg.core.data.RpgData.get(p);
                    d.questActive.clear(); d.questDone.clear();
                    rpg.core.net.Net.questSync(p);
                    return ok(c, "퀘스트를 전부 초기화했어요");
                })));
        root.then(Commands.literal("job").requires(s -> s.hasPermission(2))
                .then(Commands.argument("job", IntegerArgumentType.integer(0, 4)).executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    var d = rpg.core.data.RpgData.get(p);
                    d.job = IntegerArgumentType.getInteger(c, "job");
                    d.branch = 0; d.pendingBranch = 0; d.trialCleared = false;
                    d.recalc();
                    rpg.core.data.RpgData.sync(p);
                    return ok(c, "직업을 " + rpg.core.job.Jobs.name(d.job) + "(으)로 바꿨어요 (테스트용, 보상 없음)");
                })));
        root.then(Commands.literal("branch").requires(s -> s.hasPermission(2))
                .then(Commands.argument("branch", IntegerArgumentType.integer(0, 2)).executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    var d = rpg.core.data.RpgData.get(p);
                    if (d.job == 0) return fail(c, "먼저 1차 직업이 있어야 해요 (/rpg job)");
                    d.branch = IntegerArgumentType.getInteger(c, "branch");
                    d.pendingBranch = 0; d.trialCleared = false;
                    d.recalc();
                    rpg.core.data.RpgData.sync(p);
                    return ok(c, "2차 갈래를 " + (d.branch == 0 ? "없음" : rpg.core.job.Jobs.branchName(d.job, d.branch)) + "(으)로 바꿨어요 (테스트용, 보상 없음)");
                })));
        root.then(Commands.literal("trial").requires(s -> s.hasPermission(2))
                .then(Commands.literal("point")
                        .then(Commands.literal("add").executes(c -> {
                            rpg.core.job.Trials.addPoint(c.getSource().getPlayerOrException());
                            return ok(c, "교관의 시련 시련장을 여기로 추가했어요 (시련의 허수아비가 이 자리에 나와요)");
                        }))
                        .then(Commands.literal("remove").executes(c -> {
                            boolean had = rpg.core.job.Trials.removePoint(c.getSource().getPlayerOrException());
                            return had ? ok(c, "가까운 시련장을 지웠어요") : fail(c, "8칸 안에 시련장이 없어요");
                        }))
                        .then(Commands.literal("list").executes(c -> {
                            var lines = rpg.core.job.Trials.points(c.getSource().getServer());
                            if (lines.isEmpty()) return fail(c, "시련장이 없어요 (/rpg trial point add)");
                            for (String l : lines) c.getSource().sendSystemMessage(Component.literal(l));
                            return 1;
                        })))
                .then(Commands.literal("clear").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    var d = rpg.core.data.RpgData.get(p);
                    d.trialCleared = true;
                    rpg.core.data.RpgData.sync(p);
                    return ok(c, "시련을 통과한 걸로 했어요 (테스트용)");
                })));
        root.then(Commands.literal("potions").requires(s -> s.hasPermission(2)).executes(c -> {
            var p = c.getSource().getPlayerOrException();
            p.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.RED_POTION.get(), 30));
            p.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.BLUE_POTION.get(), 30));
            for (var po : new net.minecraftforge.registries.RegistryObject[]{ModItems.ORANGE_POTION, ModItems.WHITE_POTION, ModItems.DEEP_BLUE_POTION,
                    ModItems.MANA_ELIXIR, ModItems.ELIXIR, ModItems.POWER_ELIXIR})
                p.getInventory().add(new net.minecraft.world.item.ItemStack((net.minecraft.world.item.Item) po.get(), 10));
            return ok(c, "물약 지급");
        }));
        root.then(Commands.literal("scrolls").requires(s -> s.hasPermission(2)).executes(c -> {
            var p = c.getSource().getPlayerOrException();
            for (String k : rpg.core.item.Scroll.KINDS) for (int g = 0; g < 4; g++) {
                var s = rpg.core.item.Scroll.make(k, g);
                s.setCount(16);
                if (!p.getInventory().add(s)) p.drop(s, false);
            }
            var w = rpg.core.item.Scroll.make("white", 0);
            w.setCount(16);
            if (!p.getInventory().add(w)) p.drop(w, false);
            var f = new net.minecraft.world.item.ItemStack(ModItems.SCROLL_FRAGMENT.get(), 64);
            if (!p.getInventory().add(f)) p.drop(f, false);
            var d = rpg.core.data.RpgData.get(p);
            d.gold += 100000;
            rpg.core.data.RpgData.sync(p);
            return ok(c, "주문서 전 종류 16장씩 · 순백 16장 · 주문서 조각 64개 · 골드 +100,000");
        }));
        // ── 보스 ──
        SuggestionProvider<CommandSourceStack> BOSS_KEYS = (c, b) -> SharedSuggestionProvider.suggest(rpg.core.combat.Boss.DEFS.keySet(), b);
        root.then(Commands.literal("boss").requires(s -> s.hasPermission(2))
                .then(Commands.literal("point")
                        .then(Commands.literal("add").then(Commands.argument("boss", StringArgumentType.word()).suggests(BOSS_KEYS)
                                .executes(c -> bossAdd(c, StringArgumentType.getString(c, "boss"), -1))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(8, 64))
                                        .executes(c -> bossAdd(c, StringArgumentType.getString(c, "boss"), IntegerArgumentType.getInteger(c, "radius"))))))
                        .then(Commands.literal("remove").executes(c -> {
                            var l = c.getSource().getLevel();
                            var d = rpg.core.combat.BossPoints.get(l);
                            var here = c.getSource().getPlayerOrException().blockPosition();
                            rpg.core.combat.BossPoints.Point best = null;
                            double bd = 16 * 16;
                            for (var pt : d.points) { double dd = pt.pos.distSqr(here); if (dd < bd) { bd = dd; best = pt; } }
                            if (best == null) return ok(c, "16칸 안에 보스 경기장이 없어요");
                            if (best.boss != null) { var ent = l.getEntity(best.boss); if (ent != null) ent.discard(); }
                            d.points.remove(best);
                            d.setDirty();
                            return ok(c, "보스 경기장 삭제: " + best.id);
                        }))
                        .then(Commands.literal("list").executes(c -> {
                            var d = rpg.core.combat.BossPoints.get(c.getSource().getLevel());
                            if (d.points.isEmpty()) return ok(c, "보스 경기장 없음");
                            long now = c.getSource().getLevel().getGameTime();
                            StringBuilder sb = new StringBuilder("보스 경기장: ");
                            for (var pt : d.points) sb.append(pt.id).append(" (").append(pt.pos.toShortString()).append(", 반경 ").append((int) pt.radius)
                                    .append(pt.boss != null ? ", 등장 중" : pt.nextSpawn > now ? ", " + (pt.nextSpawn - now) / 20 + "초 뒤" : ", 곧 등장").append(")  ");
                            return ok(c, sb.toString());
                        })))
                .then(Commands.literal("spawn").then(Commands.argument("boss", StringArgumentType.word()).suggests(BOSS_KEYS).executes(c -> {
                    String key = StringArgumentType.getString(c, "boss");
                    var def = rpg.core.combat.Boss.DEFS.get(key);
                    if (def == null) return ok(c, "보스 없음: " + key);
                    var p = c.getSource().getPlayerOrException();
                    var mob = rpg.core.combat.BossPoints.spawnAt(c.getSource().getLevel(), key, p.blockPosition(), def.arena(), null);
                    return ok(c, mob == null ? "소환 실패" : "테스트 소환: " + key + " (경기장 = 지금 위치, 반경 " + (int) def.arena() + ", 재등장 없음)");
                })))
                .then(Commands.literal("respawn").executes(c -> {
                    var d = rpg.core.combat.BossPoints.get(c.getSource().getLevel());
                    for (var pt : d.points) pt.nextSpawn = 0;
                    d.setDirty();
                    return ok(c, "모든 보스 경기장 재등장 대기 시간 초기화");
                })));
        root.then(Commands.literal("mobs").requires(s -> s.hasPermission(2)).executes(c -> {
            StringBuilder sb = new StringBuilder("몹 목록: ");
            Balance.MOBS.values().forEach(d -> sb.append(d.key()).append("(").append(d.name()).append(") "));
            return ok(c, sb.toString());
        }));
        root.then(Commands.literal("spawn").requires(s -> s.hasPermission(2))
                .then(Commands.argument("mob", StringArgumentType.word()).suggests(MOB_KEYS)
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                                .executes(c -> spawn(c, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 30))
                                        .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "count")))))));
        root.then(Commands.literal("spawnelite").requires(s -> s.hasPermission(2))
                .then(Commands.argument("mob", StringArgumentType.word()).suggests(MOB_KEYS)
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String key = StringArgumentType.getString(c, "mob");
                            if (!Balance.MOBS.containsKey(key)) return fail(c, "mobs.json 에 없는 몹: " + key);
                            BlockPos at = p.blockPosition().relative(p.getDirection(), 3);
                            Mobs.spawn(p.serverLevel(), key, IntegerArgumentType.getInteger(c, "level"), at, "", true);
                            return ok(c, "엘리트 " + key + " 소환");
                        }))));

        root.then(Commands.literal("zone").requires(s -> s.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("mob", StringArgumentType.word()).suggests(MOB_KEYS)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                                                .then(Commands.argument("max", IntegerArgumentType.integer(1, 100))
                                                        .executes(c -> create(c, 5))
                                                        .then(Commands.argument("interval", IntegerArgumentType.integer(1, 600))
                                                                .executes(c -> create(c, IntegerArgumentType.getInteger(c, "interval")))))))))
                .then(Commands.literal("list").executes(ZoneCommand::list))
                .then(Commands.literal("ground").then(Commands.argument("no", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Balance.GROUNDS.keySet(), b))
                        .executes(c -> ground(c, null))
                        .then(Commands.argument("name", StringArgumentType.word()).executes(c -> ground(c, StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("remove").executes(c -> {
                            Zone z = here(c);
                            if (z == null) return fail(c, "사냥터 안에 서서 쓰거나 이름을 붙여 주세요: /rpg zone remove <이름>");
                            ZoneData d = ZoneData.get(c.getSource().getLevel());
                            d.zones.remove(z.name);
                            d.setDirty();
                            clearMobs(c.getSource().getLevel(), z.name);
                            return ok(c, "사냥터 삭제: " + z.name);
                        }).then(Commands.argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
                        .executes(c -> {
                            ServerLevel lv = c.getSource().getLevel();
                            String n = StringArgumentType.getString(c, "name");
                            ZoneData d = ZoneData.get(lv);
                            if (d.zones.remove(n) == null) return fail(c, "없는 사냥터: " + n);
                            d.setDirty();
                            clearMobs(lv, n);
                            return ok(c, "사냥터 삭제: " + n);
                        })))
                .then(Commands.literal("clear").executes(c -> {
                            Zone z = here(c);
                            if (z == null) {   // 사냥터 밖: 모든 사냥터 몹 정리
                                int n = 0;
                                for (String name : ZoneData.get(c.getSource().getLevel()).zones.keySet()) n += clearMobs(c.getSource().getLevel(), name);
                                return ok(c, "모든 사냥터 몹 " + n + "마리 정리 (다시 리젠됨)");
                            }
                            return ok(c, z.name + " 몹 " + clearMobs(c.getSource().getLevel(), z.name) + "마리 정리 (다시 리젠됨)");
                        }).then(Commands.argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
                        .executes(c -> {
                            int n = clearMobs(c.getSource().getLevel(), StringArgumentType.getString(c, "name"));
                            return ok(c, "몹 " + n + "마리 정리 (다시 리젠됨)");
                        })))
                .then(Commands.literal("show").executes(c -> {
                            ServerLevel lv = c.getSource().getLevel();
                            var zs = ZoneData.get(lv).zones.values();
                            if (zs.isEmpty()) return fail(c, "이 월드에 사냥터 없음");
                            for (Zone z : zs) outline(lv, z);
                            return ok(c, "사냥터 " + zs.size() + "개 테두리 표시");
                        }).then(Commands.argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
                        .executes(c -> {
                            ServerLevel lv = c.getSource().getLevel();
                            Zone z = ZoneData.get(lv).zones.get(StringArgumentType.getString(c, "name"));
                            if (z == null) return fail(c, "없는 사냥터");
                            outline(lv, z);
                            return ok(c, "사냥터 테두리 표시");
                        }))));
    }

    /** 사냥터 프리셋 (mobs.json _grounds): 지팡이로 찍은 구역에 그 번호의 몹·레벨 범위로 */
    private static int ground(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos[] ps = ZoneWand.get(p);
        if (ps[0] == null || ps[1] == null) return fail(c, "먼저 사냥터 지팡이로 두 점을 찍어 주세요 (/rpg wand)");
        String no = StringArgumentType.getString(c, "no");
        Balance.Ground g = Balance.GROUNDS.get(no);
        if (g == null) return fail(c, "없는 사냥터 번호: " + no + " (" + String.join(", ", Balance.GROUNDS.keySet()) + ")");
        String zn = name != null ? name : "ground" + no;
        Zone z = new Zone(zn, String.join("+", g.mobs()), g.min(), 8, 5, ps[0], ps[1]);
        z.levelMax = g.max();
        ZoneData d = ZoneData.get(c.getSource().getLevel());
        d.zones.put(zn, z);
        d.setDirty();
        outline(c.getSource().getLevel(), z);
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String k : g.mobs()) names.add(Balance.MOBS.containsKey(k) ? Balance.MOBS.get(k).name() : k);
        return ok(c, String.format("사냥터 %s '%s' 만듦 (%s): %s, Lv.%d~%d, 최대 8마리", no, zn, g.name(), String.join("·", names), g.min(), g.max()));
    }

    /** 플레이어가 서 있는 사냥터 */
    private static Zone here(CommandContext<CommandSourceStack> c) {
        var pos = c.getSource().getPosition();
        for (Zone z : ZoneData.get(c.getSource().getLevel()).zones.values()) if (z.box().inflate(0.5).contains(pos)) return z;
        return null;
    }

    private static int create(CommandContext<CommandSourceStack> c, int interval) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos[] ps = ZoneWand.get(p);
        if (ps[0] == null || ps[1] == null) return fail(c, "먼저 사냥터 지팡이로 두 점을 찍어 주세요 (/rpg wand)");
        String mob = StringArgumentType.getString(c, "mob");
        for (String k : mob.split("\\+")) if (!Balance.MOBS.containsKey(k)) return fail(c, "mobs.json 에 없는 몹: " + k);
        String name = StringArgumentType.getString(c, "name");
        Zone z = new Zone(name, mob, IntegerArgumentType.getInteger(c, "level"), IntegerArgumentType.getInteger(c, "max"),
                interval, ps[0], ps[1]);
        ZoneData d = ZoneData.get(c.getSource().getLevel());
        d.zones.put(name, z);
        d.setDirty();
        outline(c.getSource().getLevel(), z);
        return ok(c, String.format("사냥터 '%s' 만듦: %s Lv.%d 최대 %d마리, %d초마다 리젠", name, mob, z.level, z.max, z.interval));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        var zones = ZoneData.get(c.getSource().getLevel()).zones.values();
        if (zones.isEmpty()) return ok(c, "이 월드에 사냥터 없음");
        for (Zone z : zones) {
            c.getSource().sendSuccess(() -> Component.literal(String.format(" · %s: %s Lv.%s ×%d (%d초) [%d,%d,%d ~ %d,%d,%d]",
                    z.name, z.mob, z.levelMax > z.level ? z.level + "~" + z.levelMax : String.valueOf(z.level), z.max, z.interval, z.min.getX(), z.min.getY(), z.min.getZ(),
                    z.max2.getX(), z.max2.getY(), z.max2.getZ())).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String key = StringArgumentType.getString(c, "mob");
        int lv = IntegerArgumentType.getInteger(c, "level");
        if (!Balance.MOBS.containsKey(key)) return fail(c, "mobs.json 에 없는 몹: " + key);
        BlockPos base = p.blockPosition().relative(p.getDirection(), 3);
        int made = 0;
        for (int i = 0; i < count; i++) {
            if (Mobs.spawn(p.serverLevel(), key, lv, base.offset(i % 3 - 1, 0, i / 3), "") != null) made++;
        }
        return ok(c, key + " Lv." + lv + " " + made + "마리 소환");
    }

    private static int clearMobs(ServerLevel lv, String zone) {
        int n = 0;
        for (var e : lv.getAllEntities()) {
            if (e instanceof Mob m) {
                MobData d = Combat.mob(m);
                if (d != null && zone.equals(d.zone)) { m.discard(); n++; }
            }
        }
        return n;
    }

    private static void outline(ServerLevel lv, Zone z) {
        double x0 = z.min.getX(), y0 = z.min.getY() + 1, z0 = z.min.getZ();
        double x1 = z.max2.getX() + 1, z1 = z.max2.getZ() + 1;
        for (double x = x0; x <= x1; x += 1) {
            lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y0, z0, 1, 0, 0, 0, 0);
            lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y0, z1, 1, 0, 0, 0, 0);
        }
        for (double zz = z0; zz <= z1; zz += 1) {
            lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, x0, y0, zz, 1, 0, 0, 0, 0);
            lv.sendParticles(ParticleTypes.HAPPY_VILLAGER, x1, y0, zz, 1, 0, 0, 0, 0);
        }
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal("[RPG] " + msg).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal("[RPG] " + msg));
        return 0;
    }
}
