package com.accountswitcher.compat;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.mixin.MinecraftAccessor;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Applies a new session to {@link Minecraft}. Prefers the mixin accessor (strips {@code final});
 * falls back to Unsafe field writes when needed on newer 1.21.x builds.
 */
public final class SessionCompat {
	private static final Unsafe UNSAFE = findUnsafe();

	private SessionCompat() {
	}

	public static void apply(Minecraft client, User user, UserApiService apiService, Path gameDir) {
		boolean usedMixin = tryMixin(client, user, apiService);
		if (!usedMixin) {
			AccountSwitcherClient.LOGGER.info("Mixin session apply unavailable; using reflective field writes");
			setTypedField(client, User.class, user);
			if (apiService != null) {
				setTypedField(client, UserApiService.class, apiService);
			}
		}

		Object keys = createProfileKeys(apiService, user, gameDir);
		if (keys != null) {
			setAssignableField(client, keys);
		}

		refreshGameProfile(client, user);
		refreshUserProperties(client, apiService);

		User active = client.getUser();
		String wanted = UserCompat.getName(user);
		String actual = UserCompat.getName(active);
		UUID wantedId = UserCompat.getProfileId(user);
		UUID actualId = UserCompat.getProfileId(active);
		boolean idOk = wantedId == null || wantedId.equals(actualId);
		if (active == null || (!wanted.isEmpty() && !wanted.equals(actual)) || !idOk) {
			AccountSwitcherClient.LOGGER.warn(
					"Session apply may have failed (wanted {} / {}, active {} / {})",
					wanted,
					wantedId,
					actual,
					actualId
			);
		} else {
			AccountSwitcherClient.LOGGER.info("Session field now reports active user {}", actual);
		}
	}

	/**
	 * Rebuilds {@code Minecraft.profileFuture}, the cached local-player profile that supplies the
	 * singleplayer own-skin. It is fetched once in the client constructor from the launch account, so
	 * without this the singleplayer skin stays pinned to whatever account the game started with.
	 * Prefers the mixin accessor (strips {@code final}); falls back to a reflective write when the field
	 * is unambiguously typed.
	 */
	private static void refreshGameProfile(Minecraft client, User user) {
		UUID uuid = UserCompat.getProfileId(user);
		if (uuid == null) {
			return;
		}
		CompletableFuture<ProfileResult> future;
		try {
			YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(Proxy.NO_PROXY);
			MinecraftSessionService sessionService = authService.createMinecraftSessionService();
			future = CompletableFuture.supplyAsync(() -> sessionService.fetchProfile(uuid, true));
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.warn("Could not build refreshed game profile (skin) after switch", t);
			return;
		}

		try {
			((MinecraftAccessor) (Object) client).accountSwitcher$setProfileFuture(future);
			AccountSwitcherClient.LOGGER.info("Refreshed cached game profile (skin) for {}", uuid);
			return;
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.info("Mixin profileFuture apply unavailable; using reflective field write");
		}

		if (setUniqueFieldOfType(client, CompletableFuture.class, future)) {
			AccountSwitcherClient.LOGGER.info("Refreshed cached game profile (skin) via reflection for {}", uuid);
		} else {
			AccountSwitcherClient.LOGGER.warn("Could not locate a unique profileFuture field to refresh skin");
		}
	}

	/**
	 * Rebuilds {@code Minecraft.userPropertiesFuture}, the cached telemetry/social flags. Like the profile
	 * future it is fetched once in the client constructor from the launch account, so without this the
	 * telemetry opt-in and social state stay pinned to whatever account the game started with. Prefers the
	 * mixin accessor; the reflective fallback declines when the field type is ambiguous.
	 */
	private static void refreshUserProperties(Minecraft client, UserApiService apiService) {
		if (apiService == null) {
			return;
		}
		CompletableFuture<UserApiService.UserProperties> future = CompletableFuture.supplyAsync(() -> {
			try {
				return apiService.fetchProperties();
			} catch (AuthenticationException e) {
				AccountSwitcherClient.LOGGER.warn("Failed to fetch user properties after switch", e);
				return UserApiService.OFFLINE_PROPERTIES;
			}
		});

		try {
			((MinecraftAccessor) (Object) client).accountSwitcher$setUserPropertiesFuture(future);
			AccountSwitcherClient.LOGGER.info("Refreshed cached user properties after switch");
			return;
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.info("Mixin userPropertiesFuture apply unavailable; using reflective field write");
		}

		if (setUniqueFieldOfType(client, CompletableFuture.class, future)) {
			AccountSwitcherClient.LOGGER.info("Refreshed cached user properties via reflection after switch");
		} else {
			AccountSwitcherClient.LOGGER.warn("Could not locate a unique userPropertiesFuture field to refresh");
		}
	}

