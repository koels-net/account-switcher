package com.accountswitcher.compat;

import net.minecraft.client.User;

import java.util.Optional;
import java.util.UUID;

/** Session helpers for 1.21.9–1.21.11 (5-arg {@link User}, no account-type enum). */
public final class UserCompat {
	private UserCompat() {
	}

	public static String getName(User user) {
		return user == null ? "" : user.getName();
	}

	public static UUID getProfileId(User user) {
		return user == null ? null : user.getProfileId();
	}

	public static boolean isMicrosoftAccount(User user) {
		if (user == null) {
			return false;
		}
		String token = user.getAccessToken();
		return user.getProfileId() != null
				&& token != null
				&& !token.isBlank()
				&& !"0".equals(token);
	}

	public static User createMicrosoftUser(String username, UUID uuid, String accessToken, String xuid, String clientId) {
		return new User(
				username,
				uuid,
				accessToken,
				Optional.ofNullable(emptyToNull(xuid)),
				Optional.ofNullable(emptyToNull(clientId))
		);
	}

	private static String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
