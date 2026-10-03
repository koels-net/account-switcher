package com.accountswitcher.config;

/**
 * Microsoft identity defaults for Minecraft Xbox Live sign-in.
 * Override via {@code microsoftClientId} in account-switcher.json to use your own Azure app registration.
 */
public final class AuthConstants {
	/**
	 * Official Minecraft launcher public client id — Mojang/Microsoft-provided and approved for the
	 * Minecraft services API, so it clears the {@code login_with_xbox} check that rejects unapproved apps.
	 * A public/native client id is not a secret; it is safe to ship (a client <em>secret</em> is not,
	 * and is never used here).
	 */
	public static final String DEFAULT_CLIENT_ID = "00000000402b5328";

	/** MBI_SSL scope: what the launcher client id is authorized for on the login.live.com flow. */
	public static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
	/**
	 * Redirect registered for the launcher client id. Kept for refresh/token parity; interactive login
	 * uses the Live Connect device-code endpoint instead (the desktop redirect blanks {@code code=} to
	 * {@code removed=true} in external browsers).
	 */
	public static final String REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf";
	public static final String AUTHORIZE_URL = "https://login.live.com/oauth20_authorize.srf";
	/** Live Connect device authorization — works with the launcher client id + MBI_SSL. */
	public static final String DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf";
	public static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
	public static final String XBL_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
	public static final String XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
	public static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
	public static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
	public static final String MC_ENTITLEMENTS_URL = "https://api.minecraftservices.com/entitlements/mcstore";

	/**
	 * Client ids from earlier builds that never worked end-to-end (rejected as first-party, or not
	 * approved for the Minecraft API). Persisted configs/accounts holding these are migrated to
	 * {@link #DEFAULT_CLIENT_ID}.
	 */
	private static final java.util.Set<String> DEPRECATED_CLIENT_IDS = java.util.Set.of(
			"c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb",
			"74c5dc2a-449c-4224-8fe5-ca83ac47f48c"
	);

	/** Returns the id to actually use: the current default when {@code candidate} is blank or deprecated. */
	public static String resolveClientId(String candidate) {
		if (candidate == null) {
			return DEFAULT_CLIENT_ID;
		}
		String trimmed = candidate.trim();
		if (trimmed.isEmpty() || DEPRECATED_CLIENT_IDS.contains(trimmed)) {
			return DEFAULT_CLIENT_ID;
		}
		return trimmed;
	}

	private AuthConstants() {
	}
}
