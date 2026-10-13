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
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class RpgCommand {
    private RpgCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("rpg")
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
                "Lv.%d | EXP %,d/%,d | HP %.0f/%.0f MP %.0f/%.0f | 체%d 힘%d 민%d 지%d 운%d (남은 %d) | 골드 %,d",
                d.level, d.exp, Balance.expNeed(d.level), d.hp, d.maxHp, d.mp, d.maxMp,
                d.stats[0], d.stats[1], d.stats[2], d.stats[3], d.stats[4], d.statPoints, d.gold))
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal("[RPG] " + msg).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
