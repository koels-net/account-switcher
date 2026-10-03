package com.accountswitcher.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("user")
	@Mutable
	void accountSwitcher$setUser(User user);

	@Accessor("userApiService")
	@Mutable
	void accountSwitcher$setUserApiService(UserApiService service);

	@Accessor("profileKeyPairManager")
	@Mutable
	void accountSwitcher$setProfileKeyPairManager(ProfileKeyPairManager manager);

	/** Cached local-player profile (drives the singleplayer own-skin). Final in vanilla; {@link Mutable} strips it. */
	@Accessor("profileFuture")
	@Mutable
	void accountSwitcher$setProfileFuture(CompletableFuture<ProfileResult> future);

	/** Cached user properties (telemetry/social flags). Final in vanilla; {@link Mutable} strips it. */
	@Accessor("userPropertiesFuture")
	@Mutable
	void accountSwitcher$setUserPropertiesFuture(CompletableFuture<UserApiService.UserProperties> future);
}
