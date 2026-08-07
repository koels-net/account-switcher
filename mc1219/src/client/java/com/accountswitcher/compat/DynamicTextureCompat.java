package com.accountswitcher.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Dynamic textures on 1.21.9+ require a label supplier. */
public final class DynamicTextureCompat {
	private DynamicTextureCompat() {
	}

	public static AbstractTexture create(String label, NativeImage image) {
		return new DynamicTexture(() -> label, image);
	}
}
