package com.accountswitcher.storage;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local encrypted account database ({@code accounts.json}).
 */
public final class AccountStore {
	private static final int SCHEMA_VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final ModConfig config;
	private final EncryptionService encryption;
	private final CopyOnWriteArrayList<AccountRecord> accounts = new CopyOnWriteArrayList<>();
	private String activeAccountId;
	private String lastSelectedAccountId;

	public AccountStore(ModConfig config) {
		this.config = config;
		this.encryption = config.isEnableEncryption() ? new EncryptionService() : null;
	}

	public synchronized void load() {
		accounts.clear();
		Path file = StoragePaths.accountsFile();
		try {
			Files.createDirectories(file.getParent());
			if (!Files.exists(file)) {
				save();
				return;
			}
			String raw = Files.readString(file, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
			int version = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 1;
			if (version > SCHEMA_VERSION) {
				AccountSwitcherClient.LOGGER.warn("accounts.json schema {} newer than supported {}; loading best-effort", version, SCHEMA_VERSION);
			}
			activeAccountId = optString(root, "activeAccountId");
			lastSelectedAccountId = optString(root, "lastSelectedAccountId");
			JsonArray list = root.has("accounts") ? root.getAsJsonArray("accounts") : new JsonArray();
			for (JsonElement element : list) {
				try {
					accounts.add(fromJson(element.getAsJsonObject()));
				} catch (Exception e) {
					AccountSwitcherClient.LOGGER.warn("Skipping corrupted account entry", e);
				}
			}
		} catch (Exception e) {
			AccountSwitcherClient.LOGGER.error("Failed to load accounts.json — starting with empty store", e);
			accounts.clear();
		}
	}

	public synchronized void save() {
		Path file = StoragePaths.accountsFile();
		try {
			Files.createDirectories(file.getParent());
			JsonObject root = new JsonObject();
			root.addProperty("schemaVersion", SCHEMA_VERSION);
			if (activeAccountId != null) {
				root.addProperty("activeAccountId", activeAccountId);
			}
			if (lastSelectedAccountId != null) {
				root.addProperty("lastSelectedAccountId", lastSelectedAccountId);
			}
			JsonArray list = new JsonArray();
			for (AccountRecord account : accounts) {
				list.add(toJson(account));
			}
			root.add("accounts", list);

			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			AccountSwitcherClient.LOGGER.error("Failed to save accounts.json", e);
		}
	}

	public List<AccountRecord> getAccounts() {
		return List.copyOf(accounts);
	}

	public Optional<AccountRecord> findById(String id) {
		return accounts.stream().filter(a -> a.getId().equals(id)).findFirst();
	}

	public Optional<AccountRecord> findByUuid(UUID uuid) {
		return accounts.stream().filter(a -> uuid.equals(a.getUuid())).findFirst();
	}

	public void addOrUpdate(AccountRecord account) {
		Optional<AccountRecord> existing = findByUuid(account.getUuid());
		if (existing.isPresent()) {
			AccountRecord old = existing.get();
			account.setId(old.getId());
			account.setFavorite(old.isFavorite());
			account.setAddedAt(old.getAddedAt());
			accounts.remove(old);
		}
		accounts.add(account);
		save();
	}

	public boolean remove(String id) {
		boolean removed = accounts.removeIf(a -> a.getId().equals(id));
		if (removed) {
			if (id.equals(activeAccountId)) {
				activeAccountId = null;
			}
			if (id.equals(lastSelectedAccountId)) {
				lastSelectedAccountId = null;
			}
			save();
		}
		return removed;
	}

	public void setActive(String id) {
		this.activeAccountId = id;
		this.lastSelectedAccountId = id;
		findById(id).ifPresent(a -> a.setLastUsedAt(System.currentTimeMillis()));
		save();
	}

	public String getActiveAccountId() {
		return activeAccountId;
	}

	public String getLastSelectedAccountId() {
		return lastSelectedAccountId;
	}

	public List<AccountRecord> sorted(SortMode mode, String query) {
		String q = query == null ? "" : query.trim().toLowerCase();
		List<AccountRecord> filtered = new ArrayList<>();
		for (AccountRecord account : accounts) {
			if (q.isEmpty()
					|| (account.getUsername() != null && account.getUsername().toLowerCase().contains(q))
					|| (account.getUuid() != null && account.getUuid().toString().contains(q))) {
				filtered.add(account);
			}
		}
		Comparator<AccountRecord> favoritesFirst = Comparator.comparing((AccountRecord a) -> !a.isFavorite());
		Comparator<AccountRecord> comparator = switch (mode) {
			case NAME -> favoritesFirst.thenComparing(a -> a.getUsername() == null ? "" : a.getUsername(), String.CASE_INSENSITIVE_ORDER);
			case LAST_USED -> favoritesFirst.thenComparing(AccountRecord::getLastUsedAt, Comparator.reverseOrder());
			case RECENTLY_ADDED -> favoritesFirst.thenComparing(AccountRecord::getAddedAt, Comparator.reverseOrder());
		};
		filtered.sort(comparator);
		return filtered;
	}

	private JsonObject toJson(AccountRecord account) {
		JsonObject obj = new JsonObject();
		obj.addProperty("id", account.getId());
		obj.addProperty("username", account.getUsername());
		if (account.getUuid() != null) {
			obj.addProperty("uuid", account.getUuid().toString());
		}
		obj.addProperty("microsoftEmail", account.getMicrosoftEmail());
		obj.addProperty("xuid", account.getXuid());
		obj.addProperty("clientId", account.getClientId());
		obj.addProperty("skinTextureHash", account.getSkinTextureHash());
		obj.addProperty("accessTokenExpiresAt", account.getAccessTokenExpiresAt());
		obj.addProperty("lastUsedAt", account.getLastUsedAt());
		obj.addProperty("addedAt", account.getAddedAt());
		obj.addProperty("favorite", account.isFavorite());
		obj.addProperty("status", account.getStatus().name());
		if (config.isEnableEncryption() && encryption != null) {
			obj.addProperty("accessToken", encryption.encrypt(nullToEmpty(account.getAccessToken())));
			obj.addProperty("refreshToken", encryption.encrypt(nullToEmpty(account.getRefreshToken())));
			obj.addProperty("encrypted", true);
		} else {
			obj.addProperty("accessToken", nullToEmpty(account.getAccessToken()));
			obj.addProperty("refreshToken", nullToEmpty(account.getRefreshToken()));
			obj.addProperty("encrypted", false);
		}
		return obj;
	}

	private AccountRecord fromJson(JsonObject obj) {
		AccountRecord account = new AccountRecord();
		account.setId(obj.get("id").getAsString());
		account.setUsername(optString(obj, "username"));
		String uuid = optString(obj, "uuid");
		if (uuid != null && !uuid.isEmpty()) {
			account.setUuid(UUID.fromString(uuid));
		}
		account.setMicrosoftEmail(optString(obj, "microsoftEmail"));
		account.setXuid(optString(obj, "xuid"));
		account.setClientId(optString(obj, "clientId"));
		account.setSkinTextureHash(optString(obj, "skinTextureHash"));
		account.setAccessTokenExpiresAt(obj.has("accessTokenExpiresAt") ? obj.get("accessTokenExpiresAt").getAsLong() : 0L);
		account.setLastUsedAt(obj.has("lastUsedAt") ? obj.get("lastUsedAt").getAsLong() : 0L);
		account.setAddedAt(obj.has("addedAt") ? obj.get("addedAt").getAsLong() : System.currentTimeMillis());
		account.setFavorite(obj.has("favorite") && obj.get("favorite").getAsBoolean());
		if (obj.has("status")) {
			try {
				account.setStatus(AccountRecord.AccountStatus.valueOf(obj.get("status").getAsString()));
			} catch (IllegalArgumentException ignored) {
				account.setStatus(AccountRecord.AccountStatus.UNKNOWN);
			}
		}
		boolean encrypted = !obj.has("encrypted") || obj.get("encrypted").getAsBoolean();
		String access = optString(obj, "accessToken");
		String refresh = optString(obj, "refreshToken");
		if (encrypted && encryption != null) {
			account.setAccessToken(encryption.decrypt(access));
			account.setRefreshToken(encryption.decrypt(refresh));
		} else {
			account.setAccessToken(access);
			account.setRefreshToken(refresh);
		}
		return account;
	}

	private static String optString(JsonObject obj, String key) {
		return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	public enum SortMode {
		NAME,
		LAST_USED,
		RECENTLY_ADDED
	}
}
