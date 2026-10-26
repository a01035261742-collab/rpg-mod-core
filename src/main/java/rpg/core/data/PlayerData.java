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
 * 스탯 순서: 0 체력(VIT) · 1 힘(STR) · 2 민첩(DEX) · 3 지력(INT) · 4 운(LUK) · 5 스태미나(STA)
 */
public class PlayerData {
    public static final int VIT = 0, STR = 1, DEX = 2, INT = 3, LUK = 4, STA = 5, N = 6;
    public static final String[] STAT_NAMES = {"체력", "힘", "민첩", "지력", "운", "스태미나"};
    public static final int SLOTS = 6;

    public int level;
    public long exp;
    public final int[] stats = new int[N];
    public int statPoints;
    public int job;          // 0 초보자, 1 전사 2 궁수 3 마법사 4 도적
    /** 2차 전직 갈래 (0 = 아직, 1·2 = 계열 안 갈래) */
    public int branch;
    /** 고른 갈래 (시련 중, 0 = 안 고름) · 교관의 시련 통과했나 */
    public int pendingBranch;
    public boolean trialCleared;
    public long gold;
    public double hp, mp;

    // 스킬·패시브
    public int skillPoints, passivePoints;
    public final Map<String, Integer> skills = new LinkedHashMap<>();   // 스킬 id → 레벨
    public final String[] slots = new String[SLOTS];                    // Q E R F Z X 에 들어간 스킬 id
    public final Map<String, Integer> passives = new HashMap<>();       // 노드 id → 랭크

    // 퀘스트: 진행 중 (id → 목표별 처치 수) · 완료한 것
    public final Map<String, int[]> questActive = new LinkedHashMap<>();
    public final java.util.Set<String> questDone = new java.util.LinkedHashSet<>();

    // 쿨타임·버프 (저장 안 함, 게임 시간 틱)
    public final Map<String, Long> cdEnd = new HashMap<>();
    public final Map<String, Integer> cdLen = new HashMap<>();
    public long dodgeEnd;
    public int dodgeLen;
    /** 버프: 스킬 id → 끝나는 틱, 값 */
    public final Map<String, Long> buffEnd = new HashMap<>();
    public final Map<String, Double> buffVal = new HashMap<>(), buffVal2 = new HashMap<>();
    /** 차지 중인 스킬 (강타 차지) */
    public String chargeSkill;
    public long chargeStart;
    public int chargeSlot = -1;

    public boolean buff(String id, long now) { return now < buffEnd.getOrDefault(id, 0L); }
    public double buffV(String id, long now) { return buff(id, now) ? buffVal.getOrDefault(id, 0.0) : 0; }
    public double buffV2(String id, long now) { return buff(id, now) ? buffVal2.getOrDefault(id, 0.0) : 0; }

    /** 서버 시간 (매 틱 갱신, 버프 계산용) */
    public long clock;

    public long sprintEnd;
    public double sprintPct;
    /** 보호막 (넘치는 치유): 남은 양, 끝나는 틱 */
    public double shield;
    public long shieldEnd;
    /** 광폭화 연장된 시간 (초, 연속 분노 최대치 계산용) */
    public double berserkExt;
    /** 조준점 (클라 화면 가운데가 가리키는 곳, 스킬 쓸 때 받음) */
    public net.minecraft.world.phys.Vec3 aim;
    public long aimTick;
    public long lastRoll;
    /** 스킬 쓸 때 누르고 있던 방향키 (월드 방향, 0 = 안 누름) */
    public double moveX, moveZ;

    // 다운·부활 (저장 안 함)
    public long downEnd;              // 0 = 다운 아님
    public java.util.UUID reviver;
    public long reviveStart;
    public int reviveNeed;
    public boolean forceDie;

    public boolean isDown() { return downEnd > 0; }

    // 계산값 (저장 안 함)
    public double maxHp = 1, maxMp = 1;
    public final Map<String, Double> bonus = new HashMap<>();   // 패시브 합계 (stat → 값)
    public final Map<String, Double> equip = new HashMap<>();   // 장비 합계 (str dex int luk vit atk matk hp mp def allPct)

