package rpg.core.equip;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import rpg.core.combat.Combat;
import rpg.core.data.PlayerData;
import rpg.core.data.RpgData;

/**
 * 전투력 (메이플식, 공격만): 스공 × (1 + 보스%) × (1 + 크리확률 × (크리뎀 - 1)).
 * 전투력 = 공격력×(1+공%) 스탯 공식 × (1 + 데미지% + 보스%) × 크리 기대값. 버프는 빼고 계산.
 */
public final class Power {
    private Power() {}

    private static long calc(ServerPlayer p, PlayerData d) {
        double base = (Combat.mainStat(d) * 4.0 + Combat.subStat(d)) * Combat.weaponAtk(p, d) * (1 + d.bonus("atkPct") / 100.0) / 100.0;
        double crit = Math.max(0, Math.min(100, d.crit())) / 100.0;
        double ign = (1 - 0.3 * (1 - Combat.ignoreDef(d) / 100.0)) / 0.7;   // 방무: 방어율 30% 몹 기준 (방무 0이면 1배)
        return Math.round(base * (1 + (d.bonus("dmgPct") + d.bonus("bossPct")) / 100.0) * (1 + crit * (d.critDamage() - 1)) * ign);
    }

    /** 지금 장비 그대로 */
    public static long of(ServerPlayer p) {
        PlayerData d = RpgData.get(p);
        Map<String, Double> keep = new HashMap<>(d.equip);
        double hp = d.hp, mp = d.mp;
        d.equip.clear();
        d.equip.putAll(PlayerEquip.get(p).sum(p));
        d.recalc();
        long v = calc(p, d);
        d.equip.clear();
        d.equip.putAll(keep);
        d.recalc();
        d.hp = Math.min(hp, d.maxHp); d.mp = Math.min(mp, d.maxMp);
        return v;
    }

    /**
     * 장비 s 의 NBT 칸 key 를 alt 로 바꿨을 때 전투력 차이.
     * 무기가 손에 없으면 (핫바 다른 칸·인벤) 잠깐 손에 든 걸로 바꿔서 계산하고 바로 되돌림.
     * 무기 말고 안 낀 장비는 0 (착용해야 계산됨).
     */
    public static long delta(ServerPlayer p, ItemStack s, String key, CompoundTag alt) {
        var inv = p.getInventory();
        int sel = inv.selected, at = -1;
        ItemStack held = inv.items.get(sel);
        boolean swap = "weapon".equals(Gear.slotOf(s)) && held != s;
        if (swap) {
            for (int i = 0; i < inv.items.size(); i++) if (inv.items.get(i) == s) { at = i; break; }
            if (at < 0) swap = false;
        }
        if (swap) { inv.items.set(sel, s); inv.items.set(at, held); }
        try {
            long now = of(p);
            CompoundTag tag = s.getOrCreateTag();
            CompoundTag old = tag.contains(key) ? tag.getCompound(key).copy() : null;
            tag.put(key, alt);
            long after = of(p);
            if (old != null) tag.put(key, old); else tag.remove(key);
            return after - now;
        } finally {
            if (swap) { inv.items.set(at, s); inv.items.set(sel, held); }
        }
    }
}
