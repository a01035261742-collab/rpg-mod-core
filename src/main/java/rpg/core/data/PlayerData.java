package rpg.core.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import rpg.core.balance.Balance;

/**
 * 플레이어 RPG 데이터.
 * 스탯 순서: 0 체력(VIT) · 1 힘(STR) · 2 민첩(DEX) · 3 지력(INT) · 4 운(LUK)
 */
public class PlayerData {
    public static final int VIT = 0, STR = 1, DEX = 2, INT = 3, LUK = 4;
    public static final String[] STAT_NAMES = {"체력", "힘", "민첩", "지력", "운"};

    public int level;
    public long exp;
    public final int[] stats = new int[5];
    public int statPoints;
    public int job;          // 0 초보자 (전직은 6단계에서)
    public long gold;
    public double hp, mp;

    // 계산값 (저장 안 함, 동기화로 보냄)
    public double maxHp = 1, maxMp = 1;

    public boolean isNew() { return level <= 0; }

    /** 처음 시작 */
    public void init() {
        level = 1; exp = 0; job = 0; gold = 0; statPoints = 0;
        for (int i = 0; i < 5; i++) stats[i] = Balance.startStat;
        recalc();
        hp = maxHp; mp = maxMp;
    }

    /** 계산값 다시 계산. 바뀌었으면 true */
    public boolean recalc() {
        double mh = Balance.hpBase + Balance.hpPerLevel * level + Balance.hpPerVit * stats[VIT];
        double mm = Balance.mpBase + Balance.mpPerLevel * level + Balance.mpPerInt * stats[INT];
        boolean changed = mh != maxHp || mm != maxMp;
        maxHp = Math.max(1, mh); maxMp = Math.max(0, mm);
        if (hp > maxHp) { hp = maxHp; changed = true; }
        if (mp > maxMp) { mp = maxMp; changed = true; }
        return changed;
    }

    /** 찍은 스탯을 전부 돌려받기 (초기화 주문서) */
    public int resetStats() {
        int refund = 0;
        for (int i = 0; i < 5; i++) {
            refund += Math.max(0, stats[i] - Balance.startStat);
            stats[i] = Balance.startStat;
        }
        statPoints += refund;
        recalc();
        return refund;
    }

    // ── 상세 능력치 (화면 표시용, 공식은 balance 파일) ──
    public double crit() { return Balance.critBase + Balance.critPerDex * stats[DEX]; }
    public double healBonus() { return Balance.healPerInt * stats[INT]; }
    public double dropBonus() { return Balance.dropPerLuk * stats[LUK]; }
    public double goldBonus() { return Balance.goldPerLuk * stats[LUK]; }

    // ── 저장 ──
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("level", level); t.putLong("exp", exp); t.putIntArray("stats", stats.clone());
        t.putInt("sp", statPoints); t.putInt("job", job); t.putLong("gold", gold);
        t.putDouble("hp", hp); t.putDouble("mp", mp);
        return t;
    }

    public void load(CompoundTag t) {
        level = t.getInt("level"); exp = t.getLong("exp");
        int[] s = t.getIntArray("stats");
        for (int i = 0; i < 5 && i < s.length; i++) stats[i] = s[i];
        statPoints = t.getInt("sp"); job = t.getInt("job"); gold = t.getLong("gold");
        hp = t.getDouble("hp"); mp = t.getDouble("mp");
    }

    public void copyFrom(PlayerData o) {
        load(o.save());
        maxHp = o.maxHp; maxMp = o.maxMp;
    }

    // ── 동기화 (서버 → 본인) : 필요 경험치·상세 능력치까지 같이 보냄 ──
    public void write(FriendlyByteBuf b) {
        b.writeVarInt(level); b.writeLong(exp); b.writeLong(Balance.expNeed(level));
        for (int v : stats) b.writeVarInt(v);
        b.writeVarInt(statPoints); b.writeVarInt(job); b.writeLong(gold);
        b.writeDouble(hp); b.writeDouble(mp); b.writeDouble(maxHp); b.writeDouble(maxMp);
        b.writeDouble(crit()); b.writeDouble(healBonus()); b.writeDouble(dropBonus()); b.writeDouble(goldBonus());
    }
}
