package com.accountswitcher.ui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/**
 * Draws a player head using vanilla {@link PlayerFaceExtractor} (correct 64×64 face UVs).
 *
 * <p>Resolves a {@link PlayerSkin} via {@link SkinManager#createLookup} and calls the
 * {@code (…, PlayerSkin, …)} overload, which is present on both 26.1 and 26.2. The
 * {@code (…, ResolvableProfile, …)} convenience overload only exists on 26.2, so calling it
 * would throw {@code NoSuchMethodError} on 26.1.
 */
public final class HeadRenderer {
	private HeadRenderer() {
	}

	public static void draw(GuiGraphicsExtractor graphics, UUID uuid, String username, int x, int y, int size) {
		PlayerFaceExtractor.extractRenderState(graphics, resolveSkin(uuid, username), x, y, size);
	}

	private static PlayerSkin resolveSkin(UUID uuid, String username) {
		// A resolved profile carries the signed textures property; SkinManager needs that to load the
		// real skin (it does not fetch textures from a bare uuid). Resolution is async + cached, so this
		// yields the default skin until the fetch lands, then the real one.
		GameProfile profile = ProfileSkins.profileFor(uuid, username);
		try {
			SkinManager skins = Minecraft.getInstance().getSkinManager();
			// createLookup returns a supplier that yields the default skin until the real one loads.
			PlayerSkin skin = skins.createLookup(profile, false).get();
			if (skin != null) {
				return skin;
			}
		} catch (Throwable ignored) {
			// Fall through to the default skin below.
		}
		return DefaultPlayerSkin.get(profile);
	}
}