    private static final String[] STAT_KEYS = {"vit", "str", "dex", "int", "luk", "sta"};
    /** 장비 합계에서 bonus 로 옮기는 % 옵션 (잠재능력) */
    private static final String[] EQUIP_BONUS = {"atkPct", "dmgPct", "hpPct", "critChance", "goldPct", "dropPct", "speedPct", "bossPct", "critDmg", "cdPct"};
    /** 패시브 트리 상태 (저장 안 함) */
    public transient double atkSpeedPct, warcryVal, oathVal;
    public transient long crisisReadyAt, warcryUntil, oathUntil;
    public transient boolean guardNear;
    public transient int tauntCount, bleedNear;
    public transient boolean quickShotReady, moving, loneOk;
    public transient int stillStacks;
    public transient long aimUntil, windVeilReadyAt;
    public transient double lastX, lastZ, baseMp, holyVal;
    public transient long lastAttack, holyUntil, escapeReadyAt, lifestealSec;
    public transient double lifestealUsed;
    /** 피격 시 확률 무적 (잠재능력): 이 시각까지 피해 없음 */
    public transient long invulUntil;
    /** 연속 베기: 다음 타 (0~2), 이어서 누를 수 있는 시각 */
    public transient int comboStep;
    public transient long comboUntil;
    /** 마지막으로 나를 때린 것 (사망 메시지용) */
    public transient String lastHitBy = "";
    public transient long lastHitAt;

    public double eq(String k) {
        return equip.getOrDefault(k, 0.0);
    }

    /**
     * 최종 스탯 = (찍은 스탯 + 장비) × (1 + 올스탯% + 힘% 등) + % 미적용 스탯 (메이플식).
     * % 미적용: 잠재 9레벨당 스탯 + 장비/패시브 "strFlat" 등 (나중에 심볼·하이퍼 같은 시스템용)
     */
    public double total(int i) {
        String k = STAT_KEYS[i];
        double perLv = eq(k + "PerLv") * Math.floor(level / 9.0);   // 잠재능력: 캐릭터 9레벨당 스탯
        double flat = perLv + eq(k + "Flat") + bonus(k + "Flat");
        return (stats[i] + eq(k) + bonus(k)) * (1 + (eq("allPct") + eq(k + "Pct") + bonus(k + "Pct")) / 100.0) + flat;   // bonus(k) = 패시브 트리 힘 +3 등
    }

    public boolean isNew() { return level <= 0; }

    public int jobTier() {
        return job == 0 ? 0 : branch > 0 ? 2 : 1;
    }

