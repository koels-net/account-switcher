package com.accountswitcher.ui;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.compat.GuiText;
import com.accountswitcher.compat.ProfileSkins;
import com.accountswitcher.compat.SoundCompat;
import com.accountswitcher.storage.AccountRecord;
import com.accountswitcher.storage.AccountStore;
import com.accountswitcher.util.TimeFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/**
 * Vanilla-styled account switcher. Add Account opens the browser directly — no separate login screen.
 */
public final class AccountSwitcherScreen extends Screen {
	private final Screen parent;
	private AccountList list;
	private EditBox searchBox;
	private AccountStore.SortMode sortMode = AccountStore.SortMode.LAST_USED;
	private String feedback = "";
	private boolean addingAccount;
	private float openAnim;


	public AccountSwitcherScreen(Screen parent) {
		super(Component.translatable("accountswitcher.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		openAnim = 0f;
		int bottom = this.height - 52;

		this.searchBox = new EditBox(this.font, this.width / 2 - 100, 32, 200, 20, Component.translatable("accountswitcher.search"));
		this.searchBox.setHint(Component.translatable("accountswitcher.search.hint"));
		this.searchBox.setResponder(s -> rebuildList());
		this.addRenderableWidget(this.searchBox);

		this.list = new AccountList(this.width, bottom - 56, 56, 36);
		this.addRenderableWidget(this.list);
		rebuildList();

		int y = this.height - 48;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.minecraft.setScreen(parent))
				.bounds(this.width / 2 - 154, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.sort"), b -> cycleSort())
				.bounds(this.width / 2 - 50, y, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.add"), b -> startAdd())
				.bounds(this.width / 2 + 54, y, 100, 20).build());

		y = this.height - 26;
		this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.switch"), b -> switchSelected())
				.bounds(this.width / 2 - 154, y, 74, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.favorite"), b -> favoriteSelected())
				.bounds(this.width / 2 - 76, y, 74, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.refresh"), b -> refreshSelected())
				.bounds(this.width / 2 + 2, y, 74, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.remove"), b -> removeSelected())
				.bounds(this.width / 2 + 80, y, 74, 20).build());
	}

	private void rebuildList() {
		List<AccountRecord> accounts = AccountSwitcherClient.accounts().list(sortMode, searchBox == null ? "" : searchBox.getValue());
		this.list.replace(accounts);
	}

	private void cycleSort() {
		sortMode = switch (sortMode) {
			case LAST_USED -> AccountStore.SortMode.NAME;
			case NAME -> AccountStore.SortMode.RECENTLY_ADDED;
			case RECENTLY_ADDED -> AccountStore.SortMode.LAST_USED;
		};
		feedback = Component.translatable("accountswitcher.sort.mode", sortModeLabel()).getString();
		rebuildList();
	}

	private String sortModeLabel() {
		return switch (sortMode) {
			case NAME -> Component.translatable("accountswitcher.sort.name").getString();
			case LAST_USED -> Component.translatable("accountswitcher.sort.lastUsed").getString();
			case RECENTLY_ADDED -> Component.translatable("accountswitcher.sort.added").getString();
		};
	}

	private AccountRecord selected() {
		AccountEntry entry = this.list.getSelected();
		return entry == null ? null : entry.account;
	}

	private void startAdd() {
		if (addingAccount) {
			AccountSwitcherClient.accounts().cancelAuthentication();
			addingAccount = false;
			feedback = Component.translatable("accountswitcher.login.cancelled").getString();
			return;
		}
		addingAccount = true;
		feedback = Component.translatable("accountswitcher.login.waitingBrowser").getString();
		AccountSwitcherClient.accounts().startAddAccount(challenge -> {
			if (this.minecraft != null) {
				this.minecraft.keyboardHandler.setClipboard(challenge.getUserCode());
			}
			feedback = Component.translatable("accountswitcher.login.codeReady", challenge.getUserCode()).getString();
		}, account -> {
			addingAccount = false;
			rebuildList();
			feedback = Component.translatable("accountswitcher.added").getString();
		}, error -> {
			addingAccount = false;
			feedback = error == null ? Component.translatable("accountswitcher.login.failed").getString() : error;
		});
	}

	private void switchSelected() {
		AccountRecord account = selected();
		if (account == null) {
			feedback = Component.translatable("accountswitcher.noneSelected").getString();
			return;
		}
		AccountSwitcherClient.accounts().switchTo(account);
		feedback = Component.translatable("accountswitcher.switched", account.getUsername()).getString();
		rebuildList();
	}

	private void favoriteSelected() {
		AccountRecord account = selected();
		if (account == null) {
			return;
		}
		AccountSwitcherClient.accounts().toggleFavorite(account);
		rebuildList();
	}

	private void refreshSelected() {
		AccountRecord account = selected();
		if (account == null) {
			return;
		}
		feedback = Component.translatable("accountswitcher.refreshing").getString();
		AccountSwitcherClient.accounts().reauthenticate(account, updated -> {
			feedback = Component.translatable("accountswitcher.refreshed", updated.getUsername()).getString();
			rebuildList();
		}, error -> feedback = error);
	}

	private void removeSelected() {
		AccountRecord account = selected();
		if (account == null) {
			return;
		}
		this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				AccountSwitcherClient.accounts().remove(account.getId());
				feedback = Component.translatable("accountswitcher.removed", account.getUsername()).getString();
			}
			this.minecraft.setScreen(this);
			rebuildList();
		}, Component.translatable("accountswitcher.remove.confirm.title"),
				Component.translatable("accountswitcher.remove.confirm.message", account.getUsername())));
	}

