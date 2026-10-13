package rpg.core.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import rpg.core.balance.Balance;
import rpg.core.skill.Defs;

/**
 * 플레이어 RPG 데이터.
 * 스탯 순서: 0 체력(VIT) · 1 힘(STR) · 2 민첩(DEX) · 3 지력(INT) · 4 운(LUK)
 */
public class PlayerData {
    public static final int VIT = 0, STR = 1, DEX = 2, INT = 3, LUK = 4;
    public static final String[] STAT_NAMES = {"체력", "힘", "민첩", "지력", "운"};
    public static final int SLOTS = 6;

    public int level;
    public long exp;
    public final int[] stats = new int[5];
    public int statPoints;
    public int job;          // 0 초보자 (전직은 6단계에서)
    public long gold;
    public double hp, mp;

    // 스킬·패시브
    public int skillPoints, passivePoints;
    public final Map<String, Integer> skills = new LinkedHashMap<>();   // 스킬 id → 레벨
    public final String[] slots = new String[SLOTS];                    // Q E R F Z X 에 들어간 스킬 id
    public final Map<String, Integer> passives = new HashMap<>();       // 노드 id → 랭크

    // 쿨타임·버프 (저장 안 함, 게임 시간 틱)
    public final Map<String, Long> cdEnd = new HashMap<>();
    public final Map<String, Integer> cdLen = new HashMap<>();
    public long dodgeEnd;
    public int dodgeLen;
    public long sprintEnd;
    public double sprintPct;

    // 계산값 (저장 안 함)
    public double maxHp = 1, maxMp = 1;
    public final Map<String, Double> bonus = new HashMap<>();   // 패시브 합계 (stat → 값)

    public boolean isNew() { return level <= 0; }

    public int jobTier() {
        return job == 0 ? 0 : 1;   // 전직 단계는 6단계에서 확장
    }

    /** 처음 시작 */
    public void init() {
        level = 1; exp = 0; job = 0; gold = 0; statPoints = 0; skillPoints = 0; passivePoints = 0;
        for (int i = 0; i < 5; i++) stats[i] = Balance.startStat;
        skills.clear(); passives.clear(); cdEnd.clear(); cdLen.clear();
        java.util.Arrays.fill(slots, null);
        recalc();
        hp = maxHp; mp = maxMp;
    }

    public double bonus(String stat) {
        return bonus.getOrDefault(stat, 0.0);
    }

    /** 계산값 다시 계산. 바뀌었으면 true */
    public boolean recalc() {
        bonus.clear();
        for (var e : passives.entrySet()) {
            Defs.Node n = Defs.NODES.get(e.getKey());
            if (n == null || n.stat().isEmpty()) continue;
            bonus.merge(n.stat(), n.perRank() * e.getValue(), Double::sum);
        }
        double mh = (Balance.hpBase + Balance.hpPerLevel * level + Balance.hpPerVit * stats[VIT]) * (1 + bonus("hpPct") / 100.0);
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

    // ── 상세 능력치 (공식은 balance 파일) ──
    public double crit() { return Balance.critBase + Balance.critPerDex * stats[DEX] + bonus("critChance"); }
    public double critDamage() { return Balance.critDamage + bonus("critDmg") / 100.0; }
    public double healBonus() { return Balance.healPerInt * stats[INT]; }
    public double dropBonus() { return Balance.dropPerLuk * stats[LUK]; }
    public double goldBonus() { return Balance.goldPerLuk * stats[LUK]; }

    // ── 저장 ──
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("level", level); t.putLong("exp", exp); t.putIntArray("stats", stats.clone());
        t.putInt("sp", statPoints); t.putInt("job", job); t.putLong("gold", gold);
        t.putDouble("hp", hp); t.putDouble("mp", mp);
        t.putInt("skp", skillPoints); t.putInt("pp", passivePoints);
        CompoundTag sk = new CompoundTag();
        skills.forEach(sk::putInt);
        t.put("skills", sk);
        CompoundTag sl = new CompoundTag();
        for (int i = 0; i < SLOTS; i++) if (slots[i] != null) sl.putString(String.valueOf(i), slots[i]);
        t.put("slots", sl);
        CompoundTag pa = new CompoundTag();
        passives.forEach(pa::putInt);
        t.put("passives", pa);
        return t;
    }

    public void load(CompoundTag t) {
        level = t.getInt("level"); exp = t.getLong("exp");
        int[] s = t.getIntArray("stats");
        for (int i = 0; i < 5 && i < s.length; i++) stats[i] = s[i];
        statPoints = t.getInt("sp"); job = t.getInt("job"); gold = t.getLong("gold");
        hp = t.getDouble("hp"); mp = t.getDouble("mp");
        skillPoints = t.getInt("skp"); passivePoints = t.getInt("pp");
        skills.clear();
        CompoundTag sk = t.getCompound("skills");
        for (String k : sk.getAllKeys()) skills.put(k, sk.getInt(k));
        java.util.Arrays.fill(slots, null);
        CompoundTag sl = t.getCompound("slots");
        for (int i = 0; i < SLOTS; i++) if (sl.contains(String.valueOf(i))) slots[i] = sl.getString(String.valueOf(i));
        passives.clear();
        CompoundTag pa = t.getCompound("passives");
        for (String k : pa.getAllKeys()) passives.put(k, pa.getInt(k));
    }

    public void copyFrom(PlayerData o) {
        load(o.save());
        maxHp = o.maxHp; maxMp = o.maxMp;
    }

    // ── 동기화 (서버 → 본인) ──
    public void write(FriendlyByteBuf b, double atkMax, double defPct, long now) {
        b.writeVarInt(level); b.writeLong(exp); b.writeLong(Balance.expNeed(level));
        for (int v : stats) b.writeVarInt(v);
        b.writeVarInt(statPoints); b.writeVarInt(job); b.writeLong(gold);
        b.writeDouble(hp); b.writeDouble(mp); b.writeDouble(maxHp); b.writeDouble(maxMp);
        b.writeDouble(crit()); b.writeDouble(healBonus()); b.writeDouble(dropBonus()); b.writeDouble(goldBonus());
        b.writeDouble(atkMax * Balance.mastery); b.writeDouble(atkMax); b.writeDouble(defPct);
        b.writeDouble(critDamage() * 100.0);
        // 스킬
        b.writeVarInt(skillPoints); b.writeVarInt(passivePoints);
        b.writeVarInt(skills.size());
        skills.forEach((k, v) -> { b.writeUtf(k); b.writeVarInt(v); });
        for (int i = 0; i < SLOTS; i++) b.writeUtf(slots[i] == null ? "" : slots[i]);
        b.writeVarInt(passives.size());
        passives.forEach((k, v) -> { b.writeUtf(k); b.writeVarInt(v); });
        // 슬롯별 남은 쿨타임 / 전체 (틱)
        for (int i = 0; i < SLOTS; i++) {
            String id = slots[i];
            long left = id == null ? 0 : Math.max(0, cdEnd.getOrDefault(id, 0L) - now);
            b.writeVarInt((int) left);
            b.writeVarInt(id == null ? 0 : cdLen.getOrDefault(id, 0));
        }
        b.writeVarInt((int) Math.max(0, dodgeEnd - now));
        b.writeVarInt(dodgeLen);
        b.writeVarInt((int) Math.max(0, sprintEnd - now));
    }
}
