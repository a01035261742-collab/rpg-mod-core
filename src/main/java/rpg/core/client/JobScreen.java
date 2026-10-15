package rpg.core.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.job.Jobs;
import rpg.core.net.Net;
import rpg.core.skill.Defs;

/**
 * 전직 교관 대화 창.
 * state (Jobs.state): 0 1차 전직 가능 · 1 레벨 부족 · 2 2차까지 끝 · 3 다른 직업 · 4 2차 레벨 부족 · 5 갈래 고르기 · 6 시련 중 · 7 시련 통과
 */
public class JobScreen extends Screen {
    private static final String[] NAMES = {"", "전사", "궁수", "마법사", "도적"};
    private static final String[][] DESC = {
            {},
            {"힘이 주스탯인 근접 전사.", "튼튼한 몸으로 앞에서 싸워요.", "2차: 가디언(탱커) / 버서커(딜러)"},
            {"민첩이 주스탯인 원거리 사수.", "평타로 화살을 쏘고 치명타가 특기.", "2차: 스나이퍼(딜러) / 헌터(제어)"},
            {"지력이 주스탯인 마법사. 마력으로 싸워요.", "평타로 마법탄을 날려요.", "2차: 아크메이지(딜러) / 클레릭(서포터)"},
            {"운이 주스탯인 빠른 근접 도적.", "드롭률·골드 획득이 높아요.", "2차: 어쌔신(딜러) / 트릭스터(서포터)"}};
    private final int family;
    private int state;
    private final int pending;
    private int x, y, w, h;

    public JobScreen(int family, int state, int pending) {
        super(Component.literal("전직"));
        this.family = family;
        this.state = state;
        this.pending = pending;
    }

    public static void open(int family, int state, int pending) {
        Minecraft.getInstance().setScreen(new JobScreen(family, state, pending));
    }

    private void send(int action, int arg) {
        Net.CH.sendToServer(new Net.JobAdvance(family, action, arg));
    }

    @Override
    protected void init() {
        clearWidgets();
        w = state == 5 ? 300 : 230;
        h = state == 5 ? 172 : 124;
        x = width / 2 - w / 2;
        y = height / 2 - h / 2;
        int by = y + h - 24;
        switch (state) {
            case 0 -> addRenderableWidget(Button.builder(Component.literal(NAMES[family] + "(으)로 전직하기"), b -> {
                send(0, 0);
                onClose();
            }).bounds(x + 10, by, 130, 18).build());
            case 5 -> {
                for (int b = 1; b <= 2; b++) {
                    int br = b;
                    addRenderableWidget(Button.builder(Component.literal(Jobs.branchName(family, b) + " 고르기"), bt -> send(1, br))
                            .bounds(x + 10 + (b - 1) * 145, by, 100, 18).build());
                }
            }
            case 6 -> {
                addRenderableWidget(Button.builder(Component.literal("시련 시작"), b -> {
                    send(3, 0);
                    onClose();
                }).bounds(x + 10, by, 60, 18).build());
                addRenderableWidget(Button.builder(Component.literal("갈래 바꾸기"), b -> {
                    state = 5;
                    init();
                }).bounds(x + 74, by, 66, 18).build());
                addRenderableWidget(Button.builder(Component.literal("포기"), b -> send(2, 0)).bounds(x + 144, by, 40, 18).build());
            }
            case 7 -> addRenderableWidget(Button.builder(Component.literal("2차 전직 완료"), b -> {
                send(4, 0);
                onClose();
            }).bounds(x + 10, by, 100, 18).build());
            default -> {}
        }
        addRenderableWidget(Button.builder(Component.literal("닫기"), b -> onClose()).bounds(x + w - 50, by, 40, 18).build());
    }

    /** 그 갈래 2차 스킬 이름들 (액티브 먼저, 패시브는 [패시브]) */
    private List<String> skillLines(int branch) {
        List<String> act = new ArrayList<>(), pas = new ArrayList<>();
        for (Defs.SkillDef s : Defs.SKILLS.values()) {
            if (s.job() != family || s.tier() != 2 || s.branch() != branch) continue;
            (s.passive() ? pas : act).add(s.passive() ? "· " + s.name() + " [패시브]" : "· " + s.name());
        }
        act.addAll(pas);
        return act;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(x, y, x + w, y + h, 0xF0141420);
        g.fill(x, y, x + w, y + 1, 0xFFFFD24A);
        g.drawString(font, NAMES[family] + " 교관", x + 10, y + 8, 0xFFFFD24A, true);
        int ly = y + 24;
        if (state == 5) {
            g.drawString(font, "2차 전직: 갈래를 골라요 (시련 통과 전엔 바꿀 수 있어요)", x + 10, ly, 0xFFFFFFFF, false);
            for (int b = 1; b <= 2; b++) {
                int cx = x + 10 + (b - 1) * 145, cy = ly + 16;
                g.fill(cx - 4, cy - 3, cx + 136, cy + 100, b == pending ? 0x40FFD24A : 0x30FFFFFF);
                g.drawString(font, Jobs.branchName(family, b), cx, cy, 0xFFFFD24A, true);
                g.drawString(font, Jobs.branchRole(family, b), cx, cy + 12, 0xFFA0C0FF, false);
                int sy = cy + 28;
                for (String line : skillLines(b)) { g.drawString(font, line, cx, sy, 0xFFE0E0E0, false); sy += 11; }
            }
        } else {
            if (state <= 1 || state == 3) {
                for (String line : DESC[family]) { g.drawString(font, line, x + 10, ly, 0xFFFFFFFF, false); ly += 11; }
            }
            String br = Jobs.branchName(family, pending);
            String[] msg = switch (state) {
                case 0 -> new String[]{"전직 보상: 직업 무기 · 스탯 초기화 주문서 · 물약"};
                case 1 -> new String[]{"Lv.10이 되면 다시 찾아오세요."};
                case 2 -> new String[]{"열심히 하고 있군요!"};
                case 4 -> new String[]{"잘 성장하고 있군요.", "Lv.30이 되면 2차 전직을 할 수 있어요."};
                case 6 -> new String[]{br + "의 길을 골랐군요.", "교관의 시련: 시련의 허수아비를 3분 안에", "쓰러뜨리세요. 시련을 받은 파티원과 함께 갈 수 있어요.", "쓰러지거나 시련장을 벗어나면 실패 (바로 재도전)"};
                case 7 -> new String[]{"훌륭해요! 시련을 통과했어요.", br + "(으)로 전직을 완료하세요.", "보상: 직업 무기 Lv30 · 스탯 초기화 주문서 · 물약"};
                default -> new String[]{"이미 다른 길을 걷고 있네요."};
            };
            int color = state == 0 || state == 7 ? 0xFF80FF80 : state == 6 ? 0xFFFFFFFF : 0xFFA0A0A0;
            for (String line : msg) { g.drawString(font, line, x + 10, ly + 6, color, false); ly += 11; }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
