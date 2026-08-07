package com.accountswitcher.session;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.compat.SessionCompat;
import com.accountswitcher.compat.UserCompat;
import com.accountswitcher.storage.AccountRecord;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Applies a saved account to the running client without restarting.
 */
public final class SessionSwitcher {
	private SessionSwitcher() {
	}

	public static void apply(Minecraft client, AccountRecord account) {
		try {
			User user = UserCompat.createMicrosoftUser(
					account.getUsername(),
					account.getUuid(),
					account.getAccessToken(),
					account.getXuid(),
					account.getClientId()
			);

			UserApiService apiService = UserApiService.OFFLINE;
			try {
				YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(Proxy.NO_PROXY);
				apiService = createUserApiService(authService, account.getAccessToken());
			} catch (Throwable t) {
				AccountSwitcherClient.LOGGER.warn("UserApiService refresh failed; continuing with session swap", t);
			}

			SessionCompat.apply(client, user, apiService, client.gameDirectory.toPath());

			account.setLastUsedAt(System.currentTimeMillis());
			account.setStatus(AccountRecord.AccountStatus.LOGGED_IN);
			AccountSwitcherClient.LOGGER.info("Switched active account to {} ({})", account.getUsername(), account.getUuid());
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.error("Account switch failed for {}", account.getUsername(), t);
			throw t instanceof RuntimeException re ? re : new IllegalStateException(t);
		}
	}

	/**
	 * Drops the client back to an offline session. Used when the account that owns the live session is
	 * deleted and no other account remains — otherwise the client would stay signed in as the removed
	 * account.
	 */
	public static void applyOffline(Minecraft client) {
		try {
			String name = "Player";
			UUID offlineId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
			User user = UserCompat.createMicrosoftUser(name, offlineId, "", null, null);
			SessionCompat.apply(client, user, UserApiService.OFFLINE, client.gameDirectory.toPath());
			AccountSwitcherClient.LOGGER.info("Signed out to offline session");
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.warn("Failed to reset to offline session after deleting active account", t);
		}
	}

	private static UserApiService createUserApiService(YggdrasilAuthenticationService authService, String accessToken) {
		try {
			return authService.createUserApiService(accessToken);
		} catch (Throwable e) {
			AccountSwitcherClient.LOGGER.warn("UserApiService creation failed; using offline stub", e);
			return UserApiService.OFFLINE;
		}
	}
}
