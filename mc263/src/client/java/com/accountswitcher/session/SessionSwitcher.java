package com.accountswitcher.session;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.mixin.MinecraftAccessor;
import com.accountswitcher.storage.AccountRecord;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.ProfileResult;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

import java.net.Proxy;
import java.nio.charset.StandardCharsets;
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
			MinecraftServicesDiscoveryService authService = MinecraftServicesDiscoveryService.create(Proxy.NO_PROXY);
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
	private static void refreshGameProfile(MinecraftAccessor accessor, MinecraftServicesDiscoveryService authService, UUID uuid) {
		if (uuid == null) {
			return;
		}
		try {
			SessionService sessionService = authService.createMinecraftSessionService();
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

	/**
	 * Drops the client back to an offline session. Used when the account that owns the live session is
	 * deleted and no other account remains — otherwise the client would stay signed in as the removed
	 * account.
	 */
	public static void applyOffline(Minecraft client) {
		String name = "Player";
		UUID offlineId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
		User user = new User(name, offlineId, "", Optional.empty(), Optional.empty());
		MinecraftAccessor accessor = (MinecraftAccessor) (Object) client;
		accessor.accountSwitcher$setUser(user);
		try {
			accessor.accountSwitcher$setUserApiService(UserApiService.OFFLINE);
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.warn("Could not reset UserApiService when signing out", t);
		}
		AccountSwitcherClient.LOGGER.info("Signed out to offline session");
	}

	private static UserApiService createUserApiService(MinecraftServicesDiscoveryService authService, String accessToken) {
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
