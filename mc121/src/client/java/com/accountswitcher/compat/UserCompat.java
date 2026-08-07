package com.accountswitcher.compat;

import net.minecraft.client.User;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code User}/{@code Session} helpers across 1.21.4–1.21.11.
 * <ul>
 *   <li>1.21.4–1.21.8: 6-arg ctor with account-type enum</li>
 *   <li>1.21.9+: 5-arg ctor (account type removed)</li>
 * </ul>
 */
public final class UserCompat {
	private static final Method ACCOUNT_TYPE_GETTER;
	private static final Method NAME_GETTER;
	private static final Method UUID_GETTER;
	private static final Object MSA_TYPE;
	private static final Constructor<?> USER_CTOR_5;
	private static final Constructor<?> USER_CTOR_6;

	static {
		Method accountType = null;
		Method name = null;
		Method uuid = null;
		for (Method method : User.class.getMethods()) {
			if (method.getParameterCount() != 0) {
				continue;
			}
			Class<?> ret = method.getReturnType();
			String n = method.getName();
			if (ret.isEnum() && ret.getEnclosingClass() == User.class) {
				accountType = method;
			} else if (ret == String.class) {
				if ("getName".equals(n) || "getUsername".equals(n)) {
					name = method;
				} else if (name == null && n.toLowerCase().contains("username")) {
					name = method;
				}
			} else if (ret == UUID.class) {
				if ("getProfileId".equals(n) || "getUuidOrNull".equals(n) || "getUuid".equals(n)) {
					uuid = method;
				} else if (uuid == null) {
					uuid = method;
				}
			}
		}
		ACCOUNT_TYPE_GETTER = accountType;
		NAME_GETTER = name;
		UUID_GETTER = uuid;

		Object msa = null;
		for (Class<?> nested : User.class.getDeclaredClasses()) {
			if (!nested.isEnum()) {
				continue;
			}
			for (Object constant : nested.getEnumConstants()) {
				if ("MSA".equals(((Enum<?>) constant).name())) {
					msa = constant;
					break;
				}
			}
			if (msa != null) {
				break;
			}
		}
		MSA_TYPE = msa;

		Constructor<?> five = null;
		Constructor<?> six = null;
		for (Constructor<?> candidate : User.class.getConstructors()) {
			Class<?>[] p = candidate.getParameterTypes();
			if (p.length == 5
					&& p[0] == String.class
					&& p[1] == UUID.class
					&& p[2] == String.class
					&& Optional.class.isAssignableFrom(p[3])
					&& Optional.class.isAssignableFrom(p[4])) {
				five = candidate;
			} else if (p.length == 6
					&& p[0] == String.class
					&& p[1] == UUID.class
					&& p[2] == String.class
					&& Optional.class.isAssignableFrom(p[3])
					&& Optional.class.isAssignableFrom(p[4])
					&& p[5].isEnum()) {
				six = candidate;
			}
		}
		USER_CTOR_5 = five;
		USER_CTOR_6 = six;
	}

	private UserCompat() {
	}

	public static String getName(User user) {
		if (user == null) {
			return "";
		}
		if (NAME_GETTER != null) {
			try {
				Object value = NAME_GETTER.invoke(user);
				return value == null ? "" : value.toString();
			} catch (ReflectiveOperationException ignored) {
			}
		}
		try {
			return user.getName();
		} catch (Throwable ignored) {
			return "";
		}
	}

	public static UUID getProfileId(User user) {
		if (user == null) {
			return null;
		}
		if (UUID_GETTER != null) {
			try {
				return (UUID) UUID_GETTER.invoke(user);
			} catch (ReflectiveOperationException ignored) {
			}
		}
		try {
			return user.getProfileId();
		} catch (Throwable ignored) {
			return null;
		}
	}

	public static boolean isMicrosoftAccount(User user) {
		if (user == null) {
			return false;
		}
		if (ACCOUNT_TYPE_GETTER != null && MSA_TYPE != null) {
			try {
				return MSA_TYPE.equals(ACCOUNT_TYPE_GETTER.invoke(user));
			} catch (ReflectiveOperationException ignored) {
			}
		}
		String token;
		try {
			token = user.getAccessToken();
		} catch (Throwable t) {
			return getProfileId(user) != null;
		}
		return getProfileId(user) != null
				&& token != null
				&& !token.isBlank()
				&& !"0".equals(token);
	}

	public static User createMicrosoftUser(String username, UUID uuid, String accessToken, String xuid, String clientId) {
		Optional<String> xuidOpt = Optional.ofNullable(emptyToNull(xuid));
		Optional<String> clientOpt = Optional.ofNullable(emptyToNull(clientId));
		try {
			if (USER_CTOR_5 != null) {
				return (User) USER_CTOR_5.newInstance(username, uuid, accessToken, xuidOpt, clientOpt);
			}
			if (USER_CTOR_6 != null) {
				Object type = resolveMsaType();
				return (User) USER_CTOR_6.newInstance(username, uuid, accessToken, xuidOpt, clientOpt, type);
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Failed to construct User/Session", e);
		}
		throw new IllegalStateException("Cannot construct User/Session on this Minecraft version (no matching constructor)");
	}

	private static Object resolveMsaType() {
		if (MSA_TYPE != null) {
			return MSA_TYPE;
		}
		for (Class<?> nested : User.class.getDeclaredClasses()) {
			if (!nested.isEnum()) {
				continue;
			}
			Object[] constants = nested.getEnumConstants();
			if (constants == null || constants.length == 0) {
				continue;
			}
			for (Object constant : constants) {
				if ("MSA".equals(((Enum<?>) constant).name())) {
					return constant;
				}
			}
			return constants[constants.length - 1];
		}
		throw new IllegalStateException("Cannot construct User/Session: missing account type enum");
	}

	private static String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
