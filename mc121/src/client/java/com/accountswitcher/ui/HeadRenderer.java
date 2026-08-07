package com.accountswitcher.ui;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.compat.GuiBlit;
import com.accountswitcher.compat.ProfileSkins;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Draws player heads for 1.21.4–1.21.8. Prefers local cache, then vanilla face APIs.
 */
public final class HeadRenderer {
	private static final Method SKIN_PROVIDER = findSkinProvider();
	private static final Method SKIN_LOOKUP = findSkinLookup();
	private static final Method TEXTURE_GETTER = findTextureGetter();
	private static final Method FACE_DRAW = findFaceDraw();

	private HeadRenderer() {
	}

	public static void draw(GuiGraphics graphics, UUID uuid, String username, int x, int y, int size) {
		if (uuid != null) {
			AccountSwitcherClient.skins().requestHead(uuid, username, null);
			ResourceLocation cached = AccountSwitcherClient.skins().getHeadLocation(uuid);
			if (cached != null && GuiBlit.blit(graphics, cached, x, y, size, size)) {
				return;
			}
		}

		GameProfile profile = ProfileSkins.profileFor(uuid, username);
		if (tryVanilla(graphics, profile, x, y, size)) {
			return;
		}
		tryReflective(graphics, profile, x, y, size);
	}

	private static boolean tryVanilla(GuiGraphics graphics, GameProfile profile, int x, int y, int size) {
		try {
			SkinManager skins = Minecraft.getInstance().getSkinManager();
			PlayerSkin skin = skins.getInsecureSkin(profile);
			PlayerFaceRenderer.draw(graphics, skin, x, y, size);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static void tryReflective(GuiGraphics graphics, GameProfile profile, int x, int y, int size) {
		try {
			if (SKIN_PROVIDER == null || SKIN_LOOKUP == null) {
				return;
			}
			Object provider = SKIN_PROVIDER.invoke(Minecraft.getInstance());
			Object skin = SKIN_LOOKUP.invoke(provider, profile);
			if (skin == null) {
				return;
			}
			if (FACE_DRAW != null) {
				FACE_DRAW.invoke(null, graphics, skin, x, y, size);
				return;
			}
			if (TEXTURE_GETTER != null) {
				Object texture = TEXTURE_GETTER.invoke(skin);
				if (texture instanceof ResourceLocation location) {
					GuiBlit.blit(graphics, location, x, y, size, size);
				}
			}
		} catch (Throwable ignored) {
		}
	}

	private static Method findSkinProvider() {
		for (Method method : Minecraft.class.getMethods()) {
			if (method.getParameterCount() != 0) {
				continue;
			}
			String simple = method.getReturnType().getSimpleName();
			if (simple != null && simple.contains("Skin")
					&& (simple.contains("Manager") || simple.contains("Provider"))) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}

	private static Method findSkinLookup() {
		Class<?> type = SKIN_PROVIDER != null ? SKIN_PROVIDER.getReturnType() : SkinManager.class;
		Method best = null;
		for (Method method : type.getMethods()) {
			if (method.getParameterCount() != 1 || !GameProfile.class.isAssignableFrom(method.getParameterTypes()[0])) {
				continue;
			}
			String ret = method.getReturnType().getName();
			if (ret.contains("CompletableFuture") || ret.contains("Supplier")) {
				continue;
			}
			String simple = method.getReturnType().getSimpleName();
			if (simple != null && (simple.contains("Skin") || simple.contains("Textures"))) {
				best = method;
				if ("PlayerSkin".equals(simple) || "SkinTextures".equals(simple)) {
					break;
				}
			}
		}
		if (best != null) {
			best.setAccessible(true);
		}
		return best;
	}

	private static Method findTextureGetter() {
		if (SKIN_LOOKUP == null) {
			return null;
		}
		Class<?> skinType = SKIN_LOOKUP.getReturnType();
		for (Method method : skinType.getMethods()) {
			if (method.getParameterCount() != 0) {
				continue;
			}
			if (ResourceLocation.class.isAssignableFrom(method.getReturnType())) {
				String n = method.getName().toLowerCase();
				if (n.contains("texture") || n.equals("texture") || n.contains("body")) {
					method.setAccessible(true);
					return method;
				}
			}
		}
		for (Method method : skinType.getMethods()) {
			if (method.getParameterCount() == 0 && ResourceLocation.class.isAssignableFrom(method.getReturnType())) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}

	private static Method findFaceDraw() {
		Method fromLegacy = findDrawOn(PlayerFaceRenderer.class);
		if (fromLegacy != null) {
			return fromLegacy;
		}
		for (String name : new String[]{
				"net.minecraft.class_7532",
				"net.minecraft.client.gui.PlayerSkinDrawer",
				"net.minecraft.client.gui.components.PlayerFaceRenderer"
		}) {
			try {
				Method method = findDrawOn(Class.forName(name));
				if (method != null) {
					return method;
				}
			} catch (ClassNotFoundException ignored) {
			}
		}
		return null;
	}

	private static Method findDrawOn(Class<?> type) {
		for (Method method : type.getMethods()) {
			Class<?>[] p = method.getParameterTypes();
			if (p.length == 5
					&& GuiGraphics.class.isAssignableFrom(p[0])
					&& p[2] == int.class && p[3] == int.class && p[4] == int.class) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}
}
