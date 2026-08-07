package com.accountswitcher;

import com.accountswitcher.cache.SkinCacheManager;
import com.accountswitcher.config.ModConfig;
import com.accountswitcher.session.AccountManager;
import com.accountswitcher.session.StartupAccountRestorer;
import com.accountswitcher.storage.StoragePaths;
import com.accountswitcher.ui.AccountMenuButton;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AccountSwitcherClient implements ClientModInitializer {
	public static final String MOD_ID = "account-switcher";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static AccountManager accountManager;
	private static ModConfig config;
	private static SkinCacheManager skinCache;

	@Override
	public void onInitializeClient() {
		StoragePaths.init();
		config = ModConfig.loadOrCreate();
		skinCache = new SkinCacheManager(config);
		accountManager = new AccountManager(config, skinCache);
		accountManager.load();

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			StartupAccountRestorer.restoreIfNeeded(client, accountManager, config);
			accountManager.startBackgroundRefresh();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> accountManager.tick());

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!config.isEnableAccountSwitcherButton()) {
				return;
			}
			if (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen) {
				AccountMenuButton button = new AccountMenuButton(6, 6, 140, 20, screen);
				Screens.getButtons(screen).add(button);
			}
		});

		LOGGER.info("Account Switcher initialized (storage: {})", StoragePaths.root());
	}

	public static AccountManager accounts() {
		return accountManager;
	}

	public static ModConfig config() {
		return config;
	}

	public static SkinCacheManager skins() {
		return skinCache;
	}
}
