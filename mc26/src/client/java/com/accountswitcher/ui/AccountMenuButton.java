package com.accountswitcher.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Title / multiplayer control showing the active head + username (26.x).
 */
public final class AccountMenuButton extends AbstractWidget {
	private final Screen parent;

	public AccountMenuButton(int x, int y, int width, int height, Screen parent) {
		super(x, y, width, height, Component.translatable("accountswitcher.button.tooltip"));
		this.parent = parent;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean bl) {
		ScreenHelper.open(new AccountSwitcherScreen(parent));
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
		playButtonClickSound(soundManager);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int bg = this.isHoveredOrFocused() ? 0xAA000000 : 0x88000000;
		graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bg);
		graphics.outline(this.getX(), this.getY(), this.width, this.height,
				this.isHoveredOrFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);

		Minecraft client = Minecraft.getInstance();
		UUID uuid = client.getUser().getProfileId();
		String name = client.getUser().getName();
		HeadRenderer.draw(graphics, uuid, name, this.getX() + 2, this.getY() + (this.height - 16) / 2, 16);

		String hint = Component.translatable("accountswitcher.button.switchHint").getString();
		int textX = this.getX() + 22;
		graphics.text(client.font, name, textX, this.getY() + 2, UiColors.opaque(0xFFFFFF));
		graphics.text(client.font, hint, textX, this.getY() + 11, UiColors.opaque(0xA0A0A0));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		this.defaultButtonNarrationText(output);
	}
}
