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

    /** 트리 칸 하나 더 찍으면 전투력 차이 (찍을 수 없는 칸이면 0) */
    public static long nodeDelta(ServerPlayer p, String nodeId) {
        PlayerData d = RpgData.get(p);
        var n = rpg.core.skill.Defs.NODES.get(nodeId);
        if (n == null || !rpg.core.skill.Defs.inTree(n, d.job, d.branch)) return 0;
        int cur = d.passives.getOrDefault(nodeId, 0);
        if (cur >= n.maxRank()) return 0;
        long now = of(p);
        d.passives.put(nodeId, cur + 1);
        long after;
        try { after = of(p); }
        finally { if (cur == 0) d.passives.remove(nodeId); else d.passives.put(nodeId, cur); d.recalc(); }
        return after - now;
    }

    /** 툴팁 비교 결과: state 0 = 비교값, 1 = 착용 중, 2 = 착용 불가(why) */
    public record Cmp(long delta, int state, String why) {}

    /**
     * 툴팁용: 이 장비를 같은 부위에 끼면 전투력이 얼마나 바뀌나 (칸이 여러 개면 제일 이득인 칸).
     * 계산 동안만 잠깐 바꿔 끼고 바로 되돌림.
     */
    public static Cmp compare(ServerPlayer p, ItemStack s) {
        GearDefs.GearDef d = Gear.def(s);
        if (d == null) return new Cmp(0, 2, "");
        if (!Gear.canEquip(p, s)) {
            int need = d.level() - Flame.reqLv(s);
            String why = Enhance.broken(s) ? "파괴된 장비" : RpgData.get(p).level < need ? "Lv." + need + "부터 착용"
                    : (switch (d.job()) { case 1 -> "전사"; case 2 -> "궁수"; case 3 -> "마법사"; case 4 -> "도적"; default -> ""; }) + " 전용";
            return new Cmp(0, 2, why);
        }
        String slot = d.slot();
        var inv = p.getInventory();
        long now = of(p), best = Long.MIN_VALUE;
        if ("weapon".equals(slot)) {
            int sel = inv.selected;
            ItemStack held = inv.items.get(sel);
            if (ItemStack.matches(held, s)) return new Cmp(0, 1, "");
            inv.items.set(sel, s.copy());
            try { best = of(p) - now; } finally { inv.items.set(sel, held); }
            return new Cmp(best, 0, "");
        }
        net.minecraft.world.entity.EquipmentSlot es = switch (slot) {
            case "head", "helmet", "hat" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chest", "top", "armor" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "legs", "bottom", "pants" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "feet", "shoes", "boots" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            default -> null;
        };
        if (es != null) {
            int ai = es.getIndex();
            ItemStack cur = inv.armor.get(ai);
            if (ItemStack.matches(cur, s)) return new Cmp(0, 1, "");
            inv.armor.set(ai, s.copy());
            try { best = of(p) - now; } finally { inv.armor.set(ai, cur); }
            return new Cmp(best, 0, "");
        }
        PlayerEquip pe = PlayerEquip.get(p);
        for (int i = 1; i < PlayerEquip.SLOT_TYPES.length; i++) {
            if (!PlayerEquip.SLOT_TYPES[i].equals(slot)) continue;
            ItemStack cur = pe.items.getStackInSlot(i);
            if (ItemStack.matches(cur, s)) return new Cmp(0, 1, "");
            pe.items.setStackInSlot(i, s.copy());
            try { best = Math.max(best, of(p) - now); } finally { pe.items.setStackInSlot(i, cur); }
        }
        return best == Long.MIN_VALUE ? new Cmp(0, 2, "") : new Cmp(best, 0, "");
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
