package com.accountswitcher.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens screens on 26.1 ({@code Minecraft#setScreen}) and 26.2 ({@code Gui#setScreen}).
 */
public final class ScreenHelper {
	private ScreenHelper() {
	}

	public static void open(Screen screen) {
		Minecraft client = Minecraft.getInstance();
		try {
			Object gui = client.getClass().getField("gui").get(client);
			gui.getClass().getMethod("setScreen", Screen.class).invoke(gui, screen);
			return;
		} catch (ReflectiveOperationException ignored) {
			// Fall through to Minecraft#setScreen (26.1).
		}
		try {
			client.getClass().getMethod("setScreen", Screen.class).invoke(client, screen);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to open screen on this Minecraft version", e);
		}
	}
}
