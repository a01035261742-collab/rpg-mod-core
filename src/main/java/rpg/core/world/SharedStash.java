package rpg.core.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;

/** 공유 창고: 사람마다 27칸, 야생·RPG 어디서든 같은 창고 (플레이어 저장 데이터에 보관) */
public final class SharedStash {
    private SharedStash() {}

    private static final String KEY = "rpgSharedStash";

    public static void open(ServerPlayer p) {
        SimpleContainer box = new SimpleContainer(27) {
            @Override public void stopOpen(Player who) { super.stopOpen(who); save(p, this); }
        };
        load(p, box);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, box), Component.literal("공유 창고 (야생·RPG 공통)")));
    }

    private static CompoundTag keep(ServerPlayer p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static void load(ServerPlayer p, SimpleContainer box) {
        ListTag l = keep(p).getList(KEY, 10);
        box.fromTag(l);
    }

    private static void save(ServerPlayer p, SimpleContainer box) {
        CompoundTag k = keep(p);
        k.put(KEY, box.createTag());
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, k);
    }
}
