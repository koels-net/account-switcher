package com.accountswitcher.compat;

import net.minecraft.client.gui.GuiGraphics;

import java.lang.reflect.Method;

/**
 * Safe rectangle fills for GuiGraphics across 1.21.x.
 */
public final class GuiDraw {
	private static final Method FILL_FALLBACK = findFillFallback();

	private GuiDraw() {
	}

	private static Method findFillFallback() {
		for (Method method : GuiGraphics.class.getMethods()) {
			Class<?>[] p = method.getParameterTypes();
			if (p.length == 5
					&& p[0] == int.class && p[1] == int.class && p[2] == int.class
					&& p[3] == int.class && p[4] == int.class
					&& method.getReturnType() == void.class) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}

	public static void fill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		try {
			graphics.fill(x0, y0, x1, y1, color);
			return;
		} catch (Throwable ignored) {
		}
		if (FILL_FALLBACK == null) {
			return;
		}
		try {
			FILL_FALLBACK.invoke(graphics, x0, y0, x1, y1, color);
		} catch (Throwable ignored) {
		}
	}
}
