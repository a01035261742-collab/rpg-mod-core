package rpg.core.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import rpg.core.RpgCore;
import rpg.core.skill.Defs;

/** 스킬 아이콘: textures/gui/skill/<id>.png (32×32, 16 크기로 그림). 없으면 이름 첫 글자.
 *  버프 아이콘: textures/gui/buff/<영문>.png (이름 → 영문은 BUFF), 스킬 이름이면 그 스킬 아이콘 */
public final class SkillIcons {
    private SkillIcons() {}

    private static final Map<String, Boolean> EXISTS = new HashMap<>();

    public static ResourceLocation tex(String id) {
        return RpgCore.id("textures/gui/skill/" + id + ".png");
    }

    /** 버프 이름 → 아이콘 파일 (스킬이 아닌 것) */
    private static final Map<String, String> BUFF = Map.ofEntries(
            Map.entry("보호막", "shield"),
            Map.entry("힘의 물약", "item_atk"), Map.entry("마력의 물약", "item_matk"), Map.entry("수호의 물약", "item_def"),
            Map.entry("행운 쿠키", "item_stat"), Map.entry("생명의 사탕", "item_hp"), Map.entry("사과", "item_regen"),
            Map.entry("콤보", "combo"),
            Map.entry("학살자", "slayer"),
            Map.entry("전투 흥분", "battle_thrill"),
            Map.entry("사냥 본능", "hunt_speed"),
            Map.entry("재빠른 회피", "roll_dmg"),
            Map.entry("잔상 걸음", "roll_crit"),
            Map.entry("순간 영창", "tp_dmg"),
            Map.entry("추적자", "chase"),
            Map.entry("바람의 장막", "wind_veil"),
            Map.entry("도망자", "escape_speed"),
            Map.entry("회피 본능", "evasion_instinct"),
            Map.entry("축복받은 손", "blessed_hand"),
            Map.entry("전장의 함성", "warcry"),
            Map.entry("수호자의 맹세", "oath"),
            Map.entry("신성 영역", "holy"),
            Map.entry("수호 본능", "guard_instinct"),
            Map.entry("광전사의 혼", "berserker_soul"),
            Map.entry("도발의 대가", "taunt_price"),
            Map.entry("질풍", "gale"),
            Map.entry("냉정", "calm"),
            Map.entry("고독한 저격수", "lone_sniper"),
            Map.entry("마력 과부하", "overload"),
            Map.entry("고독한 암살자", "lone_assassin"),
            Map.entry("피의 향연", "blood_feast"),
            Map.entry("위기 회복", "crisis"),
            Map.entry("독", "poison"),
            Map.entry("출혈", "bleed"),
            Map.entry("기절", "stun"),
            Map.entry("둔화", "slow"));
    private static final Map<String, ResourceLocation> BUFF_TEX = new HashMap<>();

    /** 버프 칸 아이콘 (이름 앞부분으로 찾음: "콤보 · 골드 +5%" → 콤보). 없으면 false */
    public static boolean drawBuff(GuiGraphics g, String name, int x, int y) {
        ResourceLocation rl = BUFF_TEX.computeIfAbsent(name, n -> {
            String base = n.split(" · ")[0].trim();
            String f = BUFF.get(base);
            if (f != null) return RpgCore.id("textures/gui/buff/" + f + ".png");
            for (Defs.SkillDef s : Defs.SKILLS.values()) if (s.name().equals(base)) return tex(s.id());
            return null;
        });
        if (rl == null) return false;
        boolean has = EXISTS.computeIfAbsent(rl.toString(), k -> Minecraft.getInstance().getResourceManager().getResource(rl).isPresent());
        if (!has) return false;
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(rl, x, y, 16, 16, 0, 0, 32, 32, 32, 32);
        return true;
    }

    public static void draw(GuiGraphics g, String id, int x, int y) {
        ResourceLocation rl = tex(id);
        boolean has = EXISTS.computeIfAbsent(rl.toString(), k -> Minecraft.getInstance().getResourceManager().getResource(rl).isPresent());
        if (has) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(rl, x, y, 16, 16, 0, 0, 32, 32, 32, 32);
        } else {
            Defs.SkillDef s = Defs.SKILLS.get(id);
            String t = s == null || s.name().isEmpty() ? "?" : s.name().substring(0, 1);
            g.fill(x, y, x + 16, y + 16, 0xFF303050);
            g.drawString(Minecraft.getInstance().font, t, x + 8 - Minecraft.getInstance().font.width(t) / 2, y + 4, 0xFFFFFFFF, true);
        }
    }
}
