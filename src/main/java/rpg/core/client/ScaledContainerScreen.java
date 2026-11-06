package rpg.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** 1.5배 창 (아이템 칸 있는 화면) */
public abstract class ScaledContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> implements Scaled {
    protected double uiS = 1.0;

    protected ScaledContainerScreen(T menu, Inventory inv, Component t) { super(menu, inv, t); }

    @Override public double uiScale() { return uiS; }

    @Override public void setUiScale(double s) { uiS = s; }

    @Override
    public void mouseMoved(double mx, double my) { super.mouseMoved(mx / uiS, my / uiS); }
}
