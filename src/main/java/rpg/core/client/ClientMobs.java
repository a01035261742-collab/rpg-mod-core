package rpg.core.client;

import java.util.HashMap;
import java.util.Map;

/** 서버에서 받은 RPG 몹 정보 (엔티티 id → 레벨·체력·이름) */
public final class ClientMobs {
    private ClientMobs() {}

    public record Info(int level, double hp, double maxHp, String name, long time) {}

    public static final Map<Integer, Info> MAP = new HashMap<>();

    public static void update(int id, int level, double hp, double maxHp, String name) {
        Info old = MAP.put(id, new Info(level, hp, maxHp, name, System.currentTimeMillis()));
        // 엘리트면 몸통 크기 다시 계산 (WorldText.eliteSizeClient) — 엔티티는 메인 스레드에서 만짐
        if (name.startsWith("★") || old != null && old.name().startsWith("★")) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.level == null) return;
                net.minecraft.world.entity.Entity e = mc.level.getEntity(id);
                if (e == null) return;
                float w = e.getBbWidth();
                e.refreshDimensions();
                if (e.getBbWidth() != w) e.setPos(e.getX(), e.getY(), e.getZ());
            });
        }
    }
}
