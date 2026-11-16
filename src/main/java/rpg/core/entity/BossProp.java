package rpg.core.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 보스 패턴 연출용 소품 (판정 없음, 보여주기만). 서버가 위치·수명을 정함.
 * - boss_rock: from → to 포물선으로 날아감 (start 틱 전에는 안 보임), land 틱에 사라짐
 * - rock_pillar: 땅에서 솟았다가 (rise) 수명 끝에 가라앉음 (sink)
 * - void_crystal: 박히며 커졌다가 (grow) 수명 끝에 부서짐 (shatter). solid 면 플레이어가 못 지나감
 */
public class BossProp extends Entity implements GeoEntity {
    private static final EntityDataAccessor<Boolean> VISIBLE = SynchedEntityData.defineId(BossProp.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ENDING = SynchedEntityData.defineId(BossProp.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SOLID = SynchedEntityData.defineId(BossProp.class, EntityDataSerializers.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 from, to;
    private long start, land;
    private int life = 20, endTicks = 10;

    public BossProp(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override protected void defineSynchedData() {
        entityData.define(VISIBLE, true);
        entityData.define(ENDING, false);
        entityData.define(SOLID, false);
    }

    public boolean visible() { return entityData.get(VISIBLE); }

    /** 던지는 바위: start 틱부터 land 틱까지 포물선 */
    public static void rock(ServerLevel l, Vec3 from, Vec3 to, long start, long land) {
        BossProp e = new BossProp(ModEntities.BOSS_ROCK.get(), l);
        e.from = from; e.to = to; e.start = start; e.land = Math.max(start + 2, land);
        e.setPos(from);
        e.entityData.set(VISIBLE, l.getGameTime() >= start);
        l.addFreshEntity(e);
    }

    /** 바닥 높이에 맞춤: pos 위 2칸부터 아래로 내려가며 처음 만나는 단단한 블록 윗면 (없으면 그대로) */
    static Vec3 ground(ServerLevel l, Vec3 pos) {
        net.minecraft.core.BlockPos.MutableBlockPos b = new net.minecraft.core.BlockPos.MutableBlockPos();
        int x = net.minecraft.util.Mth.floor(pos.x), z = net.minecraft.util.Mth.floor(pos.z), y0 = net.minecraft.util.Mth.floor(pos.y + 2);
        for (int y = y0; y > y0 - 10; y--) {
            b.set(x, y, z);
            var shape = l.getBlockState(b).getCollisionShape(l, b);
            if (!shape.isEmpty()) return new Vec3(pos.x, y + shape.max(net.minecraft.core.Direction.Axis.Y), pos.z);
        }
        return pos;
    }

    /** 바위 기둥: life 틱 동안 서 있음 */
    public static void pillar(ServerLevel l, Vec3 pos, int life) {
        BossProp e = new BossProp(ModEntities.ROCK_PILLAR.get(), l);
        e.life = Math.max(life, 16); e.endTicks = 12;
        e.setPos(ground(l, pos));
        e.setYRot(l.random.nextFloat() * 360);
        l.addFreshEntity(e);
    }

    /** 공허 결정: life 틱 동안, solid 면 장애물 */
    public static void crystal(ServerLevel l, Vec3 pos, int life, boolean solid) {
        BossProp e = new BossProp(ModEntities.VOID_CRYSTAL.get(), l);
        e.life = Math.max(life, 14); e.endTicks = 10;
        e.entityData.set(SOLID, solid);
        e.setPos(ground(l, pos));
        e.setYRot(l.random.nextFloat() * 360);
        l.addFreshEntity(e);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel l)) return;
        if (from != null) {   // 날아가는 바위
            long now = l.getGameTime();
            if (now < start) { setPos(from); return; }
            if (!visible()) entityData.set(VISIBLE, true);
            double f = Math.min(1, (now - start) / (double) (land - start));
            double h = 2 + from.distanceTo(to) * 0.25;
            Vec3 q = from.lerp(to, f).add(0, 4 * h * f * (1 - f), 0);
            setPos(q.x, q.y, q.z);
            setYRot(getYRot() + 18); setXRot(getXRot() + 11);
            if (now >= land) {
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MOSSY_COBBLESTONE.defaultBlockState()), q.x, q.y + 0.3, q.z, 30, 0.6, 0.3, 0.6, 0.1);
                discard();
            }
            return;
        }
        if (tickCount == 1) {
            boolean cry = getType() == ModEntities.VOID_CRYSTAL.get();
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, cry ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState()),
                    getX(), getY() + 0.3, getZ(), 24, 0.8, 0.2, 0.8, 0.1);
            l.playSound(null, blockPosition(), cry ? SoundEvents.AMETHYST_CLUSTER_PLACE : SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.5f, cry ? 0.7f : 0.6f);
        }
        if (tickCount == life - endTicks) {
            entityData.set(ENDING, true);
            if (getType() == ModEntities.VOID_CRYSTAL.get())
                l.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.2f, 0.8f);
        }
        if (tickCount >= life) discard();
    }

    @Override public boolean canBeCollidedWith() { return entityData.get(SOLID) && isAlive(); }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}

    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("spin");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 0, s -> {
            EntityType<?> t = getType();
            if (t == ModEntities.BOSS_ROCK.get()) return s.setAndContinue(SPIN);
            boolean end = entityData.get(ENDING);
            String a = t == ModEntities.ROCK_PILLAR.get() ? (end ? "sink" : "rise") : (end ? "shatter" : "grow");
            return s.setAndContinue(RawAnimation.begin().thenPlayAndHold(a));
        }));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
