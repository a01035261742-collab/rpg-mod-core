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

    /** 데미지 숫자 (시안 dmg3-③): 흰 픽셀 숫자 + 검은 테, 크리 = 금색 1.25배 + 오른쪽 위 CRIT, 같은 몹 연타는 위로 쌓임 */
    private static final class Pop {
        final int id; final Vec3 pos; final String text; final int tint; final boolean crit; final float scale; final int row; int age;
        Pop(int id, Vec3 pos, String text, int tint, boolean crit, float scale, int row) { this.id = id; this.pos = pos; this.text = text; this.tint = tint; this.crit = crit; this.scale = scale; this.row = row; }
    }

    private static final List<Pop> POPS = new ArrayList<>();
    private static final int LIFE = 32;
    private static final java.util.Map<Integer, long[]> STACK = new java.util.HashMap<>();   // 몹 → [마지막 시각, 줄 수]
    private static final net.minecraft.resources.ResourceLocation DIGITS = RpgCore.id("textures/gui/dmg_digits.png"),
            CRIT = RpgCore.id("textures/gui/dmg_crit.png"),
            PL = RpgCore.id("textures/gui/ui/plaque_l.png"), PM = RpgCore.id("textures/gui/ui/plaque_m.png"), PR = RpgCore.id("textures/gui/ui/plaque_r.png");
    private static final String CHARS = "0123456789,";

    private static int adv(char ch) { return ch == ',' ? 5 : 7; }

    private static float numWidth(String t) { float w = 2; for (int i = 0; i < t.length(); i++) w += adv(t.charAt(i)); return w; }

    private static void tex(com.mojang.blaze3d.vertex.VertexConsumer v, Matrix4f m, float x0, float y0, float x1, float y1,
                            float u0, float v0, float u1, float v1, int argb) {
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        v.vertex(m, x0, y0, 0).color(r, g, b, a).uv(u0, v0).uv2(0xF000F0).endVertex();
        v.vertex(m, x0, y1, 0).color(r, g, b, a).uv(u0, v1).uv2(0xF000F0).endVertex();
        v.vertex(m, x1, y1, 0).color(r, g, b, a).uv(u1, v1).uv2(0xF000F0).endVertex();
        v.vertex(m, x1, y0, 0).color(r, g, b, a).uv(u1, v0).uv2(0xF000F0).endVertex();
    }

    public static void addDamage(int id, double amount, int type) {
        boolean crit = type == 1, dot = type >= 2;
        int dotCol = type == 2 ? 0xFF7CE05A : type == 3 ? 0xFFE04848 : 0xFFFF9A3C;   // 독 초록 · 출혈 빨강 · 화상 주황
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(id);
        if (e == null) return;
        Vec3 p = e.position().add(0, e.getBbHeight() + 1.05, 0);   // 이름 현판 위에서 시작
        String t = String.format("%,d", (long) amount);
        if (dot) {   // 독·출혈·화상: 작은 색 숫자, 쌓지 않고 옆에서 살짝
            POPS.add(new Pop(id, p.add((Math.random() - 0.5) * 0.6, -0.2, (Math.random() - 0.5) * 0.6), t, dotCol, false, 0.7f, 0));
        } else {
            long now = System.currentTimeMillis();
            long[] st = STACK.computeIfAbsent(id, k -> new long[]{0, 0});
            int row = now - st[0] < 700 ? (int) st[1] : 0;
            st[0] = now; st[1] = row + 1;
            POPS.add(new Pop(id, p, t, 0xFFFFFFFF, crit, crit ? 1.25f : 1f, row));
        }
        if (POPS.size() > 80) POPS.remove(0);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (Iterator<Pop> it = POPS.iterator(); it.hasNext(); ) if (++it.next().age > LIFE) it.remove();
        // 오래된 몹 정보 정리
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { ClientMobs.MAP.clear(); return; }
        if (mc.level.getGameTime() % 100 == 0) { ClientMobs.MAP.keySet().removeIf(id -> mc.level.getEntity(id) == null); STACK.keySet().removeIf(id -> mc.level.getEntity(id) == null); }
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
            // 이름 현판 (시안 C: 높이 17, 양끝 11, 글자는 안쪽 남색 칸 가운데)
            float ph = 17, cw = 11, mw = nw + 10, py = -ph - 2;
            ps.pushPose();
            ps.translate(0, 0, 0.01f);
            Matrix4f pm = ps.last().pose();
            int pc = a | 0xFFFFFF;
            tex(buf.getBuffer(net.minecraft.client.renderer.RenderType.text(PL)), pm, -mw / 2 - cw, py, -mw / 2, py + ph, 0, 0, 1, 1, pc);
            tex(buf.getBuffer(net.minecraft.client.renderer.RenderType.text(PM)), pm, -mw / 2, py, mw / 2, py + ph, 0, 0, 1, 1, pc);
            tex(buf.getBuffer(net.minecraft.client.renderer.RenderType.text(PR)), pm, mw / 2, py, mw / 2 + cw, py + ph, 0, 0, 1, 1, pc);
            ps.popPose();
            font.drawInBatch(name, -nw / 2f, py + 5, a | (elite ? 0xFFAA28 : 0xFFE282), false, m, buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            var tb = buf.getBuffer(net.minecraft.client.renderer.RenderType.textBackground());
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

        // 데미지 숫자 (텍스처 숫자, 벽 너머도 보임)
        var dv = buf.getBuffer(net.minecraft.client.renderer.RenderType.textSeeThrough(DIGITS));
        java.util.List<float[]> crits = new java.util.ArrayList<>();
        for (Pop pop : POPS) {
            float t = (pop.age + pt) / LIFE;
            float fadeT = Math.max(0, (t - 0.7f) / 0.3f);
            int alpha = (int) (255 * (1 - fadeT));
            if (alpha < 10) continue;
            // 처음 0.1 동안 살짝 커졌다 제자리 · 천천히 떠오름
            float pop0 = t < 0.1f ? 1.3f - t * 3f : 1f;
            Vec3 p = pop.pos.add(0, t * 0.35, 0);
            ps.pushPose();
            ps.translate(p.x - c.x, p.y - c.y, p.z - c.z);
            ps.mulPose(cam.rotation());
            float s = 0.025f * 1.4f * pop.scale * pop0;   // 1.4배
            ps.scale(-s, -s, s);
            // 줄: 아래부터 위로 (줄 높이 10px, 크기 다른 것도 같은 간격)
            ps.translate(0, -pop.row * 10f / (pop.scale * pop0), 0);
            Matrix4f m = ps.last().pose();
            float w = numWidth(pop.text), x = -w / 2f + 1;
            int col = (alpha << 24) | (pop.tint & 0xFFFFFF);
            float vRow = pop.crit ? 0.5f : 0f;
            for (int i = 0; i < pop.text.length(); i++) {
                char ch = pop.text.charAt(i);
                int k = CHARS.indexOf(ch);
                if (k < 0) continue;
                float u0 = k * 8 / 88f, u1 = (k * 8 + 8) / 88f;
                tex(dv, m, x - 1, -4.5f, x + 7, 4.5f, u0, vRow, u1, vRow + 0.5f, col);
                x += adv(ch);
            }
            if (pop.crit) crits.add(new float[]{(float) (p.x - c.x), (float) (p.y - c.y), (float) (p.z - c.z), s, w / 2f, pop.row * 10f / (pop.scale * pop0), alpha});
            ps.popPose();
        }
        // CRIT 글자 (숫자 오른쪽 위, 작게)
        var cv = buf.getBuffer(net.minecraft.client.renderer.RenderType.textSeeThrough(CRIT));
        for (float[] k : crits) {
            ps.pushPose();
            ps.translate(k[0], k[1], k[2]);
            ps.mulPose(cam.rotation());
            ps.scale(-k[3], -k[3], k[3]);
            ps.translate(0, -k[5], 0);
            Matrix4f m = ps.last().pose();
            float x0 = k[4] - 1.6f, y0 = -4.5f - 7 * 0.65f + 1.2f;   // 숫자 오른쪽 끝 위 (CRIT 0.65배)
            tex(cv, m, x0, y0, x0 + 22 * 0.65f, y0 + 7 * 0.65f, 0, 0, 1, 1, ((int) k[6] << 24) | 0xFFFFFF);
            ps.popPose();
        }
        buf.endBatch();
    }
}
