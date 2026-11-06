package rpg.core.client;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/**
 * 캐릭터 창 (시안 ui_d1~d3): 왼쪽 세로 탭 8개 + 오른쪽 내용 판. 탭마다 화면은 따로지만 같은 자리·크기.
 * 내용 판 = (x+CX0, y+CY0) ~ (x+w-12, y+h-13)
 */
public final class CharWin {
    private CharWin() {}

    public static final String[] TABS = {"장비", "스탯", "스킬", "패시브", "퀘스트", "파티", "강화", "일일"};
    public static final int EQUIP = 0, STAT = 1, SKILL = 2, PASSIVE = 3, QUEST = 4, PARTY = 5, ENHANCE = 6, DAILY = 7;
    public static final int W = 480, H = 300, CX0 = 77, CY0 = 24;

    /** 내용 판 안쪽 시작·끝 */
    public static int left(int x) { return x + CX0 + 6; }
    public static int right(int x, int w) { return x + w - 18; }
    public static int top(int y) { return y + CY0 + 6; }
    public static int bottom(int y, int h) { return y + h - 19; }

    /** 창 + 현판 + 골드 + 내용 판 (위젯은 addTabs 로) */
    public static void draw(GuiGraphics g, Font f, int x, int y, int w, int h) {
        Ui.window(g, x, y, w, h);
        Ui.plaque(g, f, x + w / 2, y + 1, "캐릭터");
        Ui.goldBox(g, f, x + w - 14, y + 4, String.format("%,d G", ClientData.gold), true);
        Ui.inset(g, x + CX0, y + CY0, x + w - 12, y + h - 13);
    }

    public static void draw(GuiGraphics g, Font f, int x, int y) { draw(g, f, x, y, W, H); }

    /** 왼쪽 탭 버튼 8개 (sel = 지금 탭, 금색) */
    public static void addTabs(Consumer<AbstractWidget> add, int x, int y, int sel) {
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            add.accept(Ui.button(Component.literal(TABS[i]), b -> { if (k != sel) open(k); })
                    .bounds(x + 11, y + 28 + i * 22, 60, 18).accent(i == sel).build());
        }
    }

    /** 탭 열기 */
    public static void open(int k) {
        Minecraft mc = Minecraft.getInstance();
        // 화면을 닫지 않고 바로 바꿈 (닫으면 마우스가 가운데로 감)
        if (k != EQUIP && mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
        switch (k) {
            case EQUIP -> { BagScreen.wantBag = false; Net.CH.sendToServer(new Net.OpenEquip(0)); }
            case STAT -> mc.setScreen(new StatScreen());
            case SKILL -> mc.setScreen(new SkillScreen());
            case PASSIVE -> mc.setScreen(new PassiveScreen());
            case QUEST -> mc.setScreen(new QuestScreen());
            case PARTY -> mc.setScreen(new PartyScreen());
            case ENHANCE -> EnhanceScreen.request();
            case DAILY -> DailyScreen.request();
            default -> {}
        }
    }
}
