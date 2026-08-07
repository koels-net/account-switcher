package com.accountswitcher.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * UI click sounds across 1.21.x. Never throws — a silent click beats a crash.
 * Prefer {@code AbstractWidget}'s static helper (survives SoundManager#play signature changes).
 */
public final class SoundCompat {
	private static final Method WIDGET_CLICK = findWidgetClick();

	private SoundCompat() {
	}

	private static Method findWidgetClick() {
		for (Method method : AbstractWidget.class.getMethods()) {
			if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
				continue;
			}
			if (SoundManager.class.isAssignableFrom(method.getParameterTypes()[0])
					&& method.getReturnType() == void.class) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}

	public static void playUiClick(SoundManager soundManager) {
		if (soundManager == null || WIDGET_CLICK == null) {
			return;
		}
		try {
			WIDGET_CLICK.invoke(null, soundManager);
		} catch (Throwable ignored) {
		}
	}
}
