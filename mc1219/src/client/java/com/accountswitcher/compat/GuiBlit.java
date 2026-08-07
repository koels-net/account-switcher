package com.accountswitcher.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

/** Textured blit for 1.21.9+ ({@link RenderPipelines#GUI_TEXTURED}). */
public final class GuiBlit {
	private GuiBlit() {
	}

	/** Stretch an 8×8 extracted head texture to {@code size}×{@code size}. */
	public static boolean blitHead(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size) {
		if (texture == null) {
			return false;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, size, size, 8, 8);
		return true;
	}

	public static boolean blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
		if (texture == null) {
			return false;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, width, height, width, height);
		return true;
	}
}
