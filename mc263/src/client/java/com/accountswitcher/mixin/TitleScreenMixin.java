package com.accountswitcher.mixin;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.ui.AccountSwitcherScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
				com.accountswitcher.ui.ScreenHelper.open(new AccountSwitcherScreen(self))
		).bounds(6, 6, 80, 20).build());
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void accountSwitcher$renderCurrentAccount(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (this.minecraft == null || this.minecraft.getUser() == null) {
			return;
		}
		String label = Component.translatable("accountswitcher.current", this.minecraft.getUser().getName()).getString();
		graphics.text(this.font, label, 6, 30, com.accountswitcher.ui.UiColors.opaque(0xA0A0A0));
	}
}
