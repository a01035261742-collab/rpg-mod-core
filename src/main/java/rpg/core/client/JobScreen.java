package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 전직 교관 대화 창 */
public class JobScreen extends Screen {
    private static final String[] NAMES = {"", "전사", "궁수", "마법사", "도적"};
    private static final String[][] DESC = {
            {},
            {"힘이 주스탯인 근접 전사.", "튼튼한 몸으로 앞에서 싸워요.", "2차: 가디언(탱) / 버서커(딜)"},
            {"민첩이 주스탯인 원거리 사수.", "평타로 화살을 쏘고 치명타가 특기.", "2차: 스나이퍼(딜) / 음유시인(서폿)"},
            {"지력이 주스탯인 마법사. 마력으로 싸워요.", "평타로 마법탄을 날려요.", "2차: 아크메이지(딜) / 클레릭(서폿)"},
            {"운이 주스탯인 빠른 근접 도적.", "드롭률·골드 획득이 높아요.", "2차: 어쌔신(딜) / 섀도우댄서(탱)"}};
    private final int family, state;
    private int x, y;

    public JobScreen(int family, int state) {
        super(Component.literal("전직"));
        this.family = family;
        this.state = state;
    }

    public static void open(int family, int state) {
        Minecraft.getInstance().setScreen(new JobScreen(family, state));
    }

    @Override
    protected void init() {
        x = width / 2 - 110;
        y = height / 2 - 60;
        if (state == 0) {
            addRenderableWidget(Button.builder(Component.literal(NAMES[family] + "(으)로 전직하기"), b -> {
                Net.CH.sendToServer(new Net.JobAdvance(family));
                onClose();
            }).bounds(x + 10, y + 92, 130, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("닫기"), b -> onClose()).bounds(x + 150, y + 92, 60, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x, y, x + 220, y + 120, 0xF0141420);
        g.fill(x, y, x + 220, y + 1, 0xFFFFD24A);
        g.drawString(font, NAMES[family] + " 교관", x + 10, y + 8, 0xFFFFD24A, true);
        int ly = y + 24;
        for (String line : DESC[family]) { g.drawString(font, line, x + 10, ly, 0xFFFFFFFF, false); ly += 11; }
        String msg = switch (state) {
            case 0 -> "전직 보상: 직업 무기 · 스탯 초기화 주문서 · 물약";
            case 1 -> "Lv.10이 되면 다시 찾아오세요.";
            case 2 -> "열심히 하고 있군요!";
            default -> "이미 다른 길을 걷고 있네요.";
        };
        g.drawString(font, msg, x + 10, ly + 6, state == 0 ? 0xFF80FF80 : 0xFFA0A0A0, false);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
