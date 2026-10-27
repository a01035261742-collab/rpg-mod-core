package rpg.core.cmd;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * 건축 도우미 (OP, WorldEdit 필요): /rpg 건축 → 채팅 버튼.
 * 건물 = config/worldedit/schematics 안 .schem 전부 (새 파일 넣으면 자동으로 버튼). 회전 골라 누르면 불러오기 + 회전 + 붙이기(공기 빼고) 한 번에.
 * 지형 = 자주 쓰는 WorldEdit 명령 버튼 (범위 · 채우기 · 브러시 · 물).
 */
public final class BuildPanel {
    private BuildPanel() {}

    /** 알려진 건물 이름 (나머지는 파일 이름 그대로) */
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("start_harold_manor", "해럴드 저택"), Map.entry("start_harold_house", "해럴드 집 (옛)"),
            Map.entry("start_general_store", "잡화점"), Map.entry("start_gear_shop", "장비점"), Map.entry("start_herb_shop", "약초 가게"),
            Map.entry("start_plaza_fountain", "광장 분수"), Map.entry("start_trainer_warrior", "전사 교관"), Map.entry("start_trainer_archer", "궁수 교관"),
            Map.entry("start_trainer_mage", "마법사 교관"), Map.entry("start_trainer_thief", "도적 교관"),
            Map.entry("farm_bram_farmhouse", "브람 농가"), Map.entry("farm_barn", "헛간"), Map.entry("farm_farm_field", "밭"),
            Map.entry("village2_lab", "모리스 버섯 연구소"), Map.entry("village2_alchemy", "네라 연금술 탑"), Map.entry("village2_shop_red", "2마을 상점 (빨강 갓)"),
            Map.entry("village2_shop_brown", "2마을 상점 (갈색 갓)"), Map.entry("village2_gate", "카엘 경비 초소 입구"));

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root) {
        for (String n : new String[]{"건축", "build"}) {
            root.then(Commands.literal(n).requires(s -> s.hasPermission(2))
                    .executes(c -> { show(c.getSource()); return 1; })
                    .then(Commands.literal("붙이기").then(Commands.argument("deg", IntegerArgumentType.integer(0, 270))
                            .then(Commands.argument("file", StringArgumentType.greedyString()).executes(c -> paste(c.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(c, "file"), IntegerArgumentType.getInteger(c, "deg"))))))
                    .then(Commands.literal("원").executes(c -> circle(c.getSource().getPlayerOrException(), 24))
                            .then(Commands.argument("r", IntegerArgumentType.integer(2, 64)).executes(c -> circle(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "r")))))
                    .then(Commands.literal("범위").executes(c -> zoneSize(c.getSource().getPlayerOrException()))));
        }
    }

    private static Path dir() { return FMLPaths.GAMEDIR.get().resolve("config").resolve("worldedit").resolve("schematics"); }

    private static List<String> files() {
        List<String> out = new ArrayList<>();
        try (var s = Files.list(dir())) {
            s.filter(p -> { String f = p.getFileName().toString(); return f.endsWith(".schem") || f.endsWith(".schematic"); })
                    .forEach(p -> { String f = p.getFileName().toString(); out.add(f.substring(0, f.lastIndexOf('.'))); });
        } catch (Exception ignored) {}
        out.sort(null);
        return out;
    }

    // ───────── 버튼 ─────────
    private static MutableComponent run(String label, String cmd, ChatFormatting col, String hover) {
        return Component.literal("[" + label + "]").withStyle(s -> s.withColor(col)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover + "\n" + cmd).withStyle(ChatFormatting.GRAY))));
    }

    /** 채팅 칸에 넣어주기만 (블록·숫자 바꿔서 엔터) */
    private static MutableComponent type(String label, String cmd, String hover) {
        return Component.literal("[" + label + "✎]").withStyle(s -> s.withColor(ChatFormatting.WHITE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, cmd))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover + "\n(채팅 칸에 넣어줌 → 고쳐서 엔터)\n" + cmd).withStyle(ChatFormatting.GRAY))));
    }

    private static MutableComponent row(String head, MutableComponent... bs) {
        MutableComponent r = Component.literal(head + "  ").withStyle(ChatFormatting.GRAY);
        for (MutableComponent b : bs) r.append(b).append(" ");
        return r;
    }

    private static void say(CommandSourceStack src, Component c) { src.sendSystemMessage(c); }

    static void show(CommandSourceStack src) {
        ChatFormatting a = ChatFormatting.AQUA, g = ChatFormatting.GREEN, y = ChatFormatting.YELLOW, r = ChatFormatting.RED, b = ChatFormatting.GOLD;
        say(src, Component.literal("━━━━━ 건축 도우미 (WorldEdit) ━━━━━").withStyle(ChatFormatting.GOLD));
        if (!ModList.get().isLoaded("worldedit")) {
            say(src, Component.literal("WorldEdit 이 없어요. 런처에서 WorldEdit (선택) 을 체크해서 깔아 주세요").withStyle(ChatFormatting.RED));
            return;
        }
        // 건물
        List<String> fs = files();
        if (fs.isEmpty()) say(src, Component.literal("건물 파일이 없어요: " + dir() + " 에 .schem 을 넣으세요").withStyle(ChatFormatting.GRAY));
        else {
            say(src, Component.literal("건물 — 놓을 자리에 서서 방향 누르기 (공기 빼고 붙음 · 잘못되면 [되돌리기])").withStyle(ChatFormatting.YELLOW));
            for (String f : fs) {
                String nm = NAMES.getOrDefault(f, f);
                String base = "/rpg 건축 붙이기 ";
                say(src, row("  " + nm, run("0°", base + "0 " + f, a, nm + " 그대로"), run("90°", base + "90 " + f, a, nm + " 90° 돌려서"),
                        run("180°", base + "180 " + f, a, nm + " 180° 돌려서"), run("270°", base + "270 " + f, a, nm + " 270° 돌려서")));
            }
        }
        say(src, row("기본", run("범위 도끼", "//wand", y, "나무 도끼: 왼클릭 1번 점 · 우클릭 2번 점"),
                run("되돌리기", "//undo", r, "방금 한 것 취소"), run("다시 하기", "//redo", g, "취소한 것 다시"),
                run("범위 풀기", "//sel", ChatFormatting.GRAY, "잡은 범위 지우기"), run("범위 크기", "//size", ChatFormatting.GRAY, "잡은 범위 크기 보기")));
        // 범위 (도끼로 두 점 잡은 뒤)
        say(src, Component.literal("지형 — 범위 (도끼로 두 점 잡은 뒤)").withStyle(ChatFormatting.YELLOW));
        say(src, row("  채우기", run("잔디", "//set grass_block", g, "범위를 잔디 블록으로"), run("흙", "//set dirt", b, "범위를 흙으로"),
                run("돌", "//set stone", ChatFormatting.GRAY, "범위를 돌로"), run("지우기", "//set air", r, "범위를 비움 (공기)"), type("다른 블록", "//set ", "블록 이름 써서 채우기")));
        say(src, row("  겉면", run("위에 잔디 덮기", "//overlay grass_block", g, "범위 맨 위 블록 위에 잔디 한 겹"),
                run("흙→잔디", "//replace dirt grass_block", g, "범위 안 흙을 잔디로"), run("길 만들기", "//replace grass_block dirt_path", b, "범위 안 잔디를 흙길로"),
                type("바꾸기", "//replace 원래블록 새블록", "범위 안 블록 바꾸기")));
        say(src, row("  모양", run("부드럽게", "//smooth 3", a, "범위 지형을 3번 다듬기 (언덕 모서리)"), run("벽", "//walls stone_bricks", ChatFormatting.GRAY, "범위 옆면 돌벽돌 벽"),
                run("위로 10칸 늘리기", "//expand 10 up", y, "범위를 위로 10칸 넓힘"), run("아래로 10칸 늘리기", "//expand 10 down", y, "범위를 아래로 10칸 넓힘")));
        say(src, row("  옮기기", run("복사", "//copy", a, "범위 복사 (내가 선 자리 기준)"), run("붙이기", "//paste -a", a, "복사한 것 여기에 (공기 빼고)"),
                run("90° 돌리기", "//rotate 90", a, "복사한 것 돌리기 (붙이기 전에)"), type("범위 옮기기", "//move 5 up", "범위를 통째로 옮기기 (숫자·방향 고치기)")));
        // 브러시 (손에 든 아이템에 붙음)
        say(src, Component.literal("지형 — 브러시 (손에 든 아이템으로 멀리 우클릭 · 아무 블록 아닌 도구 들고)").withStyle(ChatFormatting.YELLOW));
        say(src, row("  ", run("언덕 쌓기", "//brush sphere grass_block 4", g, "반지름 4 잔디 공"), run("파내기", "//brush sphere air 4", r, "반지름 4 비우기"),
                run("부드럽게", "//brush smooth 4", a, "반지름 4 다듬기 (산·언덕 마무리)"), run("평평하게", "//brush cylinder air 4 6", y, "반지름 4 · 높이 6 비우기 (위만 깎을 때 발 높이 맞춰서)"),
                type("크기 바꾸기", "//brush sphere grass_block 6", "숫자 = 반지름"), run("브러시 끄기", "/none", ChatFormatting.GRAY, "손에 든 아이템 브러시 풀기")));
        say(src, row("  길", run("흙길 (폭 1)", "//line dirt_path 0", b, "도끼로 찍은 두 점 사이 흙길 (두 점은 땅 블록을 찍기)"),
                run("흙길 (폭 3)", "//line dirt_path 1", b, "두 점 사이 폭 3 흙길"), run("돌길 (폭 3)", "//line cobblestone 1", ChatFormatting.GRAY, "두 점 사이 폭 3 조약돌 길"),
                type("다른 길", "//line gravel 1", "블록·굵기(0=1칸,1=3칸,2=5칸) 바꾸기")));
        // 미리보기
        say(src, row("미리보기", run("경기장 원 24", "/rpg 건축 원 24", ChatFormatting.LIGHT_PURPLE, "발밑에 반지름 24 원 (보스·레이드 경기장 크기) 20초"),
                type("원 크기", "/rpg 건축 원 16", "반지름 바꿔서"), run("사냥터 범위", "/rpg 건축 범위", g, "사냥터 지팡이(/rpg wand)로 찍은 범위 크기·몹 밀도 + 테두리 20초"),
                run("사냥터 지팡이", "/rpg wand", g, "사냥터 범위 찍는 지팡이")));
        // 물·기타
        say(src, row("물·기타", run("호수 (반지름 8)", "//cyl water 8 1", a, "발밑에 반지름 8 물 원판 (먼저 파두기)"),
                run("물 빼기", "//drain 10", r, "주변 10칸 물 없애기"), run("불 끄기", "//ext 10", ChatFormatting.GRAY, "주변 10칸 불 끄기"),
                run("위로 올라가기", "/up 5", y, "5칸 위 공중에 유리 받침"), run("◀ 관리 패널", "/rpg 관리", ChatFormatting.WHITE, "관리자 패널")));
    }

    // ───────── 미리보기 (파티클, 그 사람한테만 20초) ─────────
    private record Show(net.minecraft.world.phys.Vec3 c, int r, net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b, long until) {}
    private static final Map<java.util.UUID, Show> SHOWS = new java.util.HashMap<>();

    static int circle(ServerPlayer p, int r) {
        SHOWS.put(p.getUUID(), new Show(p.position(), r, null, null, p.level().getGameTime() + 400));
        p.sendSystemMessage(Component.literal("[건축] 발밑에 반지름 " + r + " 원 (지름 " + (r * 2) + "칸) · 20초").withStyle(ChatFormatting.LIGHT_PURPLE));
        return 1;
    }

    static int zoneSize(ServerPlayer p) {
        net.minecraft.core.BlockPos[] ps = rpg.core.zone.ZoneWand.get(p);
        if (ps[0] == null || ps[1] == null) { p.sendSystemMessage(Component.literal("[건축] 먼저 사냥터 지팡이(/rpg wand)로 두 점을 찍어 주세요").withStyle(ChatFormatting.RED)); return 0; }
        int w = Math.abs(ps[0].getX() - ps[1].getX()) + 1, d = Math.abs(ps[0].getZ() - ps[1].getZ()) + 1;
        int area = w * d, per = area / 8;   // 사냥터 최대 8마리
        String feel = per < 60 ? "좁음 (몹이 붙어서 나옴)" : per > 250 ? "넓음 (몹 찾으러 걸어야 함)" : "적당함";
        p.sendSystemMessage(Component.literal("[건축] 사냥터 범위 " + w + " × " + d + " = " + area + "칸 · 몹 최대 8마리 → 1마리당 약 " + per + "칸 · " + feel
                + " (추천 한 변 20~40칸)").withStyle(ChatFormatting.GREEN));
        SHOWS.put(p.getUUID(), new Show(null, 0, ps[0], ps[1], p.level().getGameTime() + 400));
        return 1;
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = rpg.core.RpgCore.MODID)
    public static final class Ticker {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e) {
            if (e.phase != net.minecraftforge.event.TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
            Show s = SHOWS.get(p.getUUID());
            if (s == null) return;
            if (p.level().getGameTime() > s.until()) { SHOWS.remove(p.getUUID()); return; }
            var l = p.serverLevel();
            var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.75f, 0.35f, 1f), 1.6f);
            if (s.c() != null) {
                int n = Math.max(24, s.r() * 7);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    l.sendParticles(p, dust, true, s.c().x + Math.cos(a) * s.r(), s.c().y + 0.3, s.c().z + Math.sin(a) * s.r(), 1, 0, 0, 0, 0);
                }
                l.sendParticles(p, dust, true, s.c().x, s.c().y + 0.3, s.c().z, 3, 0.1, 0, 0.1, 0);
            } else {
                var green = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.3f, 1f, 0.4f), 1.6f);
                int x0 = Math.min(s.a().getX(), s.b().getX()), x1 = Math.max(s.a().getX(), s.b().getX()) + 1;
                int z0 = Math.min(s.a().getZ(), s.b().getZ()), z1 = Math.max(s.a().getZ(), s.b().getZ()) + 1;
                double y = Math.max(s.a().getY(), s.b().getY()) + 1.2;
                for (int x = x0; x <= x1; x++) { l.sendParticles(p, green, true, x, y, z0, 1, 0, 0, 0, 0); l.sendParticles(p, green, true, x, y, z1, 1, 0, 0, 0, 0); }
                for (int z = z0; z <= z1; z++) { l.sendParticles(p, green, true, x0, y, z, 1, 0, 0, 0, 0); l.sendParticles(p, green, true, x1, y, z, 1, 0, 0, 0, 0); }
            }
        }
    }

    /** 불러오기 + 회전 + 붙이기 (플레이어 이름으로 WorldEdit 명령 실행) */
    static int paste(ServerPlayer p, String file, int deg) {
        if (!ModList.get().isLoaded("worldedit")) { p.sendSystemMessage(Component.literal("WorldEdit 이 없어요").withStyle(ChatFormatting.RED)); return 0; }
        if (!file.matches("[A-Za-z0-9_\\-.]+")) { p.sendSystemMessage(Component.literal("파일 이름이 이상해요: " + file).withStyle(ChatFormatting.RED)); return 0; }
        var cmds = p.getServer().getCommands();
        CommandSourceStack src = p.createCommandSourceStack();
        cmds.performPrefixedCommand(src, "//schem load " + file);   // WorldEdit 명령은 이름이 "/schem" 이라 슬래시 두 개
        if (deg % 360 != 0) cmds.performPrefixedCommand(src, "//rotate " + deg);
        cmds.performPrefixedCommand(src, "//paste -a");
        p.sendSystemMessage(Component.literal("[건축] " + NAMES.getOrDefault(file, file) + " 붙임 (" + deg + "°) · ").withStyle(ChatFormatting.GOLD)
                .append(run("되돌리기", "//undo", ChatFormatting.RED, "방금 붙인 것 취소")).append(" ").append(run("건축 도우미", "/rpg 건축", ChatFormatting.WHITE, "다시 열기")));
        return 1;
    }
}
