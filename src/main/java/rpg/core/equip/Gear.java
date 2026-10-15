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
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int lines = GearDefs.fixedLines;
        if (lines < 0) {   // fixedLines 가 없으면 예전 방식 (lineChances 확률)
            int total = 0, pick;
            for (int c : GearDefs.lineChances) total += c;
            pick = r.nextInt(Math.max(1, total));
            for (int i = 0; i < GearDefs.lineChances.length; i++) {
                pick -= GearDefs.lineChances[i];
                if (pick < 0) { lines = i; break; }
            }
            lines = Math.max(0, lines);
        }
        List<String> pool = new ArrayList<>(GearDefs.POOLS.getOrDefault(d.slot(), List.of()));
        CompoundTag opts = new CompoundTag();
        for (int i = 0; i < lines && !pool.isEmpty(); i++) {
            String k = pool.remove(r.nextInt(pool.size()));
            double[] rg = GearDefs.RANGES.getOrDefault(k, new double[]{1, 0, 2, 0});
            int lo = (int) Math.max(1, Math.floor(rg[0] + rg[1] * d.level()));
            int hi = (int) Math.max(lo, Math.floor(rg[2] + rg[3] * d.level()));
            opts.putInt(k, lo + r.nextInt(hi - lo + 1));
        }
        s.getOrCreateTag().put("rpgOpts", opts);
        s.getOrCreateTag().putBoolean("rpgRolled", true);
    }

    public static CompoundTag opts(ItemStack s) {
        return s.hasTag() ? s.getTag().getCompound("rpgOpts") : new CompoundTag();
    }

    // ───────── 착용 조건 ─────────
    public static boolean canEquip(Player p, ItemStack s) {
        GearDefs.GearDef d = def(s);
        if (d == null) return false;
        int level, job;
        if (p.level().isClientSide) { level = ClientData.level; job = ClientData.job; }
        else { PlayerData pd = RpgData.get(p); level = pd.level; job = pd.job; }
        if (level < d.level()) return false;
        if (d.job() == 0) return true;
        if (job == d.job()) return true;
        return job == 0 && d.level() < 10;   // 초보자는 Lv10 미만 직업 무기 사용 가능
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
        tip.add(Component.literal(GearDefs.slotName(d.slot()) + " · 착용 Lv." + d.level() + " · " + job).withStyle(ChatFormatting.GRAY));
        for (Map.Entry<String, Double> e : d.stats().entrySet()) {
            tip.add(Component.literal(GearDefs.label(e.getKey()) + " +" + fmt(e.getValue(), e.getKey())).withStyle(ChatFormatting.WHITE));
        }
        if (d.speed() > 0) tip.add(Component.literal("공격 속도 " + d.speed()).withStyle(ChatFormatting.DARK_GRAY));
        CompoundTag o = opts(s);
        if (!o.isEmpty()) {
            tip.add(Component.literal("추가 옵션").withStyle(ChatFormatting.AQUA));
            for (String k : o.getAllKeys()) {
                tip.add(Component.literal(" " + GearDefs.label(k) + " +" + fmt(o.getInt(k), k)).withStyle(ChatFormatting.AQUA));
            }
        } else if (rolled(s)) {
            tip.add(Component.literal("추가 옵션 없음").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static String fmt(double v, String k) {
        String n = v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
        return k.equals("allPct") ? n + "%" : n;
    }
}
