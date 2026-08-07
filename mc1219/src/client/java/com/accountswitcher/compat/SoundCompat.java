package com.accountswitcher.compat;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;

public final class SoundCompat {
	private SoundCompat() {
	}

	public static void playUiClick(SoundManager soundManager) {
		if (soundManager != null) {
			AbstractWidget.playButtonClickSound(soundManager);
		}
	}
}
