package com.accountswitcher.compat;

import net.minecraft.client.gui.GuiGraphics;

/** Rectangle fills for 1.21.9+. */
public final class GuiDraw {
	private GuiDraw() {
	}

	public static void fill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y1, color);
	}
}
