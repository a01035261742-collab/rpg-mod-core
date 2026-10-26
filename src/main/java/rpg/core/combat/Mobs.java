package rpg.core.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Slime;
import rpg.core.balance.Balance;
import rpg.core.net.Net;

/** RPG 몹 만들기 */
public final class Mobs {
    private Mobs() {}

    /** mobs.json 의 key 몹을 level 로 pos 에 소환. 실패하면 null */
    public static Mob spawn(ServerLevel level, String key, int lv, BlockPos pos, String zone) {
        return spawn(level, key, lv, pos, zone, level.random.nextDouble() * 100 < Balance.eliteChance);
    }

    public static Mob spawn(ServerLevel level, String key, int lv, BlockPos pos, String zone, boolean elite) {
        Balance.MobDef def = Balance.MOBS.get(key);
        if (def == null || !rpg.core.world.RpgWorld.is(level)) return null;   // RPG 몹은 RPG 대륙에서만
        ResourceLocation id = ResourceLocation.tryParse(def.entity());
        if (id == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.SPAWNER, null, null);
        if (mob instanceof Slime slime) slime.setSize(2, true);
        if (mob instanceof net.minecraft.world.entity.monster.Zombie z) z.setBaby(false);   // 아기 좀비 없음
        if (mob instanceof net.minecraft.world.entity.AgeableMob am) am.setAge(def.look().baby() ? -24000 : 0);
        Entity ride = mob.getVehicle();                                                      // 치킨 조키 등 없음
        if (ride != null) { mob.stopRiding(); ride.discard(); }
        for (Entity pass : java.util.List.copyOf(mob.getPassengers())) { pass.stopRiding(); pass.discard(); }  // 스파이더 조키 없음
        tame(mob);
        mob.setPersistenceRequired();
        look(mob, def);
        apply(mob, def, lv, zone);
        if (elite) makeElite(mob);
        level.addFreshEntity(mob);
        return mob;
    }

    /** 바닐라 행동 끄기: 아이템 줍기, 갑옷, 좀비 지원군 */
    public static void tame(Mob mob) {
        mob.setCanPickUpLoot(false);
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) mob.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            mob.setDropChance(slot, 0f);
        }
        var reinf = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (reinf != null) reinf.setBaseValue(0);
    }

    /** 모양·행동: 머리 아이템, 호글린 변신 막기, 닭 알 안 낳음, 벡스 수명 없음, AI 설정 */
    public static void look(Mob mob, Balance.MobDef def) {
        Balance.MobLook lk = def.look();
        if (!lk.head().isEmpty()) {
            var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(lk.head()));
            if (it != null) mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(it));
        }
        if (mob instanceof net.minecraft.world.entity.monster.hoglin.Hoglin h) h.setImmuneToZombification(true);
        if (mob instanceof net.minecraft.world.entity.animal.Chicken c) c.eggTime = Integer.MAX_VALUE;
        MobAI.setup(mob, def);
    }

    /** 엘리트: 체력·공격·보상 배수, 금색 이름, 빛남 */
    public static void makeElite(Mob mob) {
        MobData m = Combat.mob(mob);
        if (m == null) return;
        m.elite = true;
        m.defRate = Math.max(m.defRate, 20);
        m.maxHp *= Balance.eliteHp; m.hp = m.maxHp;
        m.atk *= Balance.eliteAtk;
        m.exp = Math.round(m.exp * Balance.eliteReward);
        m.gold = Math.round(m.gold * Balance.eliteGoldMul);
        m.name = "★" + m.name;
        mob.setGlowingTag(true);
    }

    public static void apply(Mob mob, Balance.MobDef def, int lv, String zone) {
        MobData m = Combat.mob(mob);
        if (m == null) return;
        Balance.MobStats s = def.at(lv);
        m.key = def.key(); m.name = def.name(); m.zone = zone == null ? "" : zone;
        m.level = lv; m.maxHp = Math.max(1, s.hp()); m.hp = m.maxHp; m.atk = s.atk(); m.exp = s.exp(); m.gold = s.gold();
        Double dr = Balance.MOB_DEF.get(def.key());
        m.defRate = dr != null ? dr : Boss.isBoss(m) ? 30 : Math.min(10, lv / 5.0);
        mob.setHealth(mob.getMaxHealth());
        // 넉백 90% 줄임 (Epic Fight 콤보가 안 끊기게)
        var kb = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(Math.max(kb.getBaseValue(), 0.9));
    }

    /** 근처 플레이어들에게 이 몹의 레벨·체력 알리기 */
    public static void sync(Mob mob) {
        MobData m = Combat.mob(mob);
        if (m != null && m.isRpg()) Net.mobInfo(mob, m);
    }
}
