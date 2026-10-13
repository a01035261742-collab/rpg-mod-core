package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 파티 창 (P): 파티원 목록, 초대, 수락/거절, 나가기, 추방 */
public class PartyScreen extends Screen {
    private static final int W = 220, H = 200, SW = 110;
    private int x, y;
    private EditBox nameBox;

    public PartyScreen() {
        super(Component.literal("파티"));
    }

    private static boolean amLeader() {
        String me = Minecraft.getInstance().getUser().getName();
        for (Net.Member m : ClientParty.MEMBERS) if (m.leader() && m.name().equals(me)) return true;
        return false;
    }

    private void send(int action, String name) {
        Net.CH.sendToServer(new Net.PartyAction(action, name));
    }

    @Override
    protected void init() {
        x = width / 2 - (W + 4 + SW) / 2;
        y = height / 2 - H / 2;
        // 오른쪽: 접속 중인 사람 → 바로 초대
        int i0 = 0;
        for (String name : onlineToInvite()) {
            if (i0 >= 8) break;
            addRenderableWidget(Button.builder(Component.literal("초대"), b -> send(0, name))
                    .bounds(x + W + 4 + SW - 36, y + 24 + i0 * 20, 30, 16).build());
            i0++;
        }
        nameBox = new EditBox(font, x + 8, y + H - 44, 120, 14, Component.literal("이름"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("초대할 이름"));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("초대"), b -> {
            if (!nameBox.getValue().isBlank()) send(0, nameBox.getValue().trim());
            nameBox.setValue("");
        }).bounds(x + 132, y + H - 45, 40, 16).build());
        addRenderableWidget(Button.builder(Component.literal("근처"), b -> fillNearest()).bounds(x + 176, y + H - 45, 36, 16).build());
        addRenderableWidget(Button.builder(Component.literal("파티 나가기"), b -> send(3, "")).bounds(x + 8, y + H - 22, 70, 16).build());
        if (!ClientParty.inviter.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("수락"), b -> send(1, "")).bounds(x + W - 92, y + H - 22, 40, 16).build());
            addRenderableWidget(Button.builder(Component.literal("거절"), b -> send(2, "")).bounds(x + W - 48, y + H - 22, 40, 16).build());
        }
        if (amLeader()) {
            String me = Minecraft.getInstance().getUser().getName();
            for (int i = 0; i < ClientParty.MEMBERS.size(); i++) {
                Net.Member m = ClientParty.MEMBERS.get(i);
                if (m.name().equals(me)) continue;
                addRenderableWidget(Button.builder(Component.literal("추방"), b -> send(4, m.name()))
                        .bounds(x + W - 40, y + 28 + i * 20, 32, 14).build());
            }
        }
    }

    /** 서버에 접속 중이고 아직 내 파티가 아닌 사람 */
    private static java.util.List<String> onlineToInvite() {
        Minecraft mc = Minecraft.getInstance();
        java.util.List<String> out = new java.util.ArrayList<>();
        if (mc.getConnection() == null) return out;
        String me = mc.getUser().getName();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String n = info.getProfile().getName();
            if (n.equals(me)) continue;
            boolean member = false;
            for (Net.Member m : ClientParty.MEMBERS) if (m.name().equals(n)) member = true;
            if (!member) out.add(n);
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    /** 가장 가까운 다른 플레이어 이름 채우기 */
    private void fillNearest() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        mc.level.players().stream().filter(p -> p != mc.player)
                .min((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)))
                .ifPresent(p -> nameBox.setValue(p.getGameProfile().getName()));
    }

    private int lastSize = -1, lastOnline = -1;
    private String lastInviter = "";

    @Override
    public void tick() {
        nameBox.tick();
        int online = onlineToInvite().size();
        if (lastSize != ClientParty.MEMBERS.size() || !lastInviter.equals(ClientParty.inviter) || online != lastOnline) {
            lastOnline = online;
            lastSize = ClientParty.MEMBERS.size();
            lastInviter = ClientParty.inviter;
            String v = nameBox.getValue();
            rebuildWidgets();
            nameBox.setValue(v);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x, y, x + W, y + H, 0xF0141420);
        g.fill(x, y, x + W, y + 1, 0xFFFFD24A);
        g.drawString(font, "파티", x + 8, y + 6, 0xFFFFD24A, true);
        g.drawString(font, ClientParty.MEMBERS.size() + "명", x + W - 8 - font.width(ClientParty.MEMBERS.size() + "명"), y + 6, 0xFFA0A0A0, false);
        if (ClientParty.MEMBERS.isEmpty()) {
            g.drawString(font, "파티가 없어요. 아래에서 초대하세요", x + 8, y + 30, 0xFF808080, false);
        }
        for (int i = 0; i < ClientParty.MEMBERS.size(); i++) {
            Net.Member m = ClientParty.MEMBERS.get(i);
            int ry = y + 26 + i * 20;
            g.fill(x + 6, ry, x + W - 6, ry + 18, 0x30FFFFFF);
            String name = (m.leader() ? "★ " : "") + m.name() + "  Lv." + m.level();
            g.drawString(font, name, x + 10, ry + 2, m.down() ? 0xFFFF6060 : 0xFFFFFFFF, true);
            double r = m.maxHp() <= 0 ? 0 : Math.max(0, Math.min(1, m.hp() / m.maxHp()));
            g.fill(x + 10, ry + 12, x + 130, ry + 15, 0xFF401010);
            g.fill(x + 10, ry + 12, x + 10 + (int) (120 * r), ry + 15, m.down() ? 0xFF808080 : 0xFFD03030);
            if (!m.near()) g.drawString(font, "멀리", x + 136, ry + 6, 0xFF808080, false);
            if (m.down()) g.drawString(font, "다운", x + 136, ry + 6, 0xFFFF6060, false);
        }
        if (!ClientParty.inviter.isEmpty()) {
            g.drawString(font, ClientParty.inviter + "님의 초대", x + W - 160, y + H - 18, 0xFF80FF80, false);
        }
        // 접속 중 목록
        int sx = x + W + 4;
        g.fill(sx, y, sx + SW, y + H, 0xF0141420);
        g.fill(sx, y, sx + SW, y + 1, 0xFF80E0FF);
        g.drawString(font, "접속 중", sx + 8, y + 6, 0xFF80E0FF, true);
        var online = onlineToInvite();
        if (online.isEmpty()) g.drawString(font, "초대할 사람 없음", sx + 8, y + 28, 0xFF808080, false);
        for (int i = 0; i < Math.min(8, online.size()); i++) {
            String n = online.get(i);
            if (font.width(n) > SW - 46) n = font.plainSubstrByWidth(n, SW - 50) + "…";
            g.drawString(font, n, sx + 8, y + 28 + i * 20, 0xFFFFFFFF, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!nameBox.isFocused() && ClientEvents.PARTY_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
