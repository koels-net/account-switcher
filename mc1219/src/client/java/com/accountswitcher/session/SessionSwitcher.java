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

	private static UserApiService createUserApiService(YggdrasilAuthenticationService authService, String accessToken) {
		try {
			return authService.createUserApiService(accessToken);
		} catch (Throwable e) {
			AccountSwitcherClient.LOGGER.warn("UserApiService creation failed; using offline stub", e);
			return UserApiService.OFFLINE;
		}
	}
}
