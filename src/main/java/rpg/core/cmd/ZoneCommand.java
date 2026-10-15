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
 *  /rpg potions                               물약 받기
 */
public final class ZoneCommand {
    private ZoneCommand() {}

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
        root.then(Commands.literal("npc").requires(s -> s.hasPermission(2))
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
            return ok(c, "물약 지급");
        }));
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
