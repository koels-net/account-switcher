package com.accountswitcher.session;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.config.ModConfig;
import com.accountswitcher.storage.AccountRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.Optional;
import java.util.UUID;

/**
 * On startup, restore the last selected account when configured (26.x).
 */
public final class StartupAccountRestorer {
	private StartupAccountRestorer() {
	}

	public static void restoreIfNeeded(Minecraft client, AccountManager manager, ModConfig config) {
		if (!config.isRememberLastAccount()) {
			return;
		}
		String lastId = manager.store().getLastSelectedAccountId();
		if (lastId == null) {
			captureLauncherAccount(client, manager);
			return;
		}
		Optional<AccountRecord> last = manager.store().findById(lastId);
		if (last.isEmpty()) {
			captureLauncherAccount(client, manager);
			return;
		}
		AccountRecord account = last.get();
		User current = client.getUser();
		UUID currentId = current.getProfileId();
		if (currentId != null && currentId.equals(account.getUuid())) {
			manager.store().setActive(account.getId());
			return;
		}
		AccountSwitcherClient.LOGGER.info("Restoring previous account {}", account.getUsername());
		manager.switchTo(account);
	}

	private static void captureLauncherAccount(Minecraft client, AccountManager manager) {
		User user = client.getUser();
		if (user.getProfileId() == null || user.getAccessToken() == null || user.getAccessToken().isBlank()) {
			return;
		}
		Optional<AccountRecord> existing = manager.store().findByUuid(user.getProfileId());
		if (existing.isPresent()) {
			manager.store().setActive(existing.get().getId());
			return;
		}
		AccountRecord record = new AccountRecord();
		record.setUsername(user.getName());
		record.setUuid(user.getProfileId());
		record.setAccessToken(user.getAccessToken());
		record.setXuid(user.getXuid().orElse(null));
		record.setClientId(user.getClientId().orElse(null));
		record.setStatus(AccountRecord.AccountStatus.LOGGED_IN);
		record.setLastUsedAt(System.currentTimeMillis());
		manager.store().addOrUpdate(record);
		manager.store().setActive(record.getId());
	}
}
