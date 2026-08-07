package com.accountswitcher.compat;

import com.accountswitcher.AccountSwitcherClient;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import java.net.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@link GameProfile}s that carry signed Mojang texture properties so the vanilla
 * {@code SkinManager} can render the real skin (it only reads textures already present on the
 * profile — it does not fetch them from a bare uuid).
 *
 * <p>Resolution goes through {@link MinecraftSessionService#fetchProfile} (the same path the game
 * uses), runs off-thread, and is cached. A freshly logged-in account is often not yet propagated on
 * the session server, so we retry a few times before accepting a texture-less result — otherwise the
 * first fetch would cache a default skin for the whole session (fixed only by relaunching).
 */
public final class ProfileSkins {
	private static final MinecraftSessionService SESSION_SERVICE =
			new YggdrasilAuthenticationService(Proxy.NO_PROXY).createMinecraftSessionService();
	private static final Map<UUID, GameProfile> CACHE = new ConcurrentHashMap<>();
	private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();

	private static final int MAX_ATTEMPTS = 5;
	private static final long RETRY_DELAY_MS = 1500L;

	private ProfileSkins() {
	}

	public static GameProfile profileFor(UUID uuid, String username) {
		String name = username == null || username.isBlank() ? "Player" : username;
		if (uuid == null) {
			return new GameProfile(null, name);
		}
		GameProfile cached = CACHE.get(uuid);
		if (cached != null) {
			return cached;
		}
		prefetch(uuid, name);
		return new GameProfile(uuid, name);
	}

	public static void prefetch(UUID uuid, String username) {
		if (uuid == null || CACHE.containsKey(uuid) || !IN_FLIGHT.add(uuid)) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				GameProfile resolved = null;
				for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
					ProfileResult result = SESSION_SERVICE.fetchProfile(uuid, false);
					if (result != null && result.profile() != null) {
						resolved = result.profile();
						if (hasTextures(resolved)) {
							break;
						}
					}
					if (attempt < MAX_ATTEMPTS) {
						Thread.sleep(RETRY_DELAY_MS);
					}
				}
				if (resolved != null) {
					// Cache even without textures after the last attempt: the account genuinely has no
					// custom skin, so the default is the correct result and we stop retrying.
					CACHE.put(uuid, resolved);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Throwable e) {
				AccountSwitcherClient.LOGGER.debug("Skin profile resolve failed for {}", uuid, e);
			} finally {
				IN_FLIGHT.remove(uuid);
			}
		}, "account-switcher-profile");
		t.setDaemon(true);
		t.start();
	}

	private static boolean hasTextures(GameProfile profile) {
		// 1.21.4 authlib (6.x) exposes getProperties(); newer lines use the record accessor.
		return profile.getProperties().containsKey("textures");
	}
}
