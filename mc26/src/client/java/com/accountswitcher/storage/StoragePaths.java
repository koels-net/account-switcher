package com.accountswitcher.storage;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Resolves {@code .minecraft/config/account-switcher/} and related paths.
 */
public final class StoragePaths {
	private static Path root;
	private static Path cacheRoot;
	private static Path skinsDir;
	private static Path headsDir;

	private StoragePaths() {
	}

	public static void init() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		root = configDir.resolve("account-switcher");
		cacheRoot = root.resolve("cache");
		skinsDir = cacheRoot.resolve("skins");
		headsDir = cacheRoot.resolve("heads");
	}

	public static Path root() {
		return root;
	}

	public static Path cacheRoot() {
		return cacheRoot;
	}

	public static Path accountsFile() {
		return root.resolve("accounts.json");
	}

	public static Path encryptionFile() {
		return root.resolve("encryption.dat");
	}

	public static Path cacheIndexFile() {
		return cacheRoot.resolve("index.json");
	}

	public static Path skinsDir() {
		return skinsDir;
	}

	public static Path headsDir() {
		return headsDir;
	}

	/** Global mod settings live beside the data folder, per project spec. */
	public static Path modConfigFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("account-switcher.json");
	}
}
