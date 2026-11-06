package rpg.core.client;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loader.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import rpg.core.net.Net;

/**
 * 물약 칸 4개 (1·2·3·4 키): 칸엔 "물약 종류"만 기억 (가방에서 끌어다 놓기), 누르면 가방에서 그 물약 하나 마시기.
 * 칸 설정은 이 컴퓨터 config/rpgcore-potions.txt 에 저장.
 */
public final class ClientPotions {
    private ClientPotions() {}

    public static final String[] IDS = new String[4];
    private static boolean loaded;

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("rpgcore-potions.txt"); }

    public static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(file())) {   // 처음: 빨간 · 파란 물약
                IDS[0] = "rpgcore:red_potion";
                IDS[1] = "rpgcore:blue_potion";
                return;
            }
            var lines = Files.readAllLines(file());
            for (int i = 0; i < 4 && i < lines.size(); i++) IDS[i] = lines.get(i).isBlank() ? null : lines.get(i).trim();
        } catch (Exception ignored) {}
    }

    private static void save() {
        StringBuilder sb = new StringBuilder();
        for (String s : IDS) sb.append(s == null ? "" : s).append('\n');
        try { Files.writeString(file(), sb.toString()); } catch (Exception ignored) {}
    }

    /** 칸에 넣을 수 있는 물건 (물약만) */
    public static boolean fits(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof rpg.core.item.Potion;
    }

    public static void set(int k, ItemStack s) {
        load();
        IDS[k] = s.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(s.getItem()).toString();
        save();
    }

    public static Item item(int k) {
        load();
        return IDS[k] == null ? null : ForgeRegistries.ITEMS.getValue(new ResourceLocation(IDS[k]));
    }

    /** 가방에 있는 개수 */
    public static int count(int k) {
        Item it = item(k);
        var p = Minecraft.getInstance().player;
        if (it == null || p == null) return 0;
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.getItem() == it) n += s.getCount();
        return n;
    }

    /** 남은 쿨타임 비율 (0~1) */
    public static float cooldown(int k) {
        Item it = item(k);
        var p = Minecraft.getInstance().player;
        return it == null || p == null ? 0 : p.getCooldowns().getCooldownPercent(it, 0);
    }

    public static void use(int k) {
        load();
        if (IDS[k] != null && count(k) > 0) Net.CH.sendToServer(new Net.QuickAct(0, IDS[k]));
    }
}
