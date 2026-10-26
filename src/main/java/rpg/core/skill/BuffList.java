package rpg.core.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rpg.core.data.PlayerData;

/** HUD 버프 칸 목록 (서버에서 만들어 보냄) */
public final class BuffList {
    private BuffList() {}

    public record Entry(String name, int left, int kind, String extra) {}

    public static final int INF = 1_000_000_000;
    /** 파티원이 걸어줄 수 있는 버프 */
    private static final Set<String> PARTY = Set.of("bless", "haste", "plunder", "blessed_hand");
    /** 스킬이 아닌 버프 id → 이름 (트리 효과) */
    private static final Map<String, String> TREE = Map.ofEntries(
            Map.entry("slayer", "학살자"), Map.entry("battle_thrill", "전투 흥분"), Map.entry("hunt_speed", "사냥 본능"),
            Map.entry("roll_dmg", "재빠른 회피"), Map.entry("roll_crit", "잔상 걸음"), Map.entry("tp_dmg", "순간 영창"),
            Map.entry("chase", "추적자"), Map.entry("wind_veil", "바람의 장막"), Map.entry("escape_speed", "도망자"),
            Map.entry("evasion_instinct", "회피 본능"), Map.entry("blessed_hand", "축복받은 손"));

    public static List<Entry> build(PlayerData d, long now) {
        List<Entry> skill = new ArrayList<>(), party = new ArrayList<>(), tree = new ArrayList<>(), cd = new ArrayList<>();
        if (now < d.sprintEnd) skill.add(new Entry("질주", (int) (d.sprintEnd - now), 0, ""));
        if (now < d.shieldEnd && d.shield > 0) skill.add(new Entry("보호막", d.shieldEnd == Long.MAX_VALUE ? INF : (int) (d.shieldEnd - now), 0, ""));
        d.buffEnd.forEach((id, end) -> {
            if (now >= end || id.equals("stealth") && d.buffV("stealth", now) == 0 && d.buffEnd.containsKey("escape_speed")) return;
            int left = (int) (end - now);
            String tn = TREE.get(id);
            if (PARTY.contains(id)) {
                var def = Defs.SKILLS.get(id);
                party.add(new Entry(tn != null ? tn : def != null ? def.name() : id, left, 1, ""));
            } else if (tn != null) {
                String ex = id.equals("slayer") || id.equals("battle_thrill") ? "×" + Math.round(d.buffVal2.getOrDefault(id, 0.0)) : "";
                tree.add(new Entry(tn, left, 2, ex));
            } else {
                var def = Defs.SKILLS.get(id);
                if (def != null) skill.add(new Entry(def.name(), left, 0, ""));
            }
        });
        // 오라 (파티)
        if (now < d.warcryUntil) party.add(new Entry("전장의 함성", INF, 1, "+" + fmt(d.warcryVal) + "%"));
        if (now < d.oathUntil) party.add(new Entry("수호자의 맹세", INF, 1, ""));
        if (now < d.holyUntil && d.holyVal > 0) party.add(new Entry("신성 영역", INF, 1, ""));
        // 조건부 트리 효과
        if (d.guardNear && d.bonus("guardInstinct") > 0) tree.add(new Entry("수호 본능", INF, 2, "ON"));
        if (d.bonus("lowHpDmg") > 0 && d.hp < d.maxHp * 0.5) tree.add(new Entry("광전사의 혼", INF, 2, "ON"));
        if (d.tauntCount > 0 && d.bonus("tauntDmg") > 0) tree.add(new Entry("도발의 대가", INF, 2, "+" + fmt(Math.min(15, d.tauntCount * d.bonus("tauntDmg"))) + "%"));
        if (d.moving && d.bonus("moveDmg") > 0) tree.add(new Entry("질풍", INF, 2, "ON"));
        if (d.stillStacks > 0) tree.add(new Entry("냉정", INF, 2, "×" + d.stillStacks));
        if (d.loneOk) tree.add(new Entry("고독한 저격수", INF, 2, "ON"));
        double ov = PassiveEffects.overload(d);
        if (ov > 0) tree.add(new Entry("마력 과부하", INF, 2, "+" + fmt(ov) + "%"));
        if (PassiveEffects.loneAssassin(d)) tree.add(new Entry("고독한 암살자", INF, 2, "ON"));
        if (d.bleedNear > 0 && d.bonus("bloodFeast") > 0) tree.add(new Entry("피의 향연", INF, 2, "+" + fmt(Math.min(25, d.bleedNear * d.bonus("bloodFeast"))) + "%"));
        // 재사용 대기
        if (d.bonus("crisisShield") > 0 && now < d.crisisReadyAt) cd.add(new Entry("위기 회복", (int) (d.crisisReadyAt - now), 3, ""));
        if (d.bonus("windVeil") > 0 && now < d.windVeilReadyAt) cd.add(new Entry("바람의 장막", (int) (d.windVeilReadyAt - now), 3, ""));
        if (d.bonus("escape") > 0 && now < d.escapeReadyAt) cd.add(new Entry("도망자", (int) (d.escapeReadyAt - now), 3, ""));
        List<Entry> out = new ArrayList<>(skill);
        out.addAll(party); out.addAll(tree); out.addAll(cd);
        return out;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