	@Override
	public void tick() {
		super.tick();
		openAnim = Math.min(1f, openAnim + 0.08f);
		String status = AccountSwitcherClient.accounts().getStatusMessage();
		if (status != null && !status.isBlank()) {
			feedback = status;
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		float alpha = openAnim;
		GuiText.draw(graphics, this.font, this.title.getString(),
				this.width / 2 - this.font.width(this.title) / 2, 12, withAlpha(0xFFFFFF, alpha));
		String current = Component.translatable("accountswitcher.current", this.minecraft.getUser().getName()).getString();
		GuiText.draw(graphics, this.font, current,
				this.width / 2 - this.font.width(current) / 2, 22, withAlpha(0xA0A0A0, alpha));
		if (feedback != null && !feedback.isBlank()) {
			GuiText.draw(graphics, this.font, feedback,
					this.width / 2 - this.font.width(feedback) / 2, this.height - 62, withAlpha(0xFFFF55, alpha));
		}
	}

	private static int withAlpha(int rgb, float alpha) {
		int a = Math.min(255, Math.max(0, (int) (alpha * 255))) << 24;
		return a | (rgb & 0xFFFFFF);
	}

	@Override
	public void onClose() {
		if (addingAccount) {
			AccountSwitcherClient.accounts().cancelAuthentication();
		}
		this.minecraft.setScreen(parent);
	}

	private final class AccountList extends ObjectSelectionList<AccountEntry> {
		AccountList(int width, int height, int y, int itemHeight) {
			super(AccountSwitcherScreen.this.minecraft, width, height, y, itemHeight);
		}

		void replace(List<AccountRecord> accounts) {
			this.clearEntries();
			UUID activeUuid = AccountSwitcherScreen.this.minecraft.getUser().getProfileId();
			for (AccountRecord account : accounts) {
				AccountEntry entry = new AccountEntry(account);
				this.addEntry(entry);
				if (activeUuid != null && activeUuid.equals(account.getUuid())) {
					this.setSelected(entry);
				}
			}
		}

		@Override
		public int getRowWidth() {
			return 300;
		}
	}

	private final class AccountEntry extends ObjectSelectionList.Entry<AccountEntry> {
		private final AccountRecord account;

		AccountEntry(AccountRecord account) {
			this.account = account;
		}

		@Override
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float delta) {
			int left = this.getContentX();
			int top = this.getContentY();
			int height = this.getContentHeight();

			UUID uuid = account.getUuid();
			ProfileSkins.prefetch(uuid, account.getUsername());
			HeadRenderer.draw(graphics, uuid, account.getUsername(), account.getSkinTextureHash(), left + 4, top + (height - 24) / 2, 24);

			boolean active = uuid != null && uuid.equals(AccountSwitcherScreen.this.minecraft.getUser().getProfileId());
			int nameColor = active ? 0x55FF55 : 0xFFFFFF;
			String star = account.isFavorite() ? "★ " : "";
			GuiText.draw(graphics, AccountSwitcherScreen.this.font, star + account.getUsername(), left + 34, top + 6, nameColor);

			String status = statusLabel(account, active);
			GuiText.draw(graphics, AccountSwitcherScreen.this.font,
					status + "  ·  " + Component.translatable("accountswitcher.entry.lastUsed", TimeFormat.relative(account.getLastUsedAt())).getString(),
					left + 34, top + 18, 0xA0A0A0);
		}

		@Override
		public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			AccountSwitcherScreen.this.list.setSelected(this);
			SoundCompat.playUiClick(AccountSwitcherScreen.this.minecraft.getSoundManager());
			if (doubleClick) {
				AccountSwitcherScreen.this.switchSelected();
			}
			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(account.getUsername());
		}
	}

	private static String statusLabel(AccountRecord account, boolean active) {
		if (active) {
			return Component.translatable("accountswitcher.status.active").getString();
		}
		return switch (account.getStatus()) {
			case LOGGED_IN -> Component.translatable("accountswitcher.status.loggedIn").getString();
			case EXPIRED -> Component.translatable("accountswitcher.status.expired").getString();
			case INVALID -> Component.translatable("accountswitcher.status.invalid").getString();
			case OFFLINE -> Component.translatable("accountswitcher.status.offline").getString();
			case UNKNOWN -> Component.translatable("accountswitcher.status.unknown").getString();
		};
	}
}
