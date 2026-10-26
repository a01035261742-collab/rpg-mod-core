package rpg.core.client;

import net.minecraft.network.FriendlyByteBuf;

/** 서버에서 받은 내 RPG 데이터 (화면 표시용) */
public final class ClientData {
    private ClientData() {}

    public static boolean ready;
    public static int level;
    public static long exp, need;
    public static final int[] stats = new int[6];
    public static final int[] statBonus = new int[6];   // 장비로 더해진 스탯
    public static int statPoints, job;
    public static long gold;
    public static double hp, mp, maxHp = 1, maxMp = 1;
    public static double crit, healBonus, dropBonus, goldBonus, staminaBonus;
    public static double atkMin, atkMax, defPct, critDmgPct, ignoreDef, bossPct, overloadPct;
    public static int skillPoints, passivePoints;
    public static final java.util.Map<String, Integer> skills = new java.util.LinkedHashMap<>();
    public static final String[] slots = new String[6];
    public static final java.util.Map<String, Integer> passives = new java.util.HashMap<>();
    public static final int[] cdLeft = new int[6], cdLen = new int[6];
    public static int dodgeLeft, dodgeLen, sprintLeft;
    public static int downLeft = -1, reviveProgress = -1;
    public static long receivedAt;
    /** kind 0 스킬 · 1 파티 · 2 트리 · 3 재사용 대기, extra = 중첩·수치 글자 */
    public record Buff(String name, int left, int kind, String extra) {}
    public static final java.util.List<Buff> BUFFS = new java.util.ArrayList<>();
    public static boolean charging;
    public static int chargeTicks;
    public static int branch, pendingBranch, freeTreeReset;
    public static boolean trialCleared;
    public static double shield;

    public static void read(FriendlyByteBuf b) {
        b.readerIndex(0);
        level = b.readVarInt(); exp = b.readLong(); need = b.readLong();
        for (int i = 0; i < 6; i++) stats[i] = b.readVarInt();
        for (int i = 0; i < 6; i++) statBonus[i] = b.readVarInt();
        statPoints = b.readVarInt(); job = b.readVarInt(); gold = b.readLong();
        hp = b.readDouble(); mp = b.readDouble(); maxHp = b.readDouble(); maxMp = b.readDouble();
        crit = b.readDouble(); healBonus = b.readDouble(); dropBonus = b.readDouble(); goldBonus = b.readDouble();
        atkMin = b.readDouble(); atkMax = b.readDouble(); defPct = b.readDouble();
        critDmgPct = b.readDouble();
        staminaBonus = b.readDouble();
        ignoreDef = b.readDouble(); bossPct = b.readDouble();
        overloadPct = b.readDouble();
        freeTreeReset = b.readVarInt();
        skillPoints = b.readVarInt(); passivePoints = b.readVarInt();
        skills.clear();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) skills.put(b.readUtf(), b.readVarInt());
        for (int i = 0; i < 6; i++) { String s = b.readUtf(); slots[i] = s.isEmpty() ? null : s; }
        passives.clear();
        n = b.readVarInt();
        for (int i = 0; i < n; i++) passives.put(b.readUtf(), b.readVarInt());
        for (int i = 0; i < 6; i++) { cdLeft[i] = b.readVarInt(); cdLen[i] = b.readVarInt(); }
        dodgeLeft = b.readVarInt(); dodgeLen = b.readVarInt(); sprintLeft = b.readVarInt();
        downLeft = b.readVarInt(); reviveProgress = b.readVarInt();
        BUFFS.clear();
        int nb = b.readVarInt();
        for (int i = 0; i < nb; i++) BUFFS.add(new Buff(b.readUtf(), b.readVarInt(), b.readByte(), b.readUtf()));
        charging = b.readBoolean();
        chargeTicks = b.readVarInt();
        branch = b.readVarInt(); pendingBranch = b.readVarInt(); trialCleared = b.readBoolean();
        shield = b.readDouble();
        receivedAt = System.currentTimeMillis();
        ready = true;
    }

    /** 받은 뒤 흐른 시간을 빼서 남은 쿨타임(틱) */
    public static int left(int ticksAtReceive) {
        long passed = (System.currentTimeMillis() - receivedAt) / 50;
        return (int) Math.max(0, ticksAtReceive - passed);
    }

    public static int jobTier() {
        return job == 0 ? 0 : branch > 0 ? 2 : 1;
    }

    public static String jobName() {
        if (branch > 0) return rpg.core.job.Jobs.branchName(job, branch);
        return switch (job) {
            case 1 -> "전사";
            case 2 -> "궁수";
            case 3 -> "마법사";
            case 4 -> "도적";
            default -> "초보자";
        };
    }
}

