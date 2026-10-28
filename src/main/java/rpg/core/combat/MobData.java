package rpg.core.combat;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 몹 RPG 데이터 (level 0 = RPG 몹 아님) */
public class MobData {
    public String key = "";      // mobs.json 의 키
    public String name = "";
    public String zone = "";     // 소속 사냥터 이름
    public transient boolean returning;   // 구역으로 돌아가는 중 (무적 · 안 노림)
    public transient long returnSince;
    public int level;
    public double hp, maxHp, atk;
    public long exp, gold;
    public boolean elite;

    // 패턴 (저장 안 함)
    public transient final long[] skillReady = new long[8];
    public transient boolean aiReady;            // 근접 공격·대상 AI 설정 끝남 (다시 불러와도 한 번만)
    public transient int casting = -1;          // 쓰는 중인 패턴 번호
    public transient long castEnd;
    public transient boolean castAnim;          // 패턴 애니메이션 시작했는지
    public transient net.minecraft.world.phys.Vec3 castPos, castDir;
    public transient net.minecraft.world.phys.Vec3 leapFrom;   // 도약 포물선 시작점 (null = 안 뛰는 중)
    public transient long leapStart;
    public transient int hitsLeft;
    // 경직 (저장 안 함)
    public transient long stunUntil;            // 이 시각까지 경직
    public transient long stunWindow;           // 연속 경직 세기 시작 시각
    public transient int stunCount;             // 창 안 경직 횟수
    public transient long stunImmuneUntil;      // 연속 경직 후 면역
    public transient boolean stunCancel;        // 경직이 공격을 끊어야 함 (근접 goal 이 처리)
    public transient net.minecraft.world.phys.Vec3[] castExtra;   // 여러 장판 패턴의 추가 위치
    // 도발 (저장 안 함): 이 플레이어만 노림
    public transient java.util.UUID tauntBy;
    public transient long tauntUntil;
    /** 방어율 % (방무로 깎임) */
    public double defRate;
    /** 분신술: 이 시각까지 이 자리를 노림 */
    public transient long decoyUntil;
    public transient net.minecraft.world.phys.Vec3 decoyPos;
    // 둔화·속박 (덫·독안개): 이동속도 -slowPct% (100 = 못 움직임)
    public transient double slowPct;
    public transient long slowUntil;

    /** 둔화 걸기 (지금 걸린 것보다 센 값·긴 시간 유지) */
    public void slow(double pct, long now, long until) {
        if (now >= slowUntil) slowPct = 0;
        slowPct = Math.max(slowPct, pct);
        slowUntil = Math.max(slowUntil, until);
    }

    public boolean isRpg() { return level > 0; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("key", key); t.putString("name", name); t.putString("zone", zone);
        t.putInt("level", level); t.putDouble("hp", hp); t.putDouble("maxHp", maxHp); t.putDouble("atk", atk);
        t.putLong("exp", exp); t.putLong("gold", gold);
        t.putBoolean("elite", elite);
        t.putDouble("defRate", defRate);
        return t;
    }

    public void load(CompoundTag t) {
        key = t.getString("key"); name = t.getString("name"); zone = t.getString("zone");
        level = t.getInt("level"); hp = t.getDouble("hp"); maxHp = t.getDouble("maxHp"); atk = t.getDouble("atk");
        exp = t.getLong("exp"); gold = t.getLong("gold");
        elite = t.getBoolean("elite");
        defRate = t.contains("defRate") ? t.getDouble("defRate") : rpg.core.combat.Boss.isBoss(this) ? 30 : Math.min(10, level / 5.0);
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        public static final Capability<MobData> CAP = CapabilityManager.get(new CapabilityToken<>() {});
        private final MobData data = new MobData();
        private final LazyOptional<MobData> opt = LazyOptional.of(() -> data);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? opt.cast() : LazyOptional.empty();
        }

        @Override public CompoundTag serializeNBT() { return data.save(); }
        @Override public void deserializeNBT(CompoundTag nbt) { data.load(nbt); }
    }
}
