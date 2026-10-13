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
 *  /rpg weapons                               테스트 무기 받기
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
        root.then(Commands.literal("weapons").requires(s -> s.hasPermission(2)).executes(c -> {
            var inv = c.getSource().getPlayerOrException().getInventory();
            inv.add(ModItems.TEST_SWORD_10.get().getDefaultInstance());
            inv.add(ModItems.TEST_SWORD_30.get().getDefaultInstance());
            inv.add(ModItems.TEST_SWORD_100.get().getDefaultInstance());
            return ok(c, "테스트 무기 지급 (공 10 / 30 / 100)");
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
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
                        .executes(c -> {
                            ServerLevel lv = c.getSource().getLevel();
                            String n = StringArgumentType.getString(c, "name");
                            ZoneData d = ZoneData.get(lv);
                            if (d.zones.remove(n) == null) return fail(c, "없는 사냥터: " + n);
                            d.setDirty();
                            clearMobs(lv, n);
                            return ok(c, "사냥터 삭제: " + n);
                        })))
                .then(Commands.literal("clear").then(Commands.argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
                        .executes(c -> {
                            int n = clearMobs(c.getSource().getLevel(), StringArgumentType.getString(c, "name"));
                            return ok(c, "몹 " + n + "마리 정리 (다시 리젠됨)");
                        })))
                .then(Commands.literal("show").then(Commands.argument("name", StringArgumentType.word()).suggests(ZONE_NAMES)
                        .executes(c -> {
                            ServerLevel lv = c.getSource().getLevel();
                            Zone z = ZoneData.get(lv).zones.get(StringArgumentType.getString(c, "name"));
                            if (z == null) return fail(c, "없는 사냥터");
                            outline(lv, z);
                            return ok(c, "사냥터 테두리 표시");
                        }))));
    }

    private static int create(CommandContext<CommandSourceStack> c, int interval) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        BlockPos[] ps = ZoneWand.get(p);
        if (ps[0] == null || ps[1] == null) return fail(c, "먼저 사냥터 지팡이로 두 점을 찍어 주세요 (/rpg wand)");
        String mob = StringArgumentType.getString(c, "mob");
        if (!Balance.MOBS.containsKey(mob)) return fail(c, "mobs.json 에 없는 몹: " + mob);
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
            c.getSource().sendSuccess(() -> Component.literal(String.format(" · %s: %s Lv.%d ×%d (%d초) [%d,%d,%d ~ %d,%d,%d]",
                    z.name, z.mob, z.level, z.max, z.interval, z.min.getX(), z.min.getY(), z.min.getZ(),
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
