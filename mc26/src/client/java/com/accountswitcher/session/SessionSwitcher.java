package com.accountswitcher.session;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.mixin.MinecraftAccessor;
import com.accountswitcher.storage.AccountRecord;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

import java.net.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Applies a saved account to the running client without restarting (26.x).
 */
public final class SessionSwitcher {
	private SessionSwitcher() {
	}

	public static void apply(Minecraft client, AccountRecord account) {
		User user = new User(
				account.getUsername(),
				account.getUuid(),
				account.getAccessToken(),
				Optional.ofNullable(emptyToNull(account.getXuid())),
				Optional.ofNullable(emptyToNull(account.getClientId()))
		);

		MinecraftAccessor accessor = (MinecraftAccessor) (Object) client;
		accessor.accountSwitcher$setUser(user);

		try {
			YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(Proxy.NO_PROXY);
			UserApiService apiService = createUserApiService(authService, account.getAccessToken());
			accessor.accountSwitcher$setUserApiService(apiService);
			ProfileKeyPairManager keys = ProfileKeyPairManager.create(apiService, user, client.gameDirectory.toPath());
			accessor.accountSwitcher$setProfileKeyPairManager(keys);
			refreshGameProfile(accessor, authService, account.getUuid());
			refreshUserProperties(accessor, apiService);
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.warn("Could not fully refresh UserApiService / profile keys after switch", t);
		}

		account.setLastUsedAt(System.currentTimeMillis());
		account.setStatus(AccountRecord.AccountStatus.LOGGED_IN);
		AccountSwitcherClient.LOGGER.info("Switched active account to {} ({})", account.getUsername(), account.getUuid());
	}

	/**
	 * Rebuilds {@code Minecraft.profileFuture}, the cached local-player profile that supplies the
	 * singleplayer own-skin. It is fetched once in the client constructor from the launch account, so
	 * without this the singleplayer skin stays pinned to whatever account the game started with.
	 */
	private static void refreshGameProfile(MinecraftAccessor accessor, YggdrasilAuthenticationService authService, UUID uuid) {
		if (uuid == null) {
			return;
		}
		try {
			MinecraftSessionService sessionService = authService.createMinecraftSessionService();
			CompletableFuture<ProfileResult> future =
					CompletableFuture.supplyAsync(() -> sessionService.fetchProfile(uuid, true));
			accessor.accountSwitcher$setProfileFuture(future);
			AccountSwitcherClient.LOGGER.info("Refreshed cached game profile (skin) for {}", uuid);
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.warn("Could not refresh cached game profile (skin) after switch", t);
		}
	}

	/**
	 * Rebuilds {@code Minecraft.userPropertiesFuture}, the cached telemetry/social flags. Like the profile
	 * future it is fetched once in the client constructor from the launch account, so without this the
	 * telemetry opt-in and social state stay pinned to whatever account the game started with.
	 */
	private static void refreshUserProperties(MinecraftAccessor accessor, UserApiService apiService) {
		if (apiService == null) {
			return;
		}
		try {
			CompletableFuture<UserApiService.UserProperties> future = CompletableFuture.supplyAsync(() -> {
				try {
					return apiService.fetchProperties();
				} catch (AuthenticationException e) {
					AccountSwitcherClient.LOGGER.warn("Failed to fetch user properties after switch", e);
					return UserApiService.OFFLINE_PROPERTIES;
				}
			});
			accessor.accountSwitcher$setUserPropertiesFuture(future);
			AccountSwitcherClient.LOGGER.info("Refreshed cached user properties after switch");
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.warn("Could not refresh cached user properties after switch", t);
		}
	}

	private static UserApiService createUserApiService(YggdrasilAuthenticationService authService, String accessToken) {
		try {
			return authService.createUserApiService(accessToken);
		} catch (Exception e) {
			AccountSwitcherClient.LOGGER.warn("UserApiService creation failed; using offline stub", e);
			return UserApiService.OFFLINE;
		}
	}

	private static String emptyToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
