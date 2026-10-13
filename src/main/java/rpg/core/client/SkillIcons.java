package rpg.core.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import rpg.core.RpgCore;
import rpg.core.skill.Defs;

/** 스킬 아이콘: textures/gui/skill/<id>.png (16×16). 없으면 이름 첫 글자 */
public final class SkillIcons {
    private SkillIcons() {}

    private static final Map<String, Boolean> EXISTS = new HashMap<>();

    public static ResourceLocation tex(String id) {
        return RpgCore.id("textures/gui/skill/" + id + ".png");
    }

    public static void draw(GuiGraphics g, String id, int x, int y) {
        ResourceLocation rl = tex(id);
        boolean has = EXISTS.computeIfAbsent(id, k -> Minecraft.getInstance().getResourceManager().getResource(rl).isPresent());
        if (has) {
            g.blit(rl, x, y, 0, 0, 16, 16, 16, 16);
        } else {
            Defs.SkillDef s = Defs.SKILLS.get(id);
            String t = s == null || s.name().isEmpty() ? "?" : s.name().substring(0, 1);
            g.fill(x, y, x + 16, y + 16, 0xFF303050);
            g.drawString(Minecraft.getInstance().font, t, x + 8 - Minecraft.getInstance().font.width(t) / 2, y + 4, 0xFFFFFFFF, true);
        }
    }
}
