package rpg.core.cmd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import rpg.core.RpgCore;
import rpg.core.balance.Balance;
import rpg.core.data.PlayerData;
import rpg.core.data.Progress;
import rpg.core.data.RpgData;
import rpg.core.item.ModItems;

/**
 * /rpg info                — 내 능력치
 * /rpg level <레벨>         — 레벨 지정 (스탯 초기화, 포인트 재지급)   [관리자]
 * /rpg exp <양>            — 경험치 주기                              [관리자]
 * /rpg sp <양>             — 스탯 포인트 주기                          [관리자]
 * /rpg gold <양>           — 골드 주기 (음수면 빼기)                    [관리자]
 * /rpg heal               — HP·MP 회복                              [관리자]
 * /rpg damage <%>         — 최대 HP의 % 만큼 피해 (HUD 테스트)          [관리자]
 * /rpg resetscroll        — 스탯 초기화 주문서 받기                     [관리자]
 * /rpg reset              — 캐릭터 처음으로 (Lv.1)                    [관리자]
 * 사냥터·몹 명령어는 ZoneCommand
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class RpgCommand {
    private RpgCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        var root = Commands.literal("rpg");
        ZoneCommand.addTo(root);
        AdminPanel.addTo(root);
        // 월드 (야생 ↔ RPG 대륙)
        for (String n : new String[]{"이동", "travel"})
            root.then(Commands.literal(n).executes(c -> { rpg.core.world.RpgWorld.command(c.getSource().getPlayerOrException()); return 1; }));
        for (String n : new String[]{"공유창고", "stash"})
            root.then(Commands.literal(n).executes(c -> { rpg.core.world.SharedStash.command(c.getSource().getPlayerOrException()); return 1; }));
        for (String n : new String[]{"스폰설정", "setspawn"})
            root.then(Commands.literal(n).requires(s -> s.hasPermission(2)).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                if (!rpg.core.world.RpgWorld.in(p)) return fail(c, "RPG 대륙 안에서만 설정할 수 있어요");
                rpg.core.world.RpgWorld.setSpawn(p);
                return ok(c, "여기를 RPG 스폰으로 정했어요 (처음 들어올 때·죽었을 때)");
            }));
        for (String n : new String[]{"보기", "markers"})
            root.then(Commands.literal(n).requires(s -> s.hasPermission(2)).executes(c -> {
                boolean on = rpg.core.world.Markers.toggle(c.getSource().getPlayerOrException());
                return ok(c, on ? "지점 보기 켬 (사냥터·보스방·NPC·퀘스트·마을)" : "지점 보기 끔");
            }));
        for (String n : new String[]{"포탈", "gate"})
            root.then(Commands.literal(n).requires(s -> s.hasPermission(2)).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                p.getInventory().add(new net.minecraft.world.item.ItemStack(rpg.core.world.ModBlocks.WORLD_PORTAL_ITEM.get(), 2));
                p.getInventory().add(new net.minecraft.world.item.ItemStack(rpg.core.world.ModBlocks.SHARED_STASH_ITEM.get(), 1));
                return ok(c, "차원 문 2개 (세로로 쌓기) + 공유 창고 1개 지급");
            }));
        // 레이드 입장
        for (String rn : new String[]{"레이드", "raid"}) {
            var raid = Commands.literal(rn);
            var name = com.mojang.brigadier.arguments.StringArgumentType.greedyString();
            com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> names = (c, b) ->
                    net.minecraft.commands.SharedSuggestionProvider.suggest(rpg.core.world.Raids.data(c.getSource().getServer()).raids.keySet(), b);
            for (String n : new String[]{"입구", "gate"})
                raid.then(Commands.literal(n).requires(s -> s.hasPermission(2)).then(Commands.argument("name", name).suggests(names).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!rpg.core.world.RpgWorld.in(p)) return fail(c, "RPG 대륙 안에서만 정할 수 있어요");
                    String nm = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name");
                    return ok(c, rpg.core.world.Raids.setGate(p, nm));
                })));
            for (String n : new String[]{"방", "room"})
                raid.then(Commands.literal(n).requires(s -> s.hasPermission(2)).then(Commands.argument("name", name).suggests(names).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!rpg.core.world.RpgWorld.in(p)) return fail(c, "RPG 대륙 안에서만 정할 수 있어요");
                    String nm = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name");
                    return ok(c, rpg.core.world.Raids.addRoom(p, nm));
                })));
            for (String n : new String[]{"보스", "boss"})
                raid.then(Commands.literal(n).requires(s -> s.hasPermission(2)).then(Commands.argument("boss", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                java.util.stream.Stream.concat(rpg.core.combat.Boss.DEFS.keySet().stream(), java.util.stream.Stream.of("none")), b))
                        .then(Commands.argument("name", name).suggests(names).executes(c -> ok(c, rpg.core.world.Raids.setBoss(c.getSource().getServer(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name"), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "boss")))))));
            for (String n : new String[]{"방초기화", "clearrooms"})
                raid.then(Commands.literal(n).requires(s -> s.hasPermission(2)).then(Commands.argument("name", name).suggests(names).executes(c -> {
                    String nm = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name");
                    var def = rpg.core.world.Raids.def(c.getSource().getServer(), nm, false);
                    if (def == null) return fail(c, "없는 레이드: " + nm);
                    def.rooms.clear(); rpg.core.world.Raids.data(c.getSource().getServer()).setDirty();
                    return ok(c, "레이드 [" + nm + "] 방 위치를 모두 지웠어요");
                })));
            for (String n : new String[]{"레벨", "level"})
                raid.then(Commands.literal(n).requires(s -> s.hasPermission(2)).then(Commands.argument("lv", IntegerArgumentType.integer(1, 999))
                        .then(Commands.argument("name", name).suggests(names).executes(c -> {
                    String nm = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name");
                    int lv = IntegerArgumentType.getInteger(c, "lv");
                    var def = rpg.core.world.Raids.def(c.getSource().getServer(), nm, true);
                    def.minLevel = lv; rpg.core.world.Raids.data(c.getSource().getServer()).setDirty();
                    return ok(c, "레이드 [" + nm + "] 입장 Lv." + lv + " 이상");
                }))));
            for (String n : new String[]{"삭제", "remove"})
                raid.then(Commands.literal(n).requires(s -> s.hasPermission(2)).then(Commands.argument("name", name).suggests(names).executes(c -> {
                    String nm = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name");
                    var data = rpg.core.world.Raids.data(c.getSource().getServer());
                    if (data.raids.remove(nm) == null) return fail(c, "없는 레이드: " + nm);
                    data.setDirty();
                    return ok(c, "레이드 [" + nm + "] 삭제 (입구 블록은 직접 부수세요)");
                })));
            for (String n : new String[]{"목록", "list"})
                raid.then(Commands.literal(n).executes(c -> { c.getSource().sendSuccess(() -> rpg.core.world.Raids.listText(c.getSource()), false); return 1; }));
            for (String n : new String[]{"나가기", "leave"})
                raid.then(Commands.literal(n).executes(c -> { rpg.core.world.Raids.leave(c.getSource().getPlayerOrException()); return 1; }));
            root.then(raid);
        }
        d.register(root
                .then(Commands.literal("info").executes(RpgCommand::info))
                .then(Commands.literal("level").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("lv", IntegerArgumentType.integer(1, 10000)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Progress.setLevel(p, IntegerArgumentType.getInteger(c, "lv"));
                            return ok(c, "레벨 " + RpgData.get(p).level + " (스탯 처음으로, 포인트 재지급)");
                        })))
                .then(Commands.literal("exp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(c -> {
                            Progress.giveExp(c.getSource().getPlayerOrException(), LongArgumentType.getLong(c, "amount"));
                            return ok(c, "경험치 지급");
                        })))
                .then(Commands.literal("sp").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            RpgData.get(p).statPoints += IntegerArgumentType.getInteger(c, "amount");
                            RpgData.sync(p);
                            return ok(c, "스탯 포인트 지급");
                        })))
                .then(Commands.literal("gold").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("amount", LongArgumentType.longArg()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            PlayerData pd = RpgData.get(p);
                            pd.gold = Math.max(0, pd.gold + LongArgumentType.getLong(c, "amount"));
                            RpgData.sync(p);
                            return ok(c, "골드 " + pd.gold);
                        })))
                .then(Commands.literal("heal").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    PlayerData pd = RpgData.get(p);
                    pd.hp = pd.maxHp; pd.mp = pd.maxMp;
                    RpgData.sync(p);
                    return ok(c, "HP·MP 회복");
                }))
                .then(Commands.literal("damage").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100)).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            PlayerData pd = RpgData.get(p);
                            pd.hp = Math.max(1, pd.hp - pd.maxHp * IntegerArgumentType.getInteger(c, "percent") / 100.0);
                            pd.mp = Math.max(0, pd.mp - pd.maxMp * IntegerArgumentType.getInteger(c, "percent") / 100.0);
                            RpgData.sync(p);
                            return ok(c, "HP·MP 감소 (회복 확인용)");
                        })))
                .then(Commands.literal("resetscroll").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getInventory().add(ModItems.STAT_RESET_SCROLL.get().getDefaultInstance());
                    return ok(c, "스탯 초기화 주문서 지급");
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    RpgData.get(p).init();
                    RpgData.sync(p);
                    return ok(c, "캐릭터 초기화 (Lv.1)");
                })));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PlayerData d = RpgData.get(p);
        c.getSource().sendSuccess(() -> Component.literal(String.format(
                "Lv.%d | EXP %,d/%,d | HP %.0f/%.0f MP %.0f/%.0f | 체%d 힘%d 민%d 지%d 운%d 스%d (남은 %d) | 골드 %,d",
                d.level, d.exp, Balance.expNeed(d.level), d.hp, d.maxHp, d.mp, d.maxMp,
                d.stats[0], d.stats[1], d.stats[2], d.stats[3], d.stats[4], d.stats[5], d.statPoints, d.gold))
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal("[RPG] " + msg));
        return 0;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal("[RPG] " + msg).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
