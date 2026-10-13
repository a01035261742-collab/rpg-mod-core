package rpg.core.client;

import java.util.HashMap;
import java.util.Map;

/** 서버에서 받은 RPG 몹 정보 (엔티티 id → 레벨·체력·이름) */
public final class ClientMobs {
    private ClientMobs() {}

    public record Info(int level, double hp, double maxHp, String name, long time) {}

    public static final Map<Integer, Info> MAP = new HashMap<>();

    public static void update(int id, int level, double hp, double maxHp, String name) {
        MAP.put(id, new Info(level, hp, maxHp, name, System.currentTimeMillis()));
    }
}
