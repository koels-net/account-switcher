package com.accountswitcher.cache;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.config.ModConfig;
import com.accountswitcher.storage.StoragePaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.accountswitcher.compat.DynamicTextureCompat;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local skin/head cache under {@code config/account-switcher/cache/}.
 * Heads are keyed by skin texture hash so identical skins are not duplicated.
 */
public final class SkinCacheManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String SESSION_PROFILE = "https://sessionserver.mojang.com/session/minecraft/profile/";
	private static final String TEXTURE_CDN = "https://textures.minecraft.net/texture/";

	private final ModConfig config;
	private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "account-switcher-skin");
		t.setDaemon(true);
		return t;
	});

	private final Map<String, CacheEntry> index = new ConcurrentHashMap<>();
	private final Map<String, ResourceLocation> loadedHeads = new ConcurrentHashMap<>();
	private final Map<UUID, String> uuidToHash = new ConcurrentHashMap<>();

	public SkinCacheManager(ModConfig config) {
		this.config = config;
		loadIndex();
	}

	public ResourceLocation getHeadLocation(UUID uuid) {
		String hash = uuidToHash.get(uuid);
		if (hash != null) {
			ResourceLocation existing = loadedHeads.get(hash);
			if (existing != null) {
				return existing;
			}
			Path headFile = StoragePaths.headsDir().resolve(hash + ".png");
			if (Files.isRegularFile(headFile)) {
				ResourceLocation loc = registerHeadTexture(hash, headFile);
				if (loc != null) {
					return loc;
				}
			}
		}
		return null;
	}

	/**
	 * Immediately use cache when present; refresh in the background when stale/missing.
	 */
	public void requestHead(UUID uuid, String username, String knownHash) {
		if (!config.isCachePlayerHeads() || uuid == null) {
			return;
		}
		if (knownHash != null && !knownHash.isBlank()) {
			uuidToHash.put(uuid, knownHash);
			Path head = StoragePaths.headsDir().resolve(knownHash + ".png");
			if (Files.isRegularFile(head)) {
				registerHeadTexture(knownHash, head);
				CacheEntry entry = index.get(knownHash);
				if (entry != null && !isExpired(entry) && knownHash.equals(entry.textureHash)) {
					return;
				}
			}
		}
		executor.execute(() -> {
			try {
				refresh(uuid, username, knownHash);
			} catch (Exception e) {
				if (config.isDebugLogging()) {
					AccountSwitcherClient.LOGGER.debug("Skin refresh failed for {}", uuid, e);
				}
			}
		});
	}

	private void refresh(UUID uuid, String username, String knownHash) throws Exception {
		Files.createDirectories(StoragePaths.skinsDir());
		Files.createDirectories(StoragePaths.headsDir());

		String hash = knownHash;
		if (hash == null || hash.isBlank()) {
			hash = fetchTextureHash(uuid);
		}
		if (hash == null || hash.isBlank()) {
			uuidToHash.put(uuid, "default");
			ensureDefaultHead();
			return;
		}

		CacheEntry existing = index.get(hash);
		Path skinFile = StoragePaths.skinsDir().resolve(hash + ".png");
		Path headFile = StoragePaths.headsDir().resolve(hash + ".png");

		if (existing != null && Files.isRegularFile(headFile) && !isExpired(existing)) {
			uuidToHash.put(uuid, hash);
			registerHeadTexture(hash, headFile);
			return;
		}

		if (!Files.isRegularFile(skinFile)) {
			downloadTexture(TEXTURE_CDN + hash, skinFile);
		}
		extractHead(skinFile, headFile);

		CacheEntry entry = new CacheEntry();
		entry.textureHash = hash;
		entry.username = username;
		entry.uuid = uuid.toString();
		entry.lastUpdated = System.currentTimeMillis();
		index.put(hash, entry);
		uuidToHash.put(uuid, hash);
		saveIndex();
		registerHeadTexture(hash, headFile);
	}

	private String fetchTextureHash(UUID uuid) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(SESSION_PROFILE + uuid.toString().replace("-", "")).toURL().openConnection();
		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(15_000);
		connection.setRequestProperty("Accept", "application/json");
		if (connection.getResponseCode() != 200) {
			return null;
		}
		try (InputStream in = connection.getInputStream()) {
			JsonObject profile = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
			if (!profile.has("properties")) {
				return null;
			}
			for (var el : profile.getAsJsonArray("properties")) {
				JsonObject prop = el.getAsJsonObject();
				if (!"textures".equals(prop.get("name").getAsString())) {
					continue;
				}
				String decoded = new String(java.util.Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8);
				JsonObject textures = JsonParser.parseString(decoded).getAsJsonObject();
				if (!textures.has("textures") || !textures.getAsJsonObject("textures").has("SKIN")) {
					return null;
				}
				String url = textures.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
				int slash = url.lastIndexOf('/');
				return slash >= 0 ? url.substring(slash + 1) : url;
			}
		}
		return null;
	}

	private static void downloadTexture(String url, Path target) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(20_000);
		try (InputStream in = connection.getInputStream()) {
			Files.write(target, in.readAllBytes());
		}
	}

	private static void extractHead(Path skinFile, Path headFile) throws IOException {
		try (NativeImage skin = NativeImage.read(Files.newInputStream(skinFile));
			 NativeImage head = new NativeImage(8, 8, false)) {
			// Base face (8x8 at 8,8)
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) {
					head.setPixel(x, y, skin.getPixel(8 + x, 8 + y));
				}
			}
			// Overlay hat layer (8x8 at 40,8) when opaque
			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) {
					int overlay = skin.getPixel(40 + x, 8 + y);
					int alpha = (overlay >>> 24) & 0xFF;
					if (alpha > 16) {
						head.setPixel(x, y, overlay);
					}
				}
			}
			head.writeToFile(headFile);
		}
	}

	private void ensureDefaultHead() {
		// Steve-colored placeholder generated once
		Path headFile = StoragePaths.headsDir().resolve("default.png");
		try {
			Files.createDirectories(StoragePaths.headsDir());
			if (!Files.exists(headFile)) {
				try (NativeImage head = new NativeImage(8, 8, false)) {
					int skin = 0xFFC4956A;
					int hair = 0xFF3B2A1F;
					for (int y = 0; y < 8; y++) {
						for (int x = 0; x < 8; x++) {
							head.setPixel(x, y, y < 2 ? hair : skin);
						}
					}
					head.writeToFile(headFile);
				}
			}
			registerHeadTexture("default", headFile);
		} catch (IOException e) {
			AccountSwitcherClient.LOGGER.warn("Failed to create default head", e);
		}
	}

	private ResourceLocation registerHeadTexture(String hash, Path file) {
		ResourceLocation existing = loadedHeads.get(hash);
		if (existing != null) {
			return existing;
		}
		try {
			NativeImage image;
			try (InputStream in = Files.newInputStream(file)) {
				image = NativeImage.read(in);
			}
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
					AccountSwitcherClient.MOD_ID,
					"head/" + hash
			);
			Runnable register = () -> {
				try {
					AbstractTexture texture = DynamicTextureCompat.create("account-switcher/" + hash, image);
					Minecraft.getInstance().getTextureManager().register(id, texture);
					loadedHeads.put(hash, id);
				} catch (Exception e) {
					AccountSwitcherClient.LOGGER.warn("Failed to register head texture {}", hash, e);
					image.close();
				}
			};
			Minecraft client = Minecraft.getInstance();
			if (client.isSameThread()) {
				register.run();
			} else {
				client.execute(register);
			}
			return loadedHeads.get(hash);
		} catch (Exception e) {
			AccountSwitcherClient.LOGGER.warn("Failed to load head texture {}", hash, e);
			return null;
		}
	}

	private boolean isExpired(CacheEntry entry) {
		long maxAge = Math.max(1, config.getCacheExpirationHours()) * 3_600_000L;
		return System.currentTimeMillis() - entry.lastUpdated > maxAge;
	}

	private void loadIndex() {
		try {
			Path file = StoragePaths.cacheIndexFile();
			if (!Files.exists(file)) {
				return;
			}
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!root.has("entries")) {
				return;
			}
			for (var entry : root.getAsJsonObject("entries").entrySet()) {
				CacheEntry cacheEntry = GSON.fromJson(entry.getValue(), CacheEntry.class);
				index.put(entry.getKey(), cacheEntry);
				if (cacheEntry.uuid != null) {
					try {
						uuidToHash.put(UUID.fromString(cacheEntry.uuid), entry.getKey());
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		} catch (Exception e) {
			AccountSwitcherClient.LOGGER.warn("Failed to load skin cache index", e);
		}
	}

	private void saveIndex() {
		try {
			Files.createDirectories(StoragePaths.cacheRoot());
			JsonObject root = new JsonObject();
			JsonObject entries = new JsonObject();
			for (var e : index.entrySet()) {
				entries.add(e.getKey(), GSON.toJsonTree(e.getValue()));
			}
			root.add("entries", entries);
			Files.writeString(StoragePaths.cacheIndexFile(), GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			AccountSwitcherClient.LOGGER.warn("Failed to save skin cache index", e);
		}
	}

	private static final class CacheEntry {
		String textureHash;
		String username;
		String uuid;
		long lastUpdated;
	}
}
