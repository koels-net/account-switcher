package com.accountswitcher.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

/**
 * Cross-1.21.x text drawing. Mojang removed the 5-arg {@code drawString} overload
 * (no shadow flag) after 1.21.5 — matching by parameter types avoids intermediary drift.
 */
public final class GuiText {
	private static final Method DRAW = findDrawMethod();

	private GuiText() {
	}

	private static Method findDrawMethod() {
		Method withShadow = null;
		Method withoutShadow = null;
		for (Method method : GuiGraphics.class.getMethods()) {
			Class<?>[] params = method.getParameterTypes();
			if (!Font.class.isAssignableFrom(params.length > 0 ? params[0] : Object.class)) {
				continue;
			}
			if (params.length == 6
					&& params[1] == String.class
					&& params[2] == int.class
					&& params[3] == int.class
					&& params[4] == int.class
					&& params[5] == boolean.class) {
				withShadow = method;
			} else if (params.length == 5
					&& params[1] == String.class
					&& params[2] == int.class
					&& params[3] == int.class
					&& params[4] == int.class) {
				withoutShadow = method;
			}
		}
		Method chosen = withShadow != null ? withShadow : withoutShadow;
		if (chosen != null) {
			chosen.setAccessible(true);
		}
		return chosen;
	}

	public static void draw(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
		if (text == null || DRAW == null) {
			return;
		}
		int argb = ensureOpaque(color);
		try {
			if (DRAW.getParameterCount() == 6) {
				DRAW.invoke(graphics, font, text, x, y, argb, true);
			} else {
				DRAW.invoke(graphics, font, text, x, y, argb);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to draw text on this Minecraft version", e);
		}
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
