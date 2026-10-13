package rpg.core.client;

import net.minecraft.network.FriendlyByteBuf;

/** 서버에서 받은 내 RPG 데이터 (화면 표시용) */
public final class ClientData {
    private ClientData() {}

    public static boolean ready;
    public static int level;
    public static long exp, need;
    public static final int[] stats = new int[5];
    public static int statPoints, job;
    public static long gold;
    public static double hp, mp, maxHp = 1, maxMp = 1;
    public static double crit, healBonus, dropBonus, goldBonus;
    public static double atkMin, atkMax, defPct;

    public static void read(FriendlyByteBuf b) {
        b.readerIndex(0);
        level = b.readVarInt(); exp = b.readLong(); need = b.readLong();
        for (int i = 0; i < 5; i++) stats[i] = b.readVarInt();
        statPoints = b.readVarInt(); job = b.readVarInt(); gold = b.readLong();
        hp = b.readDouble(); mp = b.readDouble(); maxHp = b.readDouble(); maxMp = b.readDouble();
        crit = b.readDouble(); healBonus = b.readDouble(); dropBonus = b.readDouble(); goldBonus = b.readDouble();
        atkMin = b.readDouble(); atkMax = b.readDouble(); defPct = b.readDouble();
        ready = true;
    }

    public static String jobName() {
        return switch (job) {
            case 1 -> "전사";
            case 2 -> "궁수";
            case 3 -> "마법사";
            case 4 -> "도적";
            default -> "초보자";
        };
    }
}
