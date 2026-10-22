package rpg.core.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import rpg.core.equip.Enhance;

/**
 * 주문서 (아이템 1종, 종류·등급은 NBT rpgScroll {kind, grade}).
 * kind: watk 무기 공격력 · wmatk 무기 마력 · str dex int luk vit 스탯 · white 순백
 * grade: 0 = 100% · 1 = 60% · 2 = 30% · 3 = 10%
 * 사용: 메뉴(M) → 강화 → 주문서
 */
public class Scroll extends Item {
    public static final String[] KINDS = {"watk", "wmatk", "str", "dex", "int", "luk", "vit"};
    /** 등급별 아이콘 색 (100 · 60 · 30 · 10%), 순백 = 흰색 */
    public static final int[] COLORS = {0x8FC8FF, 0x7CE07C, 0xFFB040, 0xFF5A5A};

    public Scroll(Properties p) {
        super(p.stacksTo(64));
    }

    public static ItemStack make(String kind, int grade) {
        ItemStack s = new ItemStack(ModItems.SCROLL.get());
        var t = s.getOrCreateTagElement("rpgScroll");
        t.putString("kind", kind);
        t.putInt("grade", grade);
        return s;
    }

    public static String kind(ItemStack s) {
        return s.getItem() instanceof Scroll && s.hasTag() ? s.getTag().getCompound("rpgScroll").getString("kind") : "";
    }

    public static int grade(ItemStack s) {
        return s.hasTag() ? s.getTag().getCompound("rpgScroll").getInt("grade") : 0;
    }

    public static boolean isWeapon(String kind) {
        return kind.equals("watk") || kind.equals("wmatk");
    }

    public static int color(ItemStack s) {
        if (kind(s).equals("white")) return 0xFFFFFF;
        return COLORS[Math.max(0, Math.min(COLORS.length - 1, grade(s)))];
    }

    public static String kindName(String k) {
        return switch (k) {
            case "watk" -> "무기 공격력";
            case "wmatk" -> "무기 마력";
            case "str" -> "힘";
            case "dex" -> "민첩";
            case "int" -> "지력";
            case "luk" -> "운";
            case "vit" -> "체력";
            case "white" -> "순백의";
            default -> "?";
        };
    }

    public static int rate(ItemStack s) {
        String k = kind(s);
        if (k.equals("white")) return (int) Enhance.whiteRate;
        int g = grade(s);
        int[][] t = isWeapon(k) ? Enhance.weaponScroll : Enhance.statScroll;
        return t[Math.max(0, Math.min(t.length - 1, g))][0];
    }

    @Override
    public Component getName(ItemStack s) {
        String k = kind(s);
        if (k.isEmpty()) return super.getName(s);
        if (k.equals("white")) return Component.literal("순백의 주문서");
        return Component.literal(kindName(k) + " 주문서 " + rate(s) + "%");
    }

    @Override
    public Rarity getRarity(ItemStack s) {
        if (kind(s).equals("white")) return Rarity.EPIC;
        return switch (grade(s)) { case 0 -> Rarity.COMMON; case 1 -> Rarity.UNCOMMON; case 2 -> Rarity.RARE; default -> Rarity.EPIC; };
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        String k = kind(s);
        if (k.isEmpty()) { tip.add(Component.literal("빈 주문서").withStyle(ChatFormatting.DARK_GRAY)); return; }
        if (k.equals("white")) {
            tip.add(Component.literal("성공률 " + rate(s) + "%").withStyle(ChatFormatting.GRAY));
            tip.add(Component.literal("실패로 날아간 업그레이드 횟수 1회 복구").withStyle(ChatFormatting.WHITE));
        } else {
            int g = grade(s);
            tip.add(Component.literal("성공률 " + rate(s) + "%").withStyle(ChatFormatting.GRAY));
            if (isWeapon(k)) {
                int[] v = Enhance.weaponScroll[Math.min(g, Enhance.weaponScroll.length - 1)];
                tip.add(Component.literal("무기 " + (k.equals("wmatk") ? "마력" : "공격력") + " +" + v[1] + " · 주 스탯 +" + v[2]).withStyle(ChatFormatting.WHITE));
                tip.add(Component.literal(k.equals("wmatk") ? "지팡이에만 사용" : "지팡이 외 무기에만 사용").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                int[] v = Enhance.statScroll[Math.min(g, Enhance.statScroll.length - 1)];
                tip.add(Component.literal(kindName(k) + " +" + v[1]).withStyle(ChatFormatting.WHITE));
                tip.add(Component.literal("방어구·장신구에 사용").withStyle(ChatFormatting.DARK_GRAY));
            }
            tip.add(Component.literal("성공·실패 모두 업그레이드 횟수 1 소모").withStyle(ChatFormatting.DARK_GRAY));
        }
        tip.add(Component.literal("메뉴(M) → 강화 → 주문서").withStyle(ChatFormatting.DARK_GRAY));
    }
}
