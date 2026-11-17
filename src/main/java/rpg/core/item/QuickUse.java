package rpg.core.item;

import net.minecraft.world.item.Item;

/** 물약 칸(1~4 키)에 넣을 수 있는 사용템 (물약 빼고) */
public final class QuickUse {
    private QuickUse() {}

    public static boolean usable(Item it) {
        return it instanceof BuffItem || it instanceof ReturnScroll || it instanceof TownScroll
                || it instanceof StatResetScroll || it instanceof Treasure || it instanceof rpg.core.loot.GoldPouch;
    }
}
