package rpg.core.equip;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import rpg.core.client.ClientData;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/** 장비 아이템 공통: id·정의 찾기, 추가 옵션 굴리기, 착용 조건, 툴팁 */
public final class Gear {
    private Gear() {}

    public interface GearItem {
        String gearId();
    }

    public static GearDefs.GearDef def(ItemStack s) {
        return s.getItem() instanceof GearItem g ? GearDefs.ITEMS.get(g.gearId()) : null;
    }

    public static String slotOf(ItemStack s) {
        GearDefs.GearDef d = def(s);
        return d == null ? "" : d.slot();
    }

    // ───────── 추가 옵션 ─────────
    public static boolean rolled(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean("rpgRolled");
    }

    /** 상점 일반 장비: 추가 옵션 없음 (굴린 것으로 표시만) */
    public static void rollPlain(ItemStack s) {
        if (def(s) == null || rolled(s)) return;
        s.getOrCreateTag().put("rpgOpts", new CompoundTag());
        s.getOrCreateTag().putBoolean("rpgRolled", true);
    }

    /** 처음 얻었을 때 한 번 (제작·드롭·보상): 추가 옵션 고정 줄 수(equipment.json options.lines, 기본 4), 옵션 종류·수치는 무작위 */
    public static void roll(ItemStack s) {
        GearDefs.GearDef d = def(s);
        if (d == null || rolled(s)) return;
        if (!Flame.can(d)) { rollPlain(s); return; }
        Flame.apply(s, Flame.roll(d, d.id(), 1, Flame.dropTiers));
    }

    public static CompoundTag opts(ItemStack s) {
        return s.hasTag() ? s.getTag().getCompound("rpgOpts") : new CompoundTag();
    }

    // ───────── 착용 조건 ─────────
    public static boolean canEquip(Player p, ItemStack s) {
        GearDefs.GearDef d = def(s);
        if (d == null || Enhance.broken(s)) return false;
        int level, job;
        if (p.level().isClientSide) { level = ClientData.level; job = ClientData.job; }
        else { PlayerData pd = RpgData.get(p); level = pd.level; job = pd.job; }
        if (level < d.level() - Flame.reqLv(s)) return false;
        if (d.job() == 0) return true;
        if (job == d.job()) return true;
        return job == 0 && d.level() < 10;   // 초보자는 Lv10 미만 직업 무기 사용 가능
    }

    /** 손에 든 장비 무기를 못 쓰는 이유 (쓸 수 있으면 null) */
    public static String weaponBlock(Player p, ItemStack s) {
        GearDefs.GearDef d = def(s);
        if (d == null || !"weapon".equals(d.slot()) || canEquip(p, s)) return null;
        int level = p.level().isClientSide ? ClientData.level : RpgData.get(p).level;
        if (level < d.level() - Flame.reqLv(s)) return "Lv." + (d.level() - Flame.reqLv(s)) + "부터 쓸 수 있는 무기예요";
        String job = switch (d.job()) { case 1 -> "전사"; case 2 -> "궁수"; case 3 -> "마법사"; case 4 -> "도적"; default -> ""; };
        return job + " 전용 무기라 쓸 수 없어요";
    }

