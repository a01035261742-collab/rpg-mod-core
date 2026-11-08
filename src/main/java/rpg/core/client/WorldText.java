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

    public static void addDamage(int id, double amount, int type) {
        boolean crit = type == 1, dot = type >= 2;
        int dotCol = type == 2 ? 0xFF7CE05A : type == 3 ? 0xFFE04848 : 0xFFFF9A3C;   // 독 초록 · 출혈 빨강 · 화상 주황
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(id);
        if (e == null) return;
        Vec3 p = e.position().add(0, e.getBbHeight() + 0.5, 0);
        double dx = (Math.random() - 0.5) * 0.8;
        String t = String.format("%,d", (long) amount);
        POPS.add(new Pop(p, crit ? "✦" + t : t, crit ? 0xFFFFA020 : dot ? dotCol : 0xFFFFFFFF, crit ? 1.5f : dot ? 0.7f : 1.0f, dx));
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

    // ── 엘리트는 1.5배 크게 보이기 (맞는 범위도 1.5배: eliteSizeClient · 서버 CombatEvents.eliteSize) ──
    // 에픽파이트 몹(거미·마녀·멧돼지 등)은 EF 가 Pre 를 취소하고 같은 PoseStack 으로 직접 그림 → HIGHEST 에서 먼저 키우고,
    // 취소되면 Post 가 안 오니 LOWEST(취소된 것도 받음)에서 되돌림. 우리 GeckoLib 몹은 RpgGeoRenderer 가 직접 키움.
    private static final java.util.Set<LivingEntity> SCALED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void elitePre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity le = e.getEntity();
        if (!isElite(le) || !SCALED.add(le)) return;
        float s = rpg.core.combat.Mobs.ELITE_SCALE;
        e.getPoseStack().pushPose();
        e.getPoseStack().scale(s, s, s);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void elitePreCanceled(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> e) {
        if (e.isCanceled() && SCALED.remove(e.getEntity())) e.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void elitePost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> e) {
        if (SCALED.remove(e.getEntity())) e.getPoseStack().popPose();
    }

    /** 클라 쪽 엘리트 몸통 1.5배 (조준·맞는 범위). 엘리트인지 알게 되면 ClientMobs.update 가 refreshDimensions */
    @SubscribeEvent
    public static void eliteSizeClient(net.minecraftforge.event.entity.EntityEvent.Size e) {
        Entity en = e.getEntity();
        if (!(en instanceof net.minecraft.world.entity.Mob le) || en.level() == null || !en.level().isClientSide) return;
        if (isElite(le)) e.setNewSize(e.getNewSize().scale(rpg.core.combat.Mobs.ELITE_SCALE), true);
    }

    public static boolean isElite(LivingEntity le) {
        ClientMobs.Info i = ClientMobs.MAP.get(le.getId());
        return i != null && i.name().startsWith("★");
    }

    /** 플레이어 칭호 (서버가 보냄) */
    private static final java.util.Map<java.util.UUID, rpg.core.net.Net.TitleSync> TITLES = new java.util.HashMap<>();

    public static void title(rpg.core.net.Net.TitleSync t) {
        if (t.title().isEmpty()) TITLES.remove(t.player()); else TITLES.put(t.player(), t);
    }

    private static final java.util.Map<Integer, Long> LOOK = new java.util.HashMap<>();
    private static final java.util.Map<Integer, double[]> MOB_TRAIL = new java.util.HashMap<>();

    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        v.vertex(m, x0, y0, 0).color(r, g, b, a).uv2(0xF000F0).endVertex();
        v.vertex(m, x0, y1, 0).color(r, g, b, a).uv2(0xF000F0).endVertex();
        v.vertex(m, x1, y1, 0).color(r, g, b, a).uv2(0xF000F0).endVertex();
        v.vertex(m, x1, y0, 0).color(r, g, b, a).uv2(0xF000F0).endVertex();
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

        // 플레이어 머리 위 칭호: 《 칭호 》 (이름표 위, 32칸 이내, 1인칭 내 것은 안 보임) · RPG 대륙에서만
        if (rpg.core.world.RpgWorld.is(mc.level)) for (var t : TITLES.values()) {
            var pl = mc.level.getPlayerByUUID(t.player());
            if (pl == null || pl.isInvisible() || pl.distanceToSqr(c) > 32 * 32) continue;
            if (pl == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 p = pl.getPosition(pt).add(0, pl.getBbHeight() + 0.85, 0);
            String s = "《 " + t.title() + " 》";
            ps.pushPose();
            ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
            ps.mulPose(cam.rotation());
            ps.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f m = ps.last().pose();
            font.drawInBatch(s, -font.width(s) / 2f, 0, 0xFF000000 | t.titleColor(), true, m, buf, Font.DisplayMode.NORMAL, 0x40000000, 0xF000F0);
            ps.popPose();
        }

        // 몹 머리 위 (시안 A): "Lv.5 좀비" + 테두리 바. 맞은 몹·바라보는 몹만 5초, 보스바 뜬 보스는 안 그림
        long nowMs = System.currentTimeMillis();
        Entity look = mc.crosshairPickEntity;
        if (look != null) LOOK.put(look.getId(), nowMs);
        var tb = buf.getBuffer(net.minecraft.client.renderer.RenderType.textBackground());
        for (var en : ClientMobs.MAP.entrySet()) {
            int id = en.getKey();
            Entity ent = mc.level.getEntity(id);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) continue;
            if (ent.distanceToSqr(c) > 24 * 24) continue;
            long seen = Math.max(ClientMobs.HIT.getOrDefault(id, 0L), LOOK.getOrDefault(id, 0L));
            if (nowMs - seen > 5000) continue;
            ClientMobs.Info info = en.getValue();
            if (BossBars.ACTIVE.contains(info.name())) continue;
            float fade = Math.min(1f, (5000 - (nowMs - seen)) / 500f);   // 마지막 0.5초 흐려짐
            int a = Math.max(8, (int) (255 * fade)) << 24;
            Vec3 p = ent.getPosition(pt).add(0, ent.getBbHeight() + 0.45, 0);
            boolean elite = info.name().startsWith("★");
            String name = "Lv." + info.level() + " " + info.name();
            float r = info.maxHp() <= 0 ? 0 : (float) Math.max(0, Math.min(1, info.hp() / info.maxHp()));
            double[] tr = MOB_TRAIL.computeIfAbsent(id, k -> new double[]{r, nowMs});
            if (r > tr[0]) tr[0] = r;
            double dt = Math.max(0, Math.min(0.1, (nowMs - tr[1]) / 1000.0));
            tr[1] = nowMs;
            tr[0] = Math.max(r, tr[0] - dt * 0.5);
            ps.pushPose();
            ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
            ps.mulPose(cam.rotation());
            ps.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f m = ps.last().pose();
            float nw = font.width(name);
            font.drawInBatch(name, -nw / 2f, -11, a | (elite ? 0xFFAA28 : 0xFFE282), false, m, buf, Font.DisplayMode.NORMAL, 0x60000000 & (a | 0xFFFFFF), 0xF000F0);
            float hw = elite ? 28 : 22, h = elite ? 5 : 4;
            float xr = -hw + 2 * hw * r, xt = (float) (-hw + 2 * hw * tr[0]);
            // 겹치지 않게 칸 나눠 그림 (테두리 → 채움 · 흰 자리 · 빈 칸)
            int edge = elite ? 0xECC25A : 0x0A0608;
            quad(tb, m, -hw - 1, -1, hw + 1, 0, a | edge);
            quad(tb, m, -hw - 1, h, hw + 1, h + 1, a | (elite ? 0xC8902E : 0x0A0608));
            quad(tb, m, -hw - 1, 0, -hw, h, a | edge);
            quad(tb, m, hw, 0, hw + 1, h, a | edge);
            if (xr > -hw) { quad(tb, m, -hw, 0, xr, 1, a | 0xFA6E64); quad(tb, m, -hw, 1, xr, h, a | 0xD22D2D); }
            if (xt > xr) quad(tb, m, xr, 0, xt, h, a | 0xFAF0D7);
            if (hw > xt) quad(tb, m, xt, 0, hw, h, a | 0x321014);
            ps.popPose();
        }
        if (mc.level.getGameTime() % 200 == 0) { MOB_TRAIL.keySet().removeIf(k -> !ClientMobs.MAP.containsKey(k)); LOOK.keySet().removeIf(k -> !ClientMobs.MAP.containsKey(k)); ClientMobs.HIT.keySet().removeIf(k -> !ClientMobs.MAP.containsKey(k)); }

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
