package rpg.core.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * json 아이템 id ↔ 아이템. NBT가 붙는 주문서는 # 으로 종류·등급을 적음:
 * "scroll#str#0" = 힘 주문서 100% (등급 0 100% · 1 60% · 2 30% · 3 10%), "scroll#white" = 순백의 주문서.
 * 판매가 키: 주문서는 등급만 봄 → "scroll#g0" ~ "scroll#g3", "scroll#white"
 */
public final class ItemIds {
    private ItemIds() {}

    /** id 의 아이템 (# 뒤는 무시). 없으면 null */
    public static Item item(String id) {
        String base = id.contains("#") ? id.substring(0, id.indexOf('#')) : id;
        try {
            Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(base));
            return it == null || it == Items.AIR ? null : it;
        } catch (Exception e) {
            return null;
        }
    }

    /** id → 아이템 묶음 (주문서는 종류·등급 NBT 붙임). 없으면 EMPTY */
    public static ItemStack stack(String id, int n) {
        Item it = item(id);
        if (it == null) return ItemStack.EMPTY;
        ItemStack s;
        if (it instanceof Scroll && id.contains("#")) {
            String[] p = id.substring(id.indexOf('#') + 1).split("#");
            int g = 0;
            try { if (p.length > 1) g = Integer.parseInt(p[1]); } catch (NumberFormatException ignored) {}
            s = Scroll.make(p[0], g);
        } else {
            s = new ItemStack(it);
        }
        s.setCount(Math.max(1, n));
        return s;
    }

    /** 아이템 → 판매가 키 */
    public static String sellKey(ItemStack s) {
        ResourceLocation rl = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (rl == null) return "";
        String k = Scroll.kind(s);
        if (k.isEmpty()) return rl.toString();
        return rl + (k.equals("white") ? "#white" : "#g" + Scroll.grade(s));
    }

    /** 같은 종류인지 (주문서는 종류·등급까지) — 재료 세기용 */
    public static boolean matches(ItemStack s, String id) {
        Item it = item(id);
        if (it == null || !s.is(it)) return false;
        if (!(it instanceof Scroll) || !id.contains("#")) return true;
        ItemStack want = stack(id, 1);
        return Scroll.kind(s).equals(Scroll.kind(want)) && Scroll.grade(s) == Scroll.grade(want);
    }
}