    // ───────── 툴팁 ─────────
    public static void tooltip(ItemStack s, List<Component> tip) {
        GearDefs.GearDef d = def(s);
        if (d == null) { tip.add(Component.literal("(장비 정보 없음 — 서버 접속 후 표시)").withStyle(ChatFormatting.DARK_GRAY)); return; }
        String job = switch (d.job()) {
            case 1 -> "전사";
            case 2 -> "궁수";
            case 3 -> "마법사";
            case 4 -> "도적";
            default -> "모든 직업";
        };
        int rq = Flame.reqLv(s);
        tip.add(Component.literal(GearDefs.slotName(d.slot()) + " · 착용 Lv." + (d.level() - rq) + (rq > 0 ? " (" + d.level() + " -" + rq + ")" : "") + " · " + job).withStyle(ChatFormatting.GRAY));
        if (!d.title().isEmpty()) tip.add(Component.literal("칭호 《" + d.title() + "》 (훈장 칸에 끼면 머리 위에 표시)").withStyle(s2 -> s2.withColor(d.titleColor())));
        if (Enhance.broken(s)) tip.add(Component.literal("파괴된 장비 (능력치 없음) · 메뉴(M) → 강화에서 복구").withStyle(ChatFormatting.DARK_RED));
        int max = Enhance.maxStar(d), st = Enhance.star(s);
        if (max > 0) tip.add(Component.literal("★".repeat(Math.min(st, max)) + "☆".repeat(Math.max(0, max - st))).withStyle(ChatFormatting.YELLOW));
        int um = Enhance.upgMax(d);
        if (um > 0) tip.add(Component.literal("업그레이드 가능 " + Enhance.upgLeft(s) + " (실패 " + Enhance.upgFail(s) + ")").withStyle(ChatFormatting.GRAY));
        // 능력치 = 기본 + 주문서(파랑) + 스타포스(노랑) + 추가 옵션(초록)
        CompoundTag sc = Enhance.scr(s);
        CompoundTag o = opts(s);
        Map<String, Integer> ox = Flame.expand(o);
        Map<String, Integer> sf = Enhance.starStats(s);
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(d.stats().keySet());
        for (String k : GearDefs.STAT_KEYS) if (sc.contains(k) || sf.containsKey(k) || ox.containsKey(k)) keys.add(k);
        for (String k : keys) {
            double base = d.stats().getOrDefault(k, 0.0);
            int a = sc.getInt(k), b = sf.getOrDefault(k, 0), c = ox.getOrDefault(k, 0);
            var line = Component.literal(GearDefs.label(k) + " +" + fmt(base + a + b + c, k)).withStyle(ChatFormatting.WHITE);
            if (a != 0 || b != 0 || c != 0) {
                line.append(Component.literal(" (" + fmt(base, k)).withStyle(ChatFormatting.GRAY));
                if (a != 0) line.append(Component.literal(" +" + a).withStyle(ChatFormatting.BLUE));
                if (b != 0) line.append(Component.literal(" +" + b).withStyle(ChatFormatting.YELLOW));
                if (c != 0) line.append(Component.literal(" +" + fmt(c, k)).withStyle(ChatFormatting.GREEN));
                line.append(Component.literal(")").withStyle(ChatFormatting.GRAY));
            }
            tip.add(line);
        }
        for (String k : new String[]{"bossPct", "atkPct", "speedPct"})   // 추가 옵션 전용 % 줄
            if (ox.containsKey(k)) tip.add(Component.literal(GearDefs.label(k) + " +" + ox.get(k) + "%").withStyle(ChatFormatting.GREEN));
        if (!o.isEmpty()) {   // 추가 옵션 단계
            CompoundTag tt = Flame.tiers(s);
            StringBuilder sb = new StringBuilder();
            for (String k : o.getAllKeys()) { if (sb.length() > 0) sb.append(" · "); sb.append(Flame.text(k, o.getInt(k), tt.getInt(k))); }
            tip.add(Component.literal("추가 옵션: " + sb).withStyle(ChatFormatting.DARK_GREEN));
        }
        if (Flame.pending(s)) tip.add(Component.literal("검은 불꽃 선택 대기 중 (메뉴 → 강화 → 추가옵션)").withStyle(ChatFormatting.DARK_GRAY));
        if (d.speed() > 0) tip.add(Component.literal("공격 속도 " + d.speed()).withStyle(ChatFormatting.DARK_GRAY));
        if (Potential.has(s)) {   // 잠재능력
            CompoundTag pt = Potential.pot(s);
            int g = Math.max(0, Math.min(3, pt.getInt("g")));
            tip.add(Component.literal("[" + Potential.GRADE[g] + "] 잠재능력").withStyle(sty -> sty.withColor(Potential.COLOR[g])));
            tip.addAll(Potential.lines(pt));
            if (Potential.pending(s)) tip.add(Component.literal("새 옵션 선택 대기 중 (메뉴 → 강화 → 잠재능력)").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (o.isEmpty() && rolled(s)) {
            tip.add(Component.literal("추가 옵션 없음").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 장비 이름: 강화하면 뒤에 (+N★), 파괴되면 앞에 [파괴] */
    public static Component name(ItemStack s, Component base) {
        if (def(s) == null) return base;
        int st = Enhance.star(s);
        boolean br = Enhance.broken(s);
        if (st <= 0 && !br) return base;
        var c = Component.empty();
        if (br) c.append(Component.literal("[파괴] ").withStyle(ChatFormatting.DARK_RED));
        c.append(base);
        if (st > 0) c.append(Component.literal(" (+" + st + "★)").withStyle(ChatFormatting.YELLOW));
        return c;
    }

    private static String fmt(double v, String k) {
        String n = v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
        return k.equals("allPct") ? n + "%" : n;
    }
}
