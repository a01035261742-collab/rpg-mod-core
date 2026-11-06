package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 1.5배 창 (일반 화면) */
public abstract class ScaledScreen extends Screen implements Scaled {
    protected double uiS = 1.0;

    protected ScaledScreen(Component t) { super(t); }

    @Override public double uiScale() { return uiS; }

    @Override public void setUiScale(double s) { uiS = s; }

    @Override
    public void mouseMoved(double mx, double my) { super.mouseMoved(mx / uiS, my / uiS); }
}
