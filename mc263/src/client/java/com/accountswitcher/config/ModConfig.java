package com.accountswitcher.config;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.storage.StoragePaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Migrating config stored at {@code .minecraft/config/account-switcher.json}.
 * Missing keys are filled with defaults; existing values are never overwritten.
 */
public final class ModConfig {
	public static final int CONFIG_VERSION = 1;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private int configVersion = CONFIG_VERSION;
	private boolean enableAccountSwitcherButton = true;
	private boolean automaticallyRefreshTokens = true;
	private boolean rememberLastAccount = true;
	private boolean hideMicrosoftEmails = true;
	private boolean enableEncryption = true;
	private boolean cacheSkins = true;
	private boolean cachePlayerHeads = true;
	private long cacheExpirationHours = 24;
	private boolean debugLogging = false;
	/** Azure AD public client ID used for Microsoft OAuth (device code + refresh). */
	private String microsoftClientId = AuthConstants.DEFAULT_CLIENT_ID;

	public static ModConfig loadOrCreate() {
		Path path = StoragePaths.modConfigFile();
		ModConfig config = new ModConfig();
		try {
			Files.createDirectories(path.getParent());
			if (!Files.exists(path)) {
				config.save();
				return config;
			}
			JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			config.applyMissingDefaults(json);
			config.save(); // persist newly added keys without clobbering existing ones
			return config;
		} catch (Exception e) {
			AccountSwitcherClient.LOGGER.error("Failed to load account-switcher.json; using defaults", e);
			return config;
		}
	}

	private void applyMissingDefaults(JsonObject json) {
		configVersion = getInt(json, "configVersion", CONFIG_VERSION);
		enableAccountSwitcherButton = getBool(json, "enableAccountSwitcherButton", enableAccountSwitcherButton);
		automaticallyRefreshTokens = getBool(json, "automaticallyRefreshTokens", automaticallyRefreshTokens);
		rememberLastAccount = getBool(json, "rememberLastAccount", rememberLastAccount);
		hideMicrosoftEmails = getBool(json, "hideMicrosoftEmails", hideMicrosoftEmails);
		enableEncryption = getBool(json, "enableEncryption", enableEncryption);
		cacheSkins = getBool(json, "cacheSkins", cacheSkins);
		cachePlayerHeads = getBool(json, "cachePlayerHeads", cachePlayerHeads);
		cacheExpirationHours = getLong(json, "cacheExpirationHours", cacheExpirationHours);
		debugLogging = getBool(json, "debugLogging", debugLogging);
		if (json.has("microsoftClientId") && !json.get("microsoftClientId").isJsonNull()) {
			String id = json.get("microsoftClientId").getAsString();
			if (id != null && !id.isBlank()) {
				microsoftClientId = id.trim();
			}
		}
		// Migrate away from client ids shipped by earlier builds that no longer work.
		microsoftClientId = AuthConstants.resolveClientId(microsoftClientId);
		configVersion = CONFIG_VERSION;
	}

	public synchronized void save() {
		Path path = StoragePaths.modConfigFile();
		try {
			Files.createDirectories(path.getParent());
			JsonObject json = new JsonObject();
			json.addProperty("configVersion", configVersion);
			json.addProperty("enableAccountSwitcherButton", enableAccountSwitcherButton);
			json.addProperty("automaticallyRefreshTokens", automaticallyRefreshTokens);
			json.addProperty("rememberLastAccount", rememberLastAccount);
			json.addProperty("hideMicrosoftEmails", hideMicrosoftEmails);
			json.addProperty("enableEncryption", enableEncryption);
			json.addProperty("cacheSkins", cacheSkins);
			json.addProperty("cachePlayerHeads", cachePlayerHeads);
			json.addProperty("cacheExpirationHours", cacheExpirationHours);
			json.addProperty("debugLogging", debugLogging);
			json.addProperty("microsoftClientId", microsoftClientId);

			Path temp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
			try {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (Exception e) {
			AccountSwitcherClient.LOGGER.error("Failed to save account-switcher.json", e);
		}
	}

	private static boolean getBool(JsonObject json, String key, boolean fallback) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
	}

	private static int getInt(JsonObject json, String key, int fallback) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;
	}

	private static long getLong(JsonObject json, String key, long fallback) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : fallback;
	}

	public boolean isEnableAccountSwitcherButton() {
		return enableAccountSwitcherButton;
	}

	public boolean isAutomaticallyRefreshTokens() {
		return automaticallyRefreshTokens;
	}

	public boolean isRememberLastAccount() {
		return rememberLastAccount;
	}

	public boolean isHideMicrosoftEmails() {
		return hideMicrosoftEmails;
	}

	public boolean isEnableEncryption() {
		return enableEncryption;
	}

	public boolean isCacheSkins() {
		return cacheSkins;
	}

	public boolean isCachePlayerHeads() {
		return cachePlayerHeads;
	}

	public long getCacheExpirationHours() {
		return cacheExpirationHours;
	}

	public boolean isDebugLogging() {
		return debugLogging;
	}

	public String getMicrosoftClientId() {
		return microsoftClientId;
	}
}
