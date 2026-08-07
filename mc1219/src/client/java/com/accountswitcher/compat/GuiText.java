package com.accountswitcher.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Text drawing for 1.21.9+ (opaque ARGB + shadow). */
public final class GuiText {
	private GuiText() {
	}

	public static void draw(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		if (text == null) {
			return;
		}
		graphics.drawString(font, text, x, y, ensureOpaque(color), true);
	}

	public static void draw(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
		draw(graphics, font, text == null ? "" : text.getString(), x, y, color);
	}

	public static int ensureOpaque(int color) {
		if (((color >>> 24) & 0xFF) == 0) {
			return color | 0xFF000000;
		}
		return color;
	}
}
