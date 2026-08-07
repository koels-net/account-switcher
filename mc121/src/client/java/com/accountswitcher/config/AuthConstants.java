package com.accountswitcher.config;

/**
 * Microsoft identity defaults for Minecraft Xbox Live sign-in.
 * Override via {@code microsoftClientId} in account-switcher.json to use your own Azure app registration.
 */
public final class AuthConstants {
	/**
	 * Public Azure AD client used by Prism Launcher (personal Microsoft accounts + XboxLive.SignIn).
	 * Prefer registering your own application for production redistribution.
	 */
	public static final String DEFAULT_CLIENT_ID = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

	public static final String SCOPE = "XboxLive.SignIn XboxLive.offline_access";
	public static final String DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
	public static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
	public static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
	public static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
	public static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
	public static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
	public static final String MC_ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";

	private AuthConstants() {
	}
}
