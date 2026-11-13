package rpg.core.equip;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;
import rpg.core.item.ModItems;

/** 장비 계승 (강화 창 [계승] 탭): 옛 장비 → 새 장비 (같은 부위, 더 높은 레벨, ★0).
 *  옮길 것을 골라서 (★ 절반 · 잠재능력 · 추가 옵션 · 주문서), 항목마다 골드 + 재료. 옛 장비는 없어짐. */
public final class Inherit {
    private Inherit() {}

    public static final int STAR = 1, POT = 2, FLAME = 4, SCROLL = 8;
    public static final int[] KINDS = {STAR, POT, FLAME, SCROLL};
    public static final String[] NAMES = {"스타포스", "잠재능력", "추가 옵션", "주문서 강화"};

    public record Cost(long gold, Item mat, int count) {}

    public static boolean valid(ItemStack o, ItemStack n) {
        GearDefs.GearDef od = Gear.def(o), nd = Gear.def(n);
        if (od == null || nd == null || o == n) return false;
        if (Enhance.broken(o) || Enhance.broken(n)) return false;
        return Gear.slotOf(o).equals(Gear.slotOf(n)) && nd.level() > od.level() && Enhance.star(n) == 0;
    }

    /** 옮겨질 ★ (절반, 새 장비 최대 ★ 까지) */
    public static int starTarget(ItemStack o, ItemStack n) {
        return Math.min(Enhance.star(o) / 2, Enhance.maxStar(Gear.def(n)));
    }

    /** 주문서 성공 칸 수 */
    public static int succ(ItemStack o) {
        GearDefs.GearDef d = Gear.def(o);
        return d == null ? 0 : Math.max(0, Enhance.upgMax(d) - Enhance.upgLeft(o) - Enhance.upgFail(o));
    }

    /** 이 장비에 옮길 게 있나 */
    public static boolean has(int kind, ItemStack o, ItemStack n) {
        return switch (kind) {
            case STAR -> starTarget(o, n) > 0;
            case POT -> Potential.has(o);
            case FLAME -> !Flame.tiers(o).isEmpty();
            case SCROLL -> succ(o) > 0;
            default -> false;
        };
    }

    /** 한 줄 설명 */
    public static String desc(int kind, ItemStack o, ItemStack n) {
        return switch (kind) {
            case STAR -> "★" + Enhance.star(o) + " → 새 장비 ★" + starTarget(o, n);
            case POT -> new String[]{"레어", "에픽", "유니크", "레전드리"}[Math.max(0, Math.min(3, Potential.grade(o)))] + " 그대로";
            case FLAME -> Flame.tiers(o).size() + "줄 (새 장비 레벨로)";
            case SCROLL -> "성공 " + succ(o) + "칸 그대로";
            default -> "";
        };
    }

    /** 비용 (새 장비 기준, 일부러 비싸게) */
    public static Cost cost(int kind, ItemStack o, ItemStack n) {
        GearDefs.GearDef nd = Gear.def(n);
        if (nd == null) return new Cost(0, ModItems.STAR_FRAGMENT.get(), 0);
        return switch (kind) {
            case STAR -> {
                int t = starTarget(o, n);
                long g = 0;
                for (int i = 0; i < t; i++) g += Enhance.cost(nd, i, false);
                yield new Cost(round(g), ModItems.STAR_FRAGMENT.get(), 5 * t);
            }
            case POT -> {
                int gr = Math.max(0, Math.min(3, Potential.grade(o)));
                yield new Cost(round(Potential.cost(nd, gr) * (gr + 2)), ModItems.CUBE.get(), gr + 1);
            }
            case FLAME -> {
                int l = Flame.tiers(o).size();
                yield new Cost(round(Flame.cost(nd) * l * 5), ModItems.ETERNAL_FLAME.get(), Math.max(1, l / 2));
            }
            default -> {
                int s = succ(o);
                yield new Cost(round(5000L * s), ModItems.SCROLL_FRAGMENT.get(), 4 * s + 2);
            }
        };
    }

