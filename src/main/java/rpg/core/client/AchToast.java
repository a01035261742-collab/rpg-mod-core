package rpg.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.world.item.ItemStack;

/** 업적 달성 알림: 오른쪽 위에서 밀려 나왔다 들어감 (마크 발전과제 알림 자리) — 훈장 그림 또는 골드 주머니 */
public class AchToast implements Toast {
    private final String name, reward;
    private final ItemStack icon;
    private final boolean medal;

    public AchToast(String name, String reward, String medalId) {
        this.name = name;
        this.reward = reward;
        this.medal = !medalId.isEmpty();
        ItemStack s = medal ? rpg.core.item.ItemIds.stack(rpg.core.RpgCore.MODID + ":" + medalId, 1) : ItemStack.EMPTY;
        this.icon = !s.isEmpty() ? s : new ItemStack(rpg.core.item.ModItems.GOLD_POUCH.get());
    }

    public static void show(String name, String reward, String medalId) {
        net.minecraft.client.Minecraft.getInstance().getToasts().addToast(new AchToast(name, reward, medalId));
    }

    @Override public int width() { return 170; }
    @Override public int height() { return 32; }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent tc, long time) {
        var font = tc.getMinecraft().font;
        int w = width(), h = height();
        g.fill(0, 0, w, h, Ui.G3);
        g.fill(1, 1, w - 1, h - 1, 0xFF1A080E);
        g.renderItem(icon, 8, 8);
        g.drawString(font, "업적 달성", 30, 6, Ui.GRAY, false);
        g.drawString(font, Ui.cut(font, name, w - 36), 30, 17, Ui.VAL, true);
        if (!reward.isEmpty()) {
            String r = Ui.cut(font, reward, 70);
            g.drawString(font, r, w - 5 - font.width(r), 6, medal ? 0xFFFFA040 : Ui.VAL, false);
        }
        return time >= 4000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
