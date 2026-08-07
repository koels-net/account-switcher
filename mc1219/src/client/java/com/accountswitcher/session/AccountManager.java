package com.accountswitcher.session;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.auth.DeviceCodeChallenge;
import com.accountswitcher.auth.MicrosoftAuthService;
import com.accountswitcher.auth.MinecraftAuthResult;
import com.accountswitcher.cache.SkinCacheManager;
import com.accountswitcher.compat.ProfileSkins;
import com.accountswitcher.compat.UserCompat;
import com.accountswitcher.config.ModConfig;
import com.accountswitcher.storage.AccountRecord;
import com.accountswitcher.storage.AccountStore;
import net.minecraft.client.Minecraft;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-level account operations: add, switch, refresh, remove, background token refresh.
 */
public final class AccountManager {
	private final ModConfig config;
	private final SkinCacheManager skinCache;
	private final AccountStore store;
	private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "account-switcher-auth");
		t.setDaemon(true);
		return t;
	});

	private final AtomicBoolean cancelAuth = new AtomicBoolean(false);
	private long lastBackgroundRefreshMs;
	private volatile String statusMessage = "";

	public AccountManager(ModConfig config, SkinCacheManager skinCache) {
		this.config = config;
		this.skinCache = skinCache;
		this.store = new AccountStore(config);
	}

	public void load() {
		store.load();
		for (AccountRecord account : store.getAccounts()) {
			skinCache.requestHead(account.getUuid(), account.getUsername(), account.getSkinTextureHash());
			ProfileSkins.prefetch(account.getUuid(), account.getUsername());
		}
	}

	public AccountStore store() {
		return store;
	}

	public String getStatusMessage() {
		return statusMessage;
	}

	public void clearStatusMessage() {
		statusMessage = "";
	}

	public void cancelAuthentication() {
		cancelAuth.set(true);
	}

	public void startAddAccount(Consumer<DeviceCodeChallenge> onChallenge, Consumer<AccountRecord> onSuccess, Consumer<String> onError) {
		cancelAuth.set(false);
		executor.execute(() -> {
			try {
				MicrosoftAuthService auth = new MicrosoftAuthService(config.getMicrosoftClientId());
				DeviceCodeChallenge challenge = auth.beginDeviceCode();
				statusMessage = "Waiting for Microsoft login…";
				Minecraft.getInstance().execute(() -> onChallenge.accept(challenge));
				auth.openBrowser(challenge.getBrowserUri());
				MinecraftAuthResult result = auth.pollAndAuthenticate(challenge, cancelAuth::get);
				AccountRecord account = fromResult(result);
				store.addOrUpdate(account);
				skinCache.requestHead(account.getUuid(), account.getUsername(), account.getSkinTextureHash());
				Minecraft.getInstance().execute(() -> {
					switchTo(account);
					onSuccess.accept(account);
				});
			} catch (MicrosoftAuthService.AuthCancelledException e) {
				Minecraft.getInstance().execute(() -> onError.accept("Cancelled"));
			} catch (Exception e) {
				AccountSwitcherClient.LOGGER.error("Add account failed", e);
				Minecraft.getInstance().execute(() -> onError.accept(e.getMessage() == null ? "Authentication failed" : e.getMessage()));
			} finally {
				statusMessage = "";
			}
		});
	}

	public void switchTo(AccountRecord account) {
		ensureFreshAsync(account, refreshed -> Minecraft.getInstance().execute(() -> doSwitch(refreshed, null)),
				error -> Minecraft.getInstance().execute(() -> doSwitch(account, error)));
	}

	private void doSwitch(AccountRecord account, String refreshWarning) {
		try {
			SessionSwitcher.apply(Minecraft.getInstance(), account);
			store.setActive(account.getId());
			if (refreshWarning != null && !refreshWarning.isBlank()) {
				statusMessage = "Signed in as " + account.getUsername() + " (" + refreshWarning + ")";
			} else {
				statusMessage = "Signed in as " + account.getUsername();
			}
		} catch (Throwable t) {
			AccountSwitcherClient.LOGGER.error("Failed to switch to {}", account.getUsername(), t);
			statusMessage = "Switch failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		}
	}

	public void reauthenticate(AccountRecord account, Consumer<AccountRecord> onSuccess, Consumer<String> onError) {
		cancelAuth.set(false);
		executor.execute(() -> {
			try {
				MicrosoftAuthService auth = new MicrosoftAuthService(
						account.getClientId() == null || account.getClientId().isBlank()
								? config.getMicrosoftClientId()
								: account.getClientId()
				);
				MinecraftAuthResult result;
				if (account.getRefreshToken() != null && !account.getRefreshToken().isBlank()) {
					result = auth.refresh(account.getRefreshToken());
				} else {
					DeviceCodeChallenge challenge = auth.beginDeviceCode();
					Minecraft.getInstance().execute(() -> {
						/* UI may show code if needed — open browser immediately */
					});
					auth.openBrowser(challenge.getBrowserUri());
					result = auth.pollAndAuthenticate(challenge, cancelAuth::get);
				}
				AccountRecord updated = fromResult(result);
				updated.setId(account.getId());
				updated.setFavorite(account.isFavorite());
				updated.setAddedAt(account.getAddedAt());
				store.addOrUpdate(updated);
				skinCache.requestHead(updated.getUuid(), updated.getUsername(), updated.getSkinTextureHash());
				Minecraft.getInstance().execute(() -> {
					if (updated.getId().equals(store.getActiveAccountId())
							|| updated.getUuid().equals(UserCompat.getProfileId(Minecraft.getInstance().getUser()))) {
						SessionSwitcher.apply(Minecraft.getInstance(), updated);
						store.setActive(updated.getId());
					}
					onSuccess.accept(updated);
				});
			} catch (Exception e) {
				account.setStatus(AccountRecord.AccountStatus.INVALID);
				store.save();
				Minecraft.getInstance().execute(() -> onError.accept(e.getMessage() == null ? "Reauthentication failed" : e.getMessage()));
			}
		});
	}

	public void remove(String id) {
		AccountRecord removed = store.findById(id).orElse(null);
		boolean wasActiveSession = isCurrentSession(id, removed);
		store.remove(id);
		if (!wasActiveSession) {
			return;
		}
		// The deleted account was the live session — don't leave the client signed into it.
		List<AccountRecord> remaining = store.getAccounts();
		if (!remaining.isEmpty()) {
			AccountRecord next = remaining.stream()
					.max(Comparator.comparingLong(AccountRecord::getLastUsedAt))
					.orElse(remaining.get(0));
			switchTo(next);
		} else {
			SessionSwitcher.applyOffline(Minecraft.getInstance());
			store.setActive(null);
			statusMessage = "Signed out";
		}
	}

	private boolean isCurrentSession(String id, AccountRecord removed) {
		if (id.equals(store.getActiveAccountId())) {
			return true;
		}
		if (removed == null || removed.getUuid() == null) {
			return false;
		}
		UUID current = UserCompat.getProfileId(Minecraft.getInstance().getUser());
		return removed.getUuid().equals(current);
	}

	public void toggleFavorite(AccountRecord account) {
		account.setFavorite(!account.isFavorite());
		store.save();
	}

	public void startBackgroundRefresh() {
		lastBackgroundRefreshMs = 0L;
	}

	public void tick() {
		if (!config.isAutomaticallyRefreshTokens()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastBackgroundRefreshMs < 5 * 60_000L) {
			return;
		}
		lastBackgroundRefreshMs = now;
		for (AccountRecord account : store.getAccounts()) {
			if (account.isAccessTokenExpired() && account.getRefreshToken() != null && !account.getRefreshToken().isBlank()) {
				ensureFreshAsync(account, refreshed -> {
				}, error -> AccountSwitcherClient.LOGGER.debug("Background refresh failed for {}: {}", account.getUsername(), error));
			}
		}
	}

	private void ensureFreshAsync(AccountRecord account, Consumer<AccountRecord> onReady, Consumer<String> onError) {
		if (!account.isAccessTokenExpired()) {
			onReady.accept(account);
			return;
		}
		executor.execute(() -> {
			try {
				MicrosoftAuthService auth = new MicrosoftAuthService(
						account.getClientId() == null || account.getClientId().isBlank()
								? config.getMicrosoftClientId()
								: account.getClientId()
				);
				MinecraftAuthResult result = auth.refresh(account.getRefreshToken());
				AccountRecord updated = fromResult(result);
				updated.setId(account.getId());
				updated.setFavorite(account.isFavorite());
				updated.setAddedAt(account.getAddedAt());
				updated.setMicrosoftEmail(account.getMicrosoftEmail());
				store.addOrUpdate(updated);
				Minecraft.getInstance().execute(() -> onReady.accept(updated));
			} catch (Exception e) {
				account.setStatus(AccountRecord.AccountStatus.EXPIRED);
				store.save();
				Minecraft.getInstance().execute(() -> onError.accept(e.getMessage() == null ? "Session expired" : e.getMessage()));
			}
		});
	}

	private AccountRecord fromResult(MinecraftAuthResult result) {
		AccountRecord account = new AccountRecord();
		account.setUsername(result.getUsername());
		account.setUuid(result.getUuid());
		account.setAccessToken(result.getAccessToken());
		account.setRefreshToken(result.getRefreshToken());
		account.setXuid(result.getXuid());
		account.setClientId(result.getClientId());
		account.setAccessTokenExpiresAt(result.getAccessTokenExpiresAt());
		account.setSkinTextureHash(result.getSkinTextureHash());
		account.setMicrosoftEmail(result.getMicrosoftEmail());
		account.setStatus(AccountRecord.AccountStatus.LOGGED_IN);
		account.setLastUsedAt(System.currentTimeMillis());
		return account;
	}

	public Optional<AccountRecord> activeAccount() {
		String id = store.getActiveAccountId();
		if (id == null) {
			return Optional.empty();
		}
		return store.findById(id);
	}

	public List<AccountRecord> list(AccountStore.SortMode sort, String query) {
		return store.sorted(sort, query);
	}
}
