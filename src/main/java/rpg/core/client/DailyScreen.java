package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import rpg.core.net.Net;

/** 일일 퀘스트 창 (메뉴 M → 일일 퀘스트): 오늘 3개, 진행도, [완료] [다시 뽑기] */
public class DailyScreen extends ScaledScreen {
    private static int W = 300;
    private static final int ROW = 44;
    private int H = 190;
    private Net.DailySync data;
    private int x, y;

    private DailyScreen(Net.DailySync d) {
        super(Component.literal("일일 퀘스트"));
        this.data = d;
    }

    public static void request() {
        Net.CH.sendToServer(new Net.DailyAction(0, 0));
    }

    public static void receive(Net.DailySync d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DailyScreen ds) { ds.data = d; ds.rebuildWidgets(); }
        else mc.setScreen(new DailyScreen(d));
    }

    private static final int TOP = 54;

    @Override
    protected void init() {
        W = CharWin.W; H = CharWin.H;
        x = Ui.cx(width, W);
        y = Ui.cy(height, H);
        CharWin.addTabs(this::addRenderableWidget, x, y, CharWin.DAILY);
        for (int i = 0; i < data.rows().size(); i++) {
            final int k = i;
            Net.DailyRow r = data.rows().get(i);
            int ry = y + TOP + i * ROW;
            if (r.done()) continue;
            if (r.prog() >= r.need()) {
                addRenderableWidget(Ui.button(Component.literal("완료"), b -> Net.CH.sendToServer(new Net.DailyAction(1, k)))
                        .bounds(x + W - 84, ry + 12, 60, 18).accent(true).build());
            } else {
                Button re = Ui.button(Component.literal("다시 " + data.rerolls()), b -> Net.CH.sendToServer(new Net.DailyAction(2, k)))
                        .bounds(x + W - 84, ry + 12, 60, 18).build();
                re.active = data.rerolls() > 0;
                addRenderableWidget(re);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        CharWin.draw(g, font, x, y);
        g.drawString(font, "일일 퀘스트", x + 88, y + 34, Ui.VAL, true);
        String reset = "초기화까지 " + data.minutes() / 60 + "시간 " + data.minutes() % 60 + "분 · 다시 뽑기 " + data.rerolls() + "번";
        g.drawString(font, reset, x + W - 20 - font.width(reset), y + 34, Ui.GRAY, false);
        for (int i = 0; i < data.rows().size(); i++) {
            Net.DailyRow r = data.rows().get(i);
            int ry = y + TOP + i * ROW;
            Ui.row(g, x + 85, ry, x + W - 20, ry + ROW - 5, false);
            g.drawString(font, Ui.cut(font, r.label(), W - 200), x + 93, ry + 7, r.done() ? Ui.GREEN : Ui.TXT, true);
            String p = r.prog() + " / " + r.need();
            boolean full = r.prog() >= r.need();
            g.drawString(font, p, x + 93, ry + 22, full ? Ui.GREEN : Ui.GRAY, false);
            Ui.bar(g, x + 150, ry + 24, 200, 4, (float) Math.min(1.0, r.prog() / (double) Math.max(1, r.need())), full ? 0xFF5AC85A : 0xFFC89632);
            if (r.done()) g.drawString(font, "✔ 완료", x + W - 70, ry + 17, Ui.GREEN, true);
        }
        int by = y + TOP + data.rows().size() * ROW + 4;
        Ui.hsep(g, x + 85, x + W - 20, by);
        String bonus = data.bonus() ? "오늘 보너스 받음 ✔" : "3개 다 하면 보너스";
        g.drawString(font, bonus, x + 90, by + 8, data.bonus() ? Ui.GREEN : Ui.GRAY, false);
        if (!data.bonus()) {
            String[] ic = {"scroll_fragment", "red_potion", "return_scroll", "powerful_flame"};
            for (int i = 0; i < ic.length; i++) {
                int sx = x + 90 + i * 24;
                Ui.slot(g, sx, by + 22);
                var it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("rpgcore", ic[i]));
                if (it != null) g.renderItem(new net.minecraft.world.item.ItemStack(it), sx + 1, by + 23);
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
