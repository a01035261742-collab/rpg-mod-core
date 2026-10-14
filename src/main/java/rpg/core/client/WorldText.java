package rpg.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import rpg.core.RpgCore;

/** 월드에 뜨는 글자: 몹 머리 위 이름·레벨·체력바, 떠오르는 데미지 숫자 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class WorldText {
    private WorldText() {}

    private static final class Pop {
        final Vec3 pos; final String text; final int color; final float scale; final double dx; int age;
        Pop(Vec3 pos, String text, int color, float scale, double dx) { this.pos = pos; this.text = text; this.color = color; this.scale = scale; this.dx = dx; }
    }

    private static final List<Pop> POPS = new ArrayList<>();
    private static final int LIFE = 24;

    public static void addDamage(int id, double amount, boolean crit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(id);
        if (e == null) return;
        Vec3 p = e.position().add(0, e.getBbHeight() + 0.5, 0);
        double dx = (Math.random() - 0.5) * 0.8;
        String t = String.format("%,d", (long) amount);
        POPS.add(new Pop(p, crit ? "✦" + t : t, crit ? 0xFFFFA020 : 0xFFFFFFFF, crit ? 1.5f : 1.0f, dx));
        if (POPS.size() > 60) POPS.remove(0);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (Iterator<Pop> it = POPS.iterator(); it.hasNext(); ) if (++it.next().age > LIFE) it.remove();
        // 오래된 몹 정보 정리
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { ClientMobs.MAP.clear(); return; }
        if (mc.level.getGameTime() % 100 == 0) ClientMobs.MAP.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    // ── 엘리트는 1.3배 크게 보이기 (몸 크기 판정은 그대로) ──
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)   // 다른 모드가 취소한 뒤에 (Post 짝 맞추기)
    public static void elitePre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> e) {
        if (isElite(e.getEntity())) { e.getPoseStack().pushPose(); e.getPoseStack().scale(1.3f, 1.3f, 1.3f); }
    }

    @SubscribeEvent
    public static void elitePost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> e) {
        if (isElite(e.getEntity())) e.getPoseStack().popPose();
    }

    public static boolean isElite(LivingEntity le) {
        ClientMobs.Info i = ClientMobs.MAP.get(le.getId());
        return i != null && i.name().startsWith("★");
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera cam = e.getCamera();
        Vec3 c = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float pt = e.getPartialTick();

        // 몹 머리 위: "Lv.5 좀비" + 체력바 (20칸 이내, 바라보는 쪽)
        for (var en : ClientMobs.MAP.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) continue;
            if (ent.distanceToSqr(c) > 20 * 20) continue;
            ClientMobs.Info info = en.getValue();
            Vec3 p = ent.getPosition(pt).add(0, ent.getBbHeight() + 0.45, 0);
            String name = "Lv." + info.level() + " " + info.name();
            double r = info.maxHp() <= 0 ? 0 : Math.max(0, Math.min(1, info.hp() / info.maxHp()));
            int seg = 20, full = (int) Math.ceil(r * seg);
            String bar = "|".repeat(full);
            String rest = "|".repeat(seg - full);
            ps.pushPose();
            ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
            ps.mulPose(cam.rotation());
            ps.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f m = ps.last().pose();
            float nw = font.width(name);
            boolean elite = info.name().startsWith("★");
            font.drawInBatch(name, -nw / 2f, -10, elite ? 0xFFFFA000 : 0xFFFFE070, false, m, buf, Font.DisplayMode.NORMAL, 0x60000000, 0xF000F0);
            float bw = font.width(bar + rest);
            font.drawInBatch(bar, -bw / 2f, 0, 0xFFE03030, false, m, buf, Font.DisplayMode.NORMAL, 0x60000000, 0xF000F0);
            font.drawInBatch(rest, -bw / 2f + font.width(bar), 0, 0xFF503030, false, m, buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            ps.popPose();
        }

        // 퀘스트 NPC 머리 위: ! (받기) · ? (완료) · 회색 ? (진행 중)
        if (!rpg.core.quest.Quests.NPCS.isEmpty()) {
            for (Entity ent : mc.level.entitiesForRendering()) {
                if (!(ent instanceof net.minecraft.world.entity.npc.Villager) || !ent.hasCustomName()) continue;
                if (ent.distanceToSqr(c) > 32 * 32) continue;
                String npc = rpg.core.quest.Quests.npcByName(ent.getCustomName().getString());
                if (npc == null) continue;
                int mark = ClientQuests.npcMark(npc);
                if (mark == 0) continue;
                String t = mark == 1 ? "!" : "?";
                int col = mark == 3 ? 0xFFA0A0A0 : 0xFFFFD020;
                double bob = Math.sin((mc.level.getGameTime() + pt) * 0.12) * 0.06;
                Vec3 p = ent.getPosition(pt).add(0, ent.getBbHeight() + 0.95 + bob, 0);
                ps.pushPose();
                ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
                ps.mulPose(cam.rotation());
                ps.scale(-0.06f, -0.06f, 0.06f);
                Matrix4f m = ps.last().pose();
                font.drawInBatch(t, -font.width(t) / 2f, 0, col, true, m, buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                ps.popPose();
            }
        }

        // 퀘스트 지점 위: ! (가져오기) · ? (두고 오기)
        String dim = mc.level.dimension().location().toString();
        for (var en : ClientQuests.POINTS.entrySet()) {
            var qp = en.getValue();
            if (!qp.dim().equals(dim)) continue;
            Vec3 base = Vec3.atBottomCenterOf(qp.pos());
            if (base.distanceToSqr(c) > 48 * 48) continue;
            int mark = ClientQuests.pointMark(en.getKey());
            if (mark == 0) continue;
            String t = mark == 1 ? "!" : "?";
            double bob = Math.sin((mc.level.getGameTime() + qp.pos().getX() + pt) * 0.12) * 0.06;
            String label = rpg.core.quest.Quests.pointName(en.getKey());
            ps.pushPose();
            ps.translate(base.x - c.x, base.y + 2.0 + bob - c.y, base.z - c.z);
            ps.mulPose(cam.rotation());
            ps.scale(-0.06f, -0.06f, 0.06f);
            Matrix4f m = ps.last().pose();
            font.drawInBatch(t, -font.width(t) / 2f, 0, 0xFFFFD020, true, m, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            ps.scale(0.4f, 0.4f, 0.4f);
            m = ps.last().pose();
            font.drawInBatch(label, -font.width(label) / 2f, 26, 0xFFFFFFFF, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0x60000000, 0xF000F0);
            ps.popPose();
        }

        // 데미지 숫자
        for (Pop pop : POPS) {
            float t = (pop.age + pt) / LIFE;
            int alpha = (int) (255 * Math.max(0, 1 - t * t));
            if (alpha < 10) continue;
            Vec3 p = pop.pos.add(pop.dx * t, t * 1.2, 0);
            ps.pushPose();
            ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
            ps.mulPose(cam.rotation());
            float s = 0.03f * pop.scale * (t < 0.15f ? 1.4f - t * 2.6f : 1f);
            ps.scale(-s, -s, s);
            Matrix4f m = ps.last().pose();
            float w = font.width(pop.text);
            int col = (alpha << 24) | (pop.color & 0xFFFFFF);
            font.drawInBatch(pop.text, -w / 2f, 0, col, true, m, buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }
}
