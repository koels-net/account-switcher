package com.accountswitcher.mixin;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.ui.AccountSwitcherScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
	protected JoinMultiplayerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("RETURN"))
	private void accountSwitcher$addButton(CallbackInfo ci) {
		if (!AccountSwitcherClient.config().isEnableAccountSwitcherButton()) {
			return;
		}
		JoinMultiplayerScreen self = (JoinMultiplayerScreen) (Object) this;
		this.addRenderableWidget(Button.builder(Component.translatable("accountswitcher.button.short"), button ->
				this.minecraft.setScreen(new AccountSwitcherScreen(self))
		).bounds(this.width - 105, 6, 98, 20).build());
	}
}
