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
public class PartyScreen extends ScaledScreen {
    private static final int W = CharWin.W, H = CharWin.H, MR = 300, ROWH = 38;
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
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.PARTY);
        // 오른쪽: 이름 칸 + [초대] [근처] + 접속 중 목록 (+)
        nameBox = new EditBox(font, x + 318, y + 48, W - 340, 14, Component.literal("이름"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.literal("이름"));
        addRenderableWidget(nameBox);
        addRenderableWidget(Ui.button(Component.literal("초대"), b -> {
            if (!nameBox.getValue().isBlank()) send(0, nameBox.getValue().trim());
            nameBox.setValue("");
        }).bounds(x + 317, y + 68, 68, 16).accent(true).build());
        addRenderableWidget(Ui.button(Component.literal("근처"), b -> fillNearest()).bounds(x + 391, y + 68, 68, 16).build());
        int i0 = 0;
        for (String name : onlineToInvite()) {
            if (i0 >= 9) break;
            addRenderableWidget(Ui.button(Component.literal("+"), b -> send(0, name))
                    .bounds(x + W - 38, y + 106 + i0 * 17, 16, 14).accent(true).build());
            i0++;
        }
        if (!ClientParty.MEMBERS.isEmpty())
            addRenderableWidget(Ui.button(Component.literal("파티 나가기"), b -> send(3, "")).bounds(x + 86, y + 262, 76, 16).build());
        if (!ClientParty.inviter.isEmpty()) {
            addRenderableWidget(Ui.button(Component.literal("거절"), b -> send(2, "")).bounds(x + 136, y + 160, 50, 16).build());
            addRenderableWidget(Ui.button(Component.literal("수락"), b -> send(1, "")).bounds(x + 196, y + 160, 50, 16).accent(true).build());
        }
        if (amLeader()) {
            String me = Minecraft.getInstance().getUser().getName();
            for (int i = 0; i < ClientParty.MEMBERS.size(); i++) {
                Net.Member m = ClientParty.MEMBERS.get(i);
                if (m.name().equals(me)) continue;
                addRenderableWidget(Ui.button(Component.literal("추방"), b -> send(4, m.name()))
                        .bounds(x + MR - 36, y + 52 + i * ROWH + 18, 32, 14).build());
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
        CharWin.draw(g, font, x, y);
        int n = ClientParty.MEMBERS.size();
        g.drawString(font, n == 0 ? "파티 없음" : "파티 (" + n + "/4)", x + 88, y + 34, Ui.VAL, true);
        if (n == 0) {
            g.drawCenteredString(font, "파티가 없어요.", x + 192, y + 120, Ui.GRAY);
            g.drawCenteredString(font, "오른쪽에서 초대하세요", x + 192, y + 134, Ui.GRAY);
        }
        String me = Minecraft.getInstance().getUser().getName();
        for (int i = 0; i < n; i++) {
            Net.Member m = ClientParty.MEMBERS.get(i);
            int ry = y + 52 + i * ROWH;
            Ui.row(g, x + 85, ry, x + MR, ry + ROWH - 4, m.name().equals(me));
            g.drawString(font, Ui.cut(font, (m.leader() ? "★ " : "") + m.name(), 110), x + 91, ry + 5, m.leader() ? Ui.VAL : Ui.TXT, true);
            String lv = "Lv." + m.level();
            g.drawString(font, lv, x + MR - 5 - font.width(lv), ry + 5, Ui.GRAY, false);
            double r = m.maxHp() <= 0 ? 0 : Math.max(0, Math.min(1, m.hp() / m.maxHp()));
            Ui.bar(g, x + 91, ry + 21, 120, 5, (float) r, m.down() ? 0xFF808080 : 0xFFC82832);
            if (m.down()) g.drawString(font, "다운!", x + 218, ry + 19, Ui.RED, true);
            else if (!m.near()) g.drawString(font, "멀리", x + 218, ry + 19, Ui.GRAY, false);
        }
        Ui.vsep(g, x + MR + 8, y + 30, y + 282);
        g.drawString(font, "초대", x + 318, y + 34, Ui.VAL, true);
        g.drawString(font, "접속 중", x + 318, y + 92, Ui.GRAY, false);
        var online = onlineToInvite();
        if (online.isEmpty()) g.drawString(font, "없음", x + 320, y + 108, Ui.GRAY, false);
        for (int i = 0; i < Math.min(9, online.size()); i++) {
            Ui.row(g, x + 316, y + 105 + i * 17, x + W - 20, y + 121 + i * 17, false);
            g.drawString(font, Ui.cut(font, online.get(i), W - 380), x + 321, y + 109 + i * 17, Ui.TXT, false);
        }
        super.render(g, mx, my, pt);
        if (!ClientParty.inviter.isEmpty()) {
            g.pose().pushPose(); g.pose().translate(0, 0, 200);
            Ui.window(g, x + 120, y + 104, 140, 80);
            g.drawCenteredString(font, Ui.cut(font, ClientParty.inviter + " 님의", 120), x + 190, y + 122, Ui.VAL);
            g.drawCenteredString(font, "파티 초대", x + 190, y + 136, Ui.TXT);
            g.pose().popPose();
            // 버튼은 창 위에 다시
            for (var c : children()) if (c instanceof net.minecraft.client.gui.components.Button b && (b.getMessage().getString().equals("수락") || b.getMessage().getString().equals("거절"))) {
                g.pose().pushPose(); g.pose().translate(0, 0, 210); b.render(g, mx, my, pt); g.pose().popPose();
            }
        }
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
