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
    public int level;
    public double hp, maxHp, atk;
    public long exp, gold;
    public boolean elite;

    // 패턴 (저장 안 함)
    public transient final long[] skillReady = new long[8];
    public transient int casting = -1;          // 쓰는 중인 패턴 번호
    public transient long castEnd;
    public transient boolean castAnim;          // 패턴 애니메이션 시작했는지
    public transient net.minecraft.world.phys.Vec3 castPos, castDir;
    public transient int hitsLeft;

    public boolean isRpg() { return level > 0; }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("key", key); t.putString("name", name); t.putString("zone", zone);
        t.putInt("level", level); t.putDouble("hp", hp); t.putDouble("maxHp", maxHp); t.putDouble("atk", atk);
        t.putLong("exp", exp); t.putLong("gold", gold);
        t.putBoolean("elite", elite);
        return t;
    }

    public void load(CompoundTag t) {
        key = t.getString("key"); name = t.getString("name"); zone = t.getString("zone");
        level = t.getInt("level"); hp = t.getDouble("hp"); maxHp = t.getDouble("maxHp"); atk = t.getDouble("atk");
        exp = t.getLong("exp"); gold = t.getLong("gold");
        elite = t.getBoolean("elite");
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
