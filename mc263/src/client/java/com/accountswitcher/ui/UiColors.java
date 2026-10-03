package com.accountswitcher.ui;

/**
 * ARGB helpers — Minecraft 26.2 skips drawing text when alpha is 0.
 */
public final class UiColors {
	private UiColors() {
	}

	public static int opaque(int rgb) {
		return 0xFF000000 | (rgb & 0xFFFFFF);
	}

	public static int withAlpha(int rgb, float alpha) {
		int a = Math.min(255, Math.max(0, (int) (alpha * 255))) << 24;
		return a | (rgb & 0xFFFFFF);
	}
}
