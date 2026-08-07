package com.accountswitcher.ui;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.compat.GuiDraw;
import com.accountswitcher.compat.GuiText;
import com.accountswitcher.compat.SoundCompat;
import com.accountswitcher.storage.AccountRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** Title / multiplayer control showing the active head + username. */
public final class AccountMenuButton extends AbstractWidget {
	private final Screen parent;

	public AccountMenuButton(int x, int y, int width, int height, Screen parent) {
		super(x, y, width, height, Component.translatable("accountswitcher.button.tooltip"));
		this.parent = parent;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		Minecraft.getInstance().setScreen(new AccountSwitcherScreen(parent));
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		SoundCompat.playUiClick(soundManager);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		int x = this.getX();
		int y = this.getY();
		int w = this.width;
		int h = this.height;
		int bg = this.isHoveredOrFocused() ? 0xAA000000 : 0x88000000;
		int border = this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;

		GuiDraw.fill(graphics, x, y, x + w, y + h, bg);
		GuiDraw.fill(graphics, x, y, x + w, y + 1, border);
		GuiDraw.fill(graphics, x, y + h - 1, x + w, y + h, border);
		GuiDraw.fill(graphics, x, y, x + 1, y + h, border);
		GuiDraw.fill(graphics, x + w - 1, y, x + w, y + h, border);

		Minecraft client = Minecraft.getInstance();
		UUID uuid = client.getUser().getProfileId();
		String name = client.getUser().getName();
		String hash = AccountSwitcherClient.accounts().store().findByUuid(uuid)
				.map(AccountRecord::getSkinTextureHash)
				.orElse(null);
		HeadRenderer.draw(graphics, uuid, name, hash, x + 2, y + (h - 16) / 2, 16);

		String hint = Component.translatable("accountswitcher.button.switchHint").getString();
		int textX = x + 22;
		GuiText.draw(graphics, client.font, name, textX, y + 2, 0xFFFFFF);
		GuiText.draw(graphics, client.font, hint, textX, y + 11, 0xA0A0A0);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
