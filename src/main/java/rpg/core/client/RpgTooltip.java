package rpg.core.client;

import com.mojang.datafixers.util.Either;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import rpg.core.RpgCore;
import rpg.core.equip.Gear;
import rpg.core.equip.GearDefs;
import rpg.core.equip.Potential;
import rpg.core.net.Net;

/**
 * 우리 모드 툴팁: 어두운 남색 바탕 + 등급 색 테두리, 머리(아이콘·이름·부위·별), 구역 줄, 맨 아래 전투력 비교.
 * 전투력은 마우스 올릴 때만 서버에 물어보고 5초 동안 기억.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class RpgTooltip {
    private RpgTooltip() {}

    static final int BG = 0xF0121420, GRAY = 0xFF6A7090;

    private static boolean ours(ItemStack s) {
        if (s.isEmpty()) return false;
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem());
        return id != null && RpgCore.MODID.equals(id.getNamespace());
    }

    /** 테두리 색: 잠재 등급 (없으면 회색) */
    static int gradeColor(ItemStack s) {
        if (Gear.def(s) != null && Potential.has(s)) return 0xFF000000 | Potential.COLOR[Math.max(0, Math.min(3, Potential.grade(s)))];
        return GRAY;
    }

    // ───────── 색 ─────────
    @SubscribeEvent
    public static void color(RenderTooltipEvent.Color e) {
        if (!ours(e.getItemStack())) return;
        int c = gradeColor(e.getItemStack());
        e.setBackgroundStart(BG); e.setBackgroundEnd(BG);
        e.setBorderStart(c); e.setBorderEnd((c & 0x00FFFFFF) | 0x60000000);
    }

    // ───────── 전투력 비교 (아래 줄) ─────────
    private record Res(long delta, int state, String why, long at) {}
    private static final Map<Integer, Res> CACHE = new HashMap<>();
    private static final Map<Integer, Long> ASKED = new HashMap<>();

    private static int key(ItemStack s) {
        return java.util.Objects.hash(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()), s.getTag());
    }

    public static void result(int key, long delta, int state, String why) {
        CACHE.put(key, new Res(delta, state, why, System.currentTimeMillis()));
        ASKED.remove(key);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void lines(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        if (e.getEntity() == null || Gear.def(s) == null) return;
        int k = key(s);
        long now = System.currentTimeMillis();
        Res r = CACHE.get(k);
        if (r == null || now - r.at() > 5000) {
            Long asked = ASKED.get(k);
            if (asked == null || now - asked > 1000) { ASKED.put(k, now); Net.CH.sendToServer(new Net.PowerReq(k, s.copy())); }
            if (r == null) { e.getToolTip().add(Component.literal("전투력 계산 중…").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)); return; }
        }
        Component line = switch (r.state()) {
            case 1 -> Component.literal("착용 중").withStyle(net.minecraft.ChatFormatting.GRAY);
            case 2 -> Component.literal("착용 불가" + (r.why().isEmpty() ? "" : " (" + r.why() + ")")).withStyle(net.minecraft.ChatFormatting.RED);
            default -> r.delta() > 0 ? Component.literal(String.format("전투력 +%,d ▲", r.delta())).withStyle(st -> st.withColor(0x6EE66E))
                    : r.delta() < 0 ? Component.literal(String.format("전투력 %,d ▼", r.delta())).withStyle(st -> st.withColor(0xFF6060))
                    : Component.literal("전투력 변화 없음").withStyle(net.minecraft.ChatFormatting.GRAY);
        };
        e.getToolTip().add(line);
    }

    // ───────── 머리·구역 줄 ─────────
    @SubscribeEvent
    public static void gather(RenderTooltipEvent.GatherComponents e) {
        ItemStack s = e.getItemStack();
        if (!ours(s)) return;
        List<Either<FormattedText, TooltipComponent>> list = e.getTooltipElements();
        if (list.isEmpty()) return;
        boolean gear = Gear.def(s) != null;
        if (gear) {
            // 이름 + (부위·레벨·직업 줄) + (별 줄) → 머리 칸 하나로
            String name = text(list.get(0));
            String sub = list.size() > 1 ? text(list.get(1)) : "";
            String stars = "";
            int starIdx = -1;
            for (int i = 1; i < Math.min(list.size(), 6); i++) {
                String t = text(list.get(i));
                if (t.startsWith("★") || t.startsWith("☆")) { stars = t; starIdx = i; break; }
            }
            if (starIdx > 0) list.remove(starIdx);
            if (list.size() > 1) list.remove(1);
            String grade = Potential.has(s) ? "[" + Potential.GRADE[Math.max(0, Math.min(3, Potential.grade(s)))] + "] " : "";
            list.set(0, Either.right(new Header(s, name, grade + sub, stars, gradeColor(s))));
            if (list.size() > 1) list.add(1, Either.right(new Sep(0)));
        }
        // 구역 앞에 줄: 추가 옵션 · 세트 · 잠재 · 전투력
        for (int i = list.size() - 1; i > 1; i--) {
            String t = text(list.get(i));
            if (t.startsWith("추가 옵션") || t.startsWith("[") || t.startsWith("전투력") || t.startsWith("착용") || t.equals("전투력 계산 중…"))
                if (!(list.get(i - 1).right().orElse(null) instanceof Sep)) list.add(i, Either.right(new Sep(0)));
        }
        // 줄 길이 = 툴팁 폭
        Font f = Minecraft.getInstance().font;
        int w = 0;
        for (var x : list) {
            if (x.left().isPresent()) w = Math.max(w, f.width(x.left().get()));
            else if (x.right().orElse(null) instanceof Header h) w = Math.max(w, new ClientHeader(h).getWidth(f));
        }
        for (int i = 0; i < list.size(); i++) if (list.get(i).right().orElse(null) instanceof Sep) list.set(i, Either.right(new Sep(w)));
    }

    private static String text(Either<FormattedText, TooltipComponent> x) {
        return x.left().map(FormattedText::getString).orElse("");
    }

    // ───────── 툴팁 부품 ─────────
    public record Header(ItemStack stack, String name, String sub, String stars, int color) implements TooltipComponent {}
    public record Sep(int width) implements TooltipComponent {}

    public static ClientTooltipComponent client(TooltipComponent c) {
        if (c instanceof Header h) return new ClientHeader(h);
        if (c instanceof Sep sp) return new ClientSep(sp.width());
        return null;
    }

    static final class ClientHeader implements ClientTooltipComponent {
        final Header h;
        ClientHeader(Header h) { this.h = h; }
        @Override public int getHeight() { return 30; }
        @Override public int getWidth(Font f) {
            return 30 + Math.max(f.width(h.name()), Math.max((int) (f.width(h.sub()) * 0.8), (int) (f.width(h.stars()) * 0.8)));
        }
        @Override public void renderText(Font f, int x, int y, Matrix4f m, net.minecraft.client.renderer.MultiBufferSource.BufferSource buf) {}
        @Override public void renderImage(Font f, int x, int y, GuiGraphics g) {
            g.fill(x, y, x + 26, y + 26, 0xFF0A0A12);
            int c = h.color();
            g.fill(x, y, x + 26, y + 1, c); g.fill(x, y + 25, x + 26, y + 26, c);
            g.fill(x, y, x + 1, y + 26, c); g.fill(x + 25, y, x + 26, y + 26, c);
            g.pose().pushPose();
            g.pose().translate(x + 1, y + 1, 0);
            g.pose().scale(1.5f, 1.5f, 1);
            g.renderItem(h.stack(), 0, 0);
            g.pose().popPose();
            g.drawString(f, h.name(), x + 30, y, 0xFFFFFFFF, true);
            g.pose().pushPose();
            g.pose().translate(x + 30, y + 11, 0);
            g.pose().scale(0.8f, 0.8f, 1);
            g.drawString(f, h.sub(), 0, 0, c, true);
            if (!h.stars().isEmpty()) g.drawString(f, h.stars(), 0, 10, 0xFFFFD24A, true);
            g.pose().popPose();
        }
    }

    static final class ClientSep implements ClientTooltipComponent {
        final int w;
        ClientSep(int w) { this.w = w; }
        @Override public int getHeight() { return 5; }
        @Override public int getWidth(Font f) { return 10; }
        @Override public void renderImage(Font f, int x, int y, GuiGraphics g) {
            g.fill(x, y + 2, x + Math.max(10, w), y + 3, 0xFF3A4260);
        }
    }

    /** 장비 이름 색 (GearDefs 쪽에서 안 쓰면 무시) */
    static int nameColor(GearDefs.GearDef d) { return 0xFFFFFFFF; }
}
