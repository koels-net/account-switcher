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
import net.minecraft.client.multiplayer.ProfileKeyPairManager;

import java.net.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Applies a new {@link User} to the running client on 1.21.9+. */
public final class SessionCompat {
	private SessionCompat() {
	}

	public static void apply(Minecraft client, User user, UserApiService apiService, Path gameDir) {
		MinecraftAccessor accessor = (MinecraftAccessor) (Object) client;
		accessor.accountSwitcher$setUser(user);
		if (apiService != null) {
			accessor.accountSwitcher$setUserApiService(apiService);
			try {
				accessor.accountSwitcher$setProfileKeyPairManager(
						ProfileKeyPairManager.create(apiService, user, gameDir)
				);
			} catch (Throwable t) {
				AccountSwitcherClient.LOGGER.warn("Profile key refresh failed after switch", t);
			}
		}

		refreshGameProfile(accessor, UserCompat.getProfileId(user));
		refreshUserProperties(accessor, apiService);

		User active = client.getUser();
		if (active == null || !user.getName().equals(active.getName())) {
			AccountSwitcherClient.LOGGER.warn(
					"Session apply may have failed (wanted {}, active {})",
					user.getName(),
					active == null ? "null" : active.getName()
			);
		} else {
			AccountSwitcherClient.LOGGER.info("Session field now reports active user {}", active.getName());
		}
	}

	/**
	 * Rebuilds {@code Minecraft.profileFuture}, the cached local-player profile that supplies the
	 * singleplayer own-skin. It is fetched once in the client constructor from the launch account, so
	 * without this the singleplayer skin stays pinned to whatever account the game started with.
	 */
	private static void refreshGameProfile(MinecraftAccessor accessor, UUID uuid) {
		if (uuid == null) {
			return;
		}
		try {
			YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(Proxy.NO_PROXY);
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
}
