package com.accountswitcher.ui;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.compat.GuiBlit;
import com.accountswitcher.compat.ProfileSkins;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Draws real player heads on 1.21.9+.
 * Prefer locally cached full skins (correct face UVs), then 8×8 head crops,
 * and only then vanilla lookup (which often returns Ari/Kai defaults).
 */
public final class HeadRenderer {
	private HeadRenderer() {
	}

	public static void draw(GuiGraphics graphics, UUID uuid, String username, int x, int y, int size) {
		draw(graphics, uuid, username, null, x, y, size);
	}

	public static void draw(GuiGraphics graphics, UUID uuid, String username, String skinHash, int x, int y, int size) {
		if (uuid != null) {
			AccountSwitcherClient.skins().requestHead(uuid, username, skinHash);
		}

		if (uuid != null) {
			ResourceLocation skin = AccountSwitcherClient.skins().getSkinLocation(uuid);
			if (skin != null) {
				// Samples the face + hat from a 64×64 skin — same as vanilla.
				PlayerFaceRenderer.draw(graphics, skin, x, y, size, true, false, -1);
				return;
			}
			ResourceLocation head = AccountSwitcherClient.skins().getHeadLocation(uuid);
			if (head != null) {
				GuiBlit.blitHead(graphics, head, x, y, size);
				return;
			}
		}

		GameProfile profile = ProfileSkins.profileFor(uuid, username);
		tryVanilla(graphics, profile, x, y, size);
	}

	private static void tryVanilla(GuiGraphics graphics, GameProfile profile, int x, int y, int size) {
		try {
			Supplier<PlayerSkin> lookup = Minecraft.getInstance().getSkinManager().createLookup(profile, false);
			PlayerSkin skin = lookup.get();
			if (skin == null) {
				return;
			}
			// Skip obvious defaults so we don't paint Ari/Kai over a pending cache load.
			ResourceLocation body = skin.body().texturePath();
			if (body.getNamespace().equals("minecraft") && body.getPath().startsWith("entity/player/")) {
				return;
			}
			PlayerFaceRenderer.draw(graphics, skin, x, y, size);
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.debug("Vanilla head draw failed", t);
		}
	}
}
