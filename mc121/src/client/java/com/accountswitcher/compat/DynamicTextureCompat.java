package com.accountswitcher.compat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.lang.reflect.Constructor;
import java.util.function.Supplier;

/**
 * Creates dynamic textures across 1.21.x. Around 1.21.5+ the concrete type is
 * {@code NativeImageBackedTexture} ({@code class_1043}) with a label supplier.
 */
public final class DynamicTextureCompat {
	private static final Constructor<?> CTOR = findConstructor();

	private DynamicTextureCompat() {
	}

	private static Constructor<?> findConstructor() {
		Constructor<?> found = findOn(DynamicTexture.class);
		if (found != null) {
			return found;
		}
		// Intermediary id for NativeImageBackedTexture / legacy DynamicTexture impl
		for (String name : new String[]{
				"net.minecraft.class_1043",
				"net.minecraft.client.texture.NativeImageBackedTexture",
				"net.minecraft.client.renderer.texture.NativeImageBackedTexture"
		}) {
			try {
				found = findOn(Class.forName(name));
				if (found != null) {
					return found;
				}
			} catch (ClassNotFoundException ignored) {
			}
		}
		return null;
	}

	private static Constructor<?> findOn(Class<?> type) {
		if (type == null || type.isInterface()) {
			return null;
		}
		Constructor<?> supplierCtor = null;
		Constructor<?> imageCtor = null;
		Constructor<?> stringCtor = null;
		for (Constructor<?> candidate : type.getConstructors()) {
			Class<?>[] p = candidate.getParameterTypes();
			if (p.length == 2 && Supplier.class.isAssignableFrom(p[0]) && isNativeImage(p[1])) {
				supplierCtor = candidate;
			} else if (p.length == 1 && isNativeImage(p[0])) {
				imageCtor = candidate;
			} else if (p.length == 2 && p[0] == String.class && isNativeImage(p[1])) {
				stringCtor = candidate;
			}
		}
		if (supplierCtor != null) {
			return supplierCtor;
		}
		if (imageCtor != null) {
			return imageCtor;
		}
		return stringCtor;
	}

	private static boolean isNativeImage(Class<?> type) {
		return NativeImage.class.isAssignableFrom(type) || "NativeImage".equals(type.getSimpleName());
	}

	public static AbstractTexture create(String label, NativeImage image) {
		if (CTOR == null) {
			throw new IllegalStateException("No compatible DynamicTexture constructor on this version");
		}
		try {
			Class<?>[] p = CTOR.getParameterTypes();
			if (p.length == 1) {
				return (AbstractTexture) CTOR.newInstance(image);
			}
			if (Supplier.class.isAssignableFrom(p[0])) {
				return (AbstractTexture) CTOR.newInstance((Supplier<String>) () -> label, image);
			}
			return (AbstractTexture) CTOR.newInstance(label, image);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to create DynamicTexture", e);
		}
	}
}