	private static boolean setUniqueFieldOfType(Object owner, Class<?> type, Object value) {
		Field match = null;
		for (Field field : owner.getClass().getDeclaredFields()) {
			if (field.getType() == type) {
				if (match != null) {
					return false; // ambiguous — refuse to guess
				}
				match = field;
			}
		}
		return match != null && setField(match, owner, value);
	}

	private static boolean tryMixin(Minecraft client, User user, UserApiService apiService) {
		try {
			MinecraftAccessor accessor = (MinecraftAccessor) (Object) client;
			accessor.accountSwitcher$setUser(user);
			if (apiService != null) {
				accessor.accountSwitcher$setUserApiService(apiService);
			}
			return true;
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.warn("Mixin session apply failed", t);
			return false;
		}
	}

	private static Object createProfileKeys(UserApiService apiService, User user, Path gameDir) {
		if (apiService == null || user == null || gameDir == null) {
			return null;
		}
		Class<?>[] holders = {
				safeClass("net.minecraft.client.multiplayer.ProfileKeyPairManager"),
				safeClass("net.minecraft.client.session.ProfileKeys"),
				safeClass("net.minecraft.class_7853")
		};
		for (Class<?> holder : holders) {
			if (holder == null) {
				continue;
			}
			Object created = invokeCreate(holder, apiService, user, gameDir);
			if (created != null) {
				return created;
			}
		}
		return null;
	}

	private static Object invokeCreate(Class<?> holder, UserApiService apiService, User user, Path gameDir) {
		try {
			for (Method method : holder.getMethods()) {
				if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 3) {
					continue;
				}
				Class<?>[] p = method.getParameterTypes();
				if (UserApiService.class.isAssignableFrom(p[0])
						&& p[1].isInstance(user)
						&& Path.class.isAssignableFrom(p[2])) {
					return method.invoke(null, apiService, user, gameDir);
				}
			}
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.debug("Profile key create failed on {}", holder.getName(), t);
		}
		return null;
	}

	private static Class<?> safeClass(String name) {
		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	private static void setTypedField(Object owner, Class<?> type, Object value) {
		for (Field field : owner.getClass().getDeclaredFields()) {
			if (!(field.getType() == type || type.isAssignableFrom(field.getType()) || field.getType().isAssignableFrom(type))) {
				continue;
			}
			if (value != null && !field.getType().isInstance(value) && !type.isInstance(value)) {
				continue;
			}
			if (setField(field, owner, value)) {
				AccountSwitcherClient.LOGGER.info("Set Minecraft.{} via reflection", field.getName());
				return;
			}
		}
		AccountSwitcherClient.LOGGER.error("Could not set Minecraft field of type {}", type.getName());
	}

	private static void setAssignableField(Object owner, Object value) {
		Class<?> valueType = value.getClass();
		for (Field field : owner.getClass().getDeclaredFields()) {
			Class<?> fieldType = field.getType();
			if (!fieldType.isAssignableFrom(valueType)) {
				continue;
			}
			String n = fieldType.getSimpleName();
			if (fieldType.isInterface() || n.contains("ProfileKey") || n.contains("KeyPair") || n.contains("Keys")) {
				if (setField(field, owner, value)) {
					return;
				}
			}
		}
		// Last resort: intermediary field_39068
		try {
			Field field = owner.getClass().getDeclaredField("field_39068");
			setField(field, owner, value);
		} catch (NoSuchFieldException ignored) {
		}
	}

	private static boolean setField(Field field, Object owner, Object value) {
		try {
			field.setAccessible(true);
			if (UNSAFE != null) {
				long offset = UNSAFE.objectFieldOffset(field);
				UNSAFE.putObject(owner, offset, value);
				return true;
			}
			field.set(owner, value);
			return true;
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.warn("Failed setting field {}", field.getName(), t);
			return false;
		}
	}

	private static Unsafe findUnsafe() {
		try {
			Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			return (Unsafe) theUnsafe.get(null);
		} catch (Throwable t) {
			return null;
		}
	}
}