    /** 처음 시작 */
    public void init() {
        level = 1; exp = 0; job = 0; branch = 0; pendingBranch = 0; trialCleared = false; gold = 0; statPoints = 0; skillPoints = 0; passivePoints = 0;
        for (int i = 0; i < N; i++) stats[i] = start(i);
        skills.clear(); passives.clear(); cdEnd.clear(); cdLen.clear();
        questActive.clear(); questDone.clear();
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
            if (n == null || !Defs.inTree(n, job, branch)) continue;   // 지금 트리 칸만
            if (!n.stat().isEmpty()) bonus.merge(n.stat(), n.perRank() * e.getValue(), Double::sum);
            if (!n.stat2().isEmpty()) bonus.merge(n.stat2(), n.perRank2() * e.getValue(), Double::sum);
        }
        // 스킬 창 패시브 (배운 레벨만큼 항상 켜짐)
        for (var e : skills.entrySet()) {
            Defs.SkillDef sd = Defs.SKILLS.get(e.getKey());
            if (sd == null || !sd.passive() || e.getValue() <= 0 || !rpg.core.skill.Skills.available(this, sd)) continue;
            if (!sd.stat().isEmpty()) bonus.merge(sd.stat(), sd.value(e.getValue()), Double::sum);
            if (!sd.stat2().isEmpty()) bonus.merge(sd.stat2(), sd.value2(e.getValue()), Double::sum);
        }
        // 장비(잠재능력)의 % 옵션을 패시브 합계에 더함
        for (String k : EQUIP_BONUS) { double v = eq(k); if (v != 0) bonus.merge(k, v, Double::sum); }
        double vitHp = Balance.hpPerVit * total(VIT) * (1 + bonus("vitHpPct") / 100.0);   // 단단한 피부
        double mh = (Balance.hpBase + Balance.hpPerLevel * level + vitHp + eq("hp")) * (1 + bonus("hpPct") / 100.0)
                * (1 + bonus("giantHp") / 100.0)    // 거신: 최대 HP ×1.3
                * (oathVal > 0 ? 1.05 : 1)           // 수호자의 맹세: +5%
                * (1 + holyVal / 100.0);             // 신성 영역
        baseMp = Balance.mpBase + Balance.mpPerLevelByJob[Math.max(0, Math.min(4, job))] * level + Balance.mpPerInt * total(INT);   // 마력 과부하: 이걸 넘는 MP = 추가 MP
        double mm = (baseMp + eq("mp")) * (1 + bonus("mpPct") / 100.0);
        boolean changed = mh != maxHp || mm != maxMp;
        maxHp = Math.max(1, mh); maxMp = Math.max(0, mm);
        if (hp > maxHp) { hp = maxHp; changed = true; }
        if (mp > maxMp) { mp = maxMp; changed = true; }
        return changed;
    }

    /** 직업 개편: 스킬 포인트 전부 돌려받고 스킬 칸 비우기 */
    public void refundSkills() {
        int sp = 0;
        for (int v : skills.values()) sp += v;
        skills.clear();
        for (int i = 0; i < slots.length; i++) slots[i] = null;
        skillPoints += sp;
        recalc();
    }

    /** 찍은 스탯을 전부 돌려받기 (초기화 주문서) */
    public int resetStats() {
        int refund = 0;
        for (int i = 0; i < N; i++) {
            refund += Math.max(0, stats[i] - start(i));
            stats[i] = start(i);
        }
        statPoints += refund;
        recalc();
        return refund;
    }

    // ── 상세 능력치 (공식은 balance 파일) ──
    public double crit() { return Balance.critBase + Balance.critPerDex * total(DEX) + bonus("critChance") + buffV("focus", clock) + rpg.core.skill.PassiveEffects.extraCrit(this); }
    public double critDamage() { return Balance.critDamage + (bonus("critDmg") + rpg.core.skill.PassiveEffects.extraCritDmg(this)) / 100.0; }
    public double healBonus() { return bonus("healPct"); }   // 회복량 증가 (패시브·전사의 회복력). 지력은 이제 힐 스공으로 반영
    public double dropBonus() { return Balance.dropPerLuk * total(LUK) + bonus("dropPct") + buffV("plunder", clock); }
    public double goldBonus() { return Balance.goldPerLuk * total(LUK) + bonus("goldPct") + buffV("plunder", clock); }
    /** Epic Fight 최대 스태미나에 더해지는 양 (스태미나 스탯 + 패시브) */
    public double staminaBonus() { return Balance.staminaPerPoint * total(STA) + bonus("stamina"); }

    /** 시작 스탯 (스태미나는 따로) */
    public static int start(int i) { return i == STA ? Balance.startStamina : Balance.startStat; }

    // ── 저장 ──
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("level", level); t.putLong("exp", exp); t.putIntArray("stats", stats.clone());
        t.putInt("sp", statPoints); t.putInt("job", job); t.putLong("gold", gold);
        t.putInt("branch", branch); t.putInt("pendingBranch", pendingBranch); t.putBoolean("trialCleared", trialCleared);
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
        CompoundTag qa = new CompoundTag();
        questActive.forEach((k, v) -> qa.putIntArray(k, v.clone()));
        t.put("questActive", qa);
        net.minecraft.nbt.ListTag qd = new net.minecraft.nbt.ListTag();
        for (String k : questDone) qd.add(net.minecraft.nbt.StringTag.valueOf(k));
        t.put("questDone", qd);
        return t;
    }

    public void load(CompoundTag t) {
        level = t.getInt("level"); exp = t.getLong("exp");
        int[] s = t.getIntArray("stats");
        for (int i = 0; i < N; i++) stats[i] = i < s.length ? s[i] : start(i);
        statPoints = t.getInt("sp"); job = t.getInt("job"); gold = t.getLong("gold");
        branch = t.getInt("branch"); pendingBranch = t.getInt("pendingBranch"); trialCleared = t.getBoolean("trialCleared");
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
        questActive.clear();
        CompoundTag qa = t.getCompound("questActive");
        for (String k : qa.getAllKeys()) questActive.put(k, qa.getIntArray(k));
        questDone.clear();
        net.minecraft.nbt.ListTag qd = t.getList("questDone", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < qd.size(); i++) questDone.add(qd.getString(i));
    }

    public void copyFrom(PlayerData o) {
        load(o.save());
        maxHp = o.maxHp; maxMp = o.maxMp;
    }

    // ── 동기화 (서버 → 본인) ──
    public void write(FriendlyByteBuf b, double atkMax, double defPct, long now) {
        b.writeVarInt(level); b.writeLong(exp); b.writeLong(Balance.expNeed(level));
        for (int v : stats) b.writeVarInt(v);
        for (int i = 0; i < N; i++) b.writeVarInt((int) Math.round(total(i) - stats[i]));
        b.writeVarInt(statPoints); b.writeVarInt(job); b.writeLong(gold);
        b.writeDouble(hp); b.writeDouble(mp); b.writeDouble(maxHp); b.writeDouble(maxMp);
        b.writeDouble(crit()); b.writeDouble(healBonus()); b.writeDouble(dropBonus()); b.writeDouble(goldBonus());
        b.writeDouble(atkMax * rpg.core.combat.Combat.mastery(this)); b.writeDouble(atkMax); b.writeDouble(defPct);
        b.writeDouble(critDamage() * 100.0);
        b.writeDouble(staminaBonus());
        b.writeDouble(rpg.core.combat.Combat.ignoreDef(this)); b.writeDouble(bonus("bossPct") + rpg.core.skill.PassiveEffects.extraBoss(this));
        b.writeDouble(rpg.core.skill.PassiveEffects.overload(this));
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
        b.writeVarInt(isDown() ? (int) Math.max(0, downEnd - now) : -1);
        b.writeVarInt(reviver != null && reviveNeed > 0 ? (int) Math.min(100, (now - reviveStart) * 100 / reviveNeed) : -1);
        // 버프 표시 (이름, 남은 틱)
        java.util.List<String> names = new java.util.ArrayList<>();
        java.util.List<Integer> lefts = new java.util.ArrayList<>();
        if (now < sprintEnd) { names.add("질주"); lefts.add((int) (sprintEnd - now)); }
        if (now < shieldEnd && shield > 0) { names.add("보호막"); lefts.add(shieldEnd == Long.MAX_VALUE ? 1_000_000_000 : (int) (shieldEnd - now)); }
        buffEnd.forEach((id, end) -> {
            if (now < end) {
                var def = Defs.SKILLS.get(id);
                names.add(def == null ? id : def.name());
                lefts.add((int) (end - now));
            }
        });
        b.writeVarInt(names.size());
        for (int i = 0; i < names.size(); i++) { b.writeUtf(names.get(i)); b.writeVarInt(lefts.get(i)); }
        b.writeBoolean(chargeSkill != null);
        b.writeVarInt(chargeSkill != null ? (int) (now - chargeStart) : 0);
        b.writeVarInt(branch); b.writeVarInt(pendingBranch); b.writeBoolean(trialCleared);
        b.writeDouble(now < shieldEnd ? shield : 0);
    }
}
