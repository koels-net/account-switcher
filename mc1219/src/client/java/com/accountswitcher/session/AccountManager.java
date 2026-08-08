package com.accountswitcher.session;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.auth.DeviceCodeChallenge;
import com.accountswitcher.auth.MicrosoftAuthService;
import com.accountswitcher.auth.MinecraftAuthResult;
import com.accountswitcher.cache.SkinCacheManager;
import com.accountswitcher.compat.ProfileSkins;
import com.accountswitcher.compat.UserCompat;
import com.accountswitcher.config.AuthConstants;
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
 * High-level account operations. Adding and re-authenticating both run one pipeline
 * ({@link #authenticate}): a silent refresh when a refresh token exists, otherwise an interactive
 * Live Connect device-code sign-in.
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
		authenticate(null, onChallenge, onSuccess, onError);
	}

	public void reauthenticate(AccountRecord account, Consumer<DeviceCodeChallenge> onChallenge,
	                           Consumer<AccountRecord> onSuccess, Consumer<String> onError) {
		authenticate(account, onChallenge, onSuccess, onError);
	}

	/**
	 * The single Microsoft authentication pipeline. {@code existing == null} adds a new account;
	 * otherwise the existing account is updated in place after the same flow.
	 */
	private void authenticate(AccountRecord existing, Consumer<DeviceCodeChallenge> onChallenge,
	                          Consumer<AccountRecord> onSuccess, Consumer<String> onError) {
		cancelAuth.set(false);
		executor.execute(() -> {
			try {
				String clientId = AuthConstants.resolveClientId(
						existing != null && existing.getClientId() != null && !existing.getClientId().isBlank()
								? existing.getClientId()
								: config.getMicrosoftClientId());
				MicrosoftAuthService auth = new MicrosoftAuthService(clientId);

				MinecraftAuthResult result;
				String refreshToken = existing == null ? null : existing.getRefreshToken();
				// INVALID means a prior refresh already failed — skip straight to browser reauth.
				boolean forceInteractive = existing != null
						&& existing.getStatus() == AccountRecord.AccountStatus.INVALID;
				if (!forceInteractive && refreshToken != null && !refreshToken.isBlank()) {
					try {
						result = auth.refresh(refreshToken);
					} catch (Exception refreshFailed) {
						AccountSwitcherClient.LOGGER.info("Silent refresh failed, falling back to interactive sign-in");
						result = interactive(auth, onChallenge);
					}
				} else {
					result = interactive(auth, onChallenge);
				}

				AccountRecord record = fromResult(result);
				if (existing != null) {
					record.setId(existing.getId());
					record.setFavorite(existing.isFavorite());
					record.setAddedAt(existing.getAddedAt());
				}
				store.addOrUpdate(record);
				skinCache.requestHead(record.getUuid(), record.getUsername(), record.getSkinTextureHash());

				Minecraft.getInstance().execute(() -> {
					if (existing == null) {
						switchTo(record);
					} else if (record.getId().equals(store.getActiveAccountId())
							|| record.getUuid().equals(currentSessionUuid())) {
						SessionSwitcher.apply(Minecraft.getInstance(), record);
						store.setActive(record.getId());
					}
					onSuccess.accept(record);
				});
			} catch (MicrosoftAuthService.AuthCancelledException e) {
				Minecraft.getInstance().execute(() -> onError.accept("Cancelled"));
			} catch (Exception e) {
				if (existing != null) {
					existing.setStatus(AccountRecord.AccountStatus.INVALID);
					store.save();
				}
				AccountSwitcherClient.LOGGER.error("Authentication failed", e);
				Minecraft.getInstance().execute(() -> onError.accept(e.getMessage() == null ? "Authentication failed" : e.getMessage()));
			} finally {
				statusMessage = "";
			}
		});
	}

	private MinecraftAuthResult interactive(MicrosoftAuthService auth, Consumer<DeviceCodeChallenge> onChallenge) throws Exception {
		DeviceCodeChallenge challenge = auth.requestDeviceCode();
		statusMessage = "Enter code " + challenge.getUserCode() + " at microsoft.com/link";
		Minecraft.getInstance().execute(() -> {
			try {
				Minecraft.getInstance().keyboardHandler.setClipboard(challenge.getUserCode());
			} catch (Throwable ignored) {
			}
			onChallenge.accept(challenge);
		});
		auth.openBrowser(challenge.getBrowserUri());
		return auth.authenticateWithDeviceCode(challenge, cancelAuth::get);
	}

	private static UUID currentSessionUuid() {
		return UserCompat.getProfileId(Minecraft.getInstance().getUser());
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
		return removed.getUuid().equals(currentSessionUuid());
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
				MicrosoftAuthService auth = new MicrosoftAuthService(AuthConstants.resolveClientId(
						account.getClientId() == null || account.getClientId().isBlank()
								? config.getMicrosoftClientId()
								: account.getClientId()
				));
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
