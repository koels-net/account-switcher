package com.accountswitcher.mixin;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.compat.GuiText;
import com.accountswitcher.ui.AccountSwitcherScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void accountSwitcher$addButton(CallbackInfo ci) {
		if (!AccountSwitcherClient.config().isEnableAccountSwitcherButton()) {
			return;
		}
		TitleScreen self = (TitleScreen) (Object) this;
		this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.button.short"), button ->
				this.minecraft.setScreen(new AccountSwitcherScreen(self))
		).bounds(6, 6, 80, 20).build());
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void accountSwitcher$renderCurrentAccount(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (this.minecraft == null || this.minecraft.getUser() == null) {
			return;
		}
		String name = this.minecraft.getUser().getName();
		GuiText.draw(graphics, this.font, Component.translatable("accountswitcher.current", name), 6, 30, 0xA0A0A0);
	}
}