    private static long round(double g) { return Math.round(g / 1000.0) * 1000; }

    // ───────── 서버 ─────────
    public static void run(ServerPlayer p, int oldLoc, int newLoc, int mask) {
        ItemStack o = Enhance.at(p, oldLoc), n = Enhance.at(p, newLoc);
        if (oldLoc == newLoc || !valid(o, n)) { msg(p, "계승할 수 없는 조합이에요 (같은 부위 · 더 높은 레벨 · 새 장비 ★0)", ChatFormatting.RED); return; }
        PlayerData d = RpgData.get(p);
        long gold = 0;
        java.util.Map<Item, Integer> mats = new java.util.HashMap<>();
        int use = 0;
        for (int k : KINDS) {
            if ((mask & k) == 0 || !has(k, o, n)) continue;
            Cost c = cost(k, o, n);
            gold += c.gold();
            mats.merge(c.mat(), c.count(), Integer::sum);
            use |= k;
        }
        if (use == 0) { msg(p, "옮길 것을 골라주세요", ChatFormatting.GRAY); return; }
        if (d.gold < gold) { msg(p, "골드가 부족해요 (" + String.format("%,d", gold) + " G 필요)", ChatFormatting.RED); return; }
        for (var e : mats.entrySet())
            if (Enhance.count(p, e.getKey()) < e.getValue()) { msg(p, e.getKey().getDescription().getString() + " 이(가) 부족해요 (" + e.getValue() + "개 필요)", ChatFormatting.RED); return; }
        d.gold -= gold;
        for (var e : mats.entrySet()) take(p, e.getKey(), e.getValue());
        GearDefs.GearDef nd = Gear.def(n);
        CompoundTag nt = n.getOrCreateTag();
        if ((use & STAR) != 0) nt.putInt("rpgStar", starTarget(o, n));
        if ((use & POT) != 0) {
            nt.put("rpgPot", Potential.pot(o).copy());
            nt.getCompound("rpgPot").remove("new");
        }
        if ((use & FLAME) != 0) {
            CompoundTag t = Flame.tiers(o).copy(), v = new CompoundTag();
            for (String k : t.getAllKeys()) v.putInt(k, Flame.value(nd, k, t.getInt(k)));
            nt.put("rpgOpts", v);
            nt.put("rpgOptT", t);
            nt.putBoolean("rpgRolled", true);
            nt.remove("rpgOptNew");
        }
        if ((use & SCROLL) != 0) {
            int s = succ(o);
            nt.put("rpgScr", Enhance.scr(o).copy());
            nt.putInt("rpgUpg", Math.max(0, Enhance.upgMax(nd) - s));
            nt.putInt("rpgUpgFail", 0);
        }
        remove(p, oldLoc);
        RpgData.sync(p);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1f, 1f);
        msg(p, "계승 완료! (" + String.format("%,d", gold) + " G)", ChatFormatting.GOLD);
    }

    private static void remove(ServerPlayer p, int loc) {
        if (loc >= 0 && loc < 36) p.getInventory().items.set(loc, ItemStack.EMPTY);
        else if (loc >= 100 && loc < 104) p.getInventory().armor.set(loc - 100, ItemStack.EMPTY);
        else if (loc > 200 && loc < 200 + PlayerEquip.SLOT_TYPES.length) PlayerEquip.get(p).items.setStackInSlot(loc - 200, ItemStack.EMPTY);
    }

    private static void take(ServerPlayer p, Item it, int n) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() != it) continue;
            int t = Math.min(n, s.getCount());
            s.shrink(t);
            n -= t;
        }
    }

    private static void msg(ServerPlayer p, String s, ChatFormatting c) {
        p.displayClientMessage(Component.literal(s).withStyle(c), true);
    }
}
