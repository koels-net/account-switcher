package com.accountswitcher.auth;

import com.accountswitcher.AccountSwitcherClient;
import com.accountswitcher.config.AuthConstants;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * One Microsoft authentication pipeline shared by adding and re-authenticating accounts.
 *
 * <p>Uses the official Minecraft launcher public client id ({@code 00000000402b5328}) with the
 * Live Connect {@code MBI_SSL} scope. Interactive sign-in uses the device-code grant on
 * {@link AuthConstants#DEVICE_CODE_URL} — external browsers blank {@code oauth20_desktop.srf?code=}
 * to {@code ?removed=true}, so clipboard/URL paste cannot complete that redirect.
 *
 * <p>Pipeline: Microsoft token (device code or refresh) → Xbox Live → XSTS → Minecraft
 * {@code login_with_xbox} → profile.
 */
public final class MicrosoftAuthService {
	private final String clientId;

	public MicrosoftAuthService(String clientId) {
		this.clientId = clientId;
	}

	/**
	 * Starts Live Connect device authorization. Show {@link DeviceCodeChallenge#getUserCode()} and
	 * open {@link DeviceCodeChallenge#getBrowserUri()} for the player.
	 */
	public DeviceCodeChallenge requestDeviceCode() throws IOException, AuthException {
		String body = "client_id=" + enc(clientId)
				+ "&scope=" + enc(AuthConstants.SCOPE)
				+ "&response_type=device_code";
		JsonObject json = postForm(AuthConstants.DEVICE_CODE_URL, body);
		if (json.has("error")) {
			throw new AuthException("Device code request failed: " + errorText(json));
		}
		String userCode = reqString(json, "user_code");
		String deviceCode = reqString(json, "device_code");
		String verificationUri = json.has("verification_uri")
				? json.get("verification_uri").getAsString()
				: "https://www.microsoft.com/link";
		String verificationUriComplete = json.has("verification_uri_complete")
				? json.get("verification_uri_complete").getAsString()
				: "";
		int interval = json.has("interval") ? Math.max(1, json.get("interval").getAsInt()) : 5;
		long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 900L;
		return new DeviceCodeChallenge(
				userCode,
				verificationUri,
				verificationUriComplete,
				deviceCode,
				interval,
				System.currentTimeMillis() + expiresIn * 1000L
		);
	}

	/**
	 * Polls the token endpoint until the user finishes device-code sign-in, then completes Minecraft login.
	 *
	 * @param cancelled returns true when the user cancelled; checked between polls
	 */
	public MinecraftAuthResult authenticateWithDeviceCode(DeviceCodeChallenge challenge, BooleanSupplier cancelled)
			throws Exception {
		if (challenge == null || challenge.getDeviceCode() == null || challenge.getDeviceCode().isBlank()) {
			throw new AuthException("No device code");
		}
		long deadline = challenge.getExpiresAtEpochMs();
		int intervalMs = Math.max(1, challenge.getIntervalSeconds()) * 1000;
		while (System.currentTimeMillis() < deadline) {
			if (cancelled != null && cancelled.getAsBoolean()) {
				throw new AuthCancelledException("Authentication cancelled");
			}
			JsonObject token = pollDeviceToken(challenge.getDeviceCode());
			if (token.has("access_token")) {
				String msAccess = token.get("access_token").getAsString();
				String refresh = token.has("refresh_token") ? token.get("refresh_token").getAsString() : "";
				return completeMinecraftLogin(msAccess, refresh);
			}
			String error = token.has("error") ? token.get("error").getAsString() : "";
			if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
				if ("slow_down".equals(error)) {
					intervalMs += 1000;
				}
				Thread.sleep(intervalMs);
				continue;
			}
			if ("expired_token".equals(error) || "code_expired".equals(error)) {
				throw new AuthException("Microsoft login timed out — try again");
			}
			if ("authorization_declined".equals(error) || "access_denied".equals(error)) {
				throw new AuthCancelledException("Microsoft login declined");
			}
			throw new AuthException("Microsoft login failed: " + errorText(token));
		}
		throw new AuthException("Microsoft login timed out");
	}

	public MinecraftAuthResult refresh(String refreshToken) throws Exception {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new AuthException("Missing refresh token");
		}
		String body = "client_id=" + enc(clientId)
				+ "&grant_type=refresh_token"
				+ "&refresh_token=" + enc(refreshToken)
				+ "&scope=" + enc(AuthConstants.SCOPE);
		JsonObject token = postForm(AuthConstants.TOKEN_URL, body);
		if (token.has("error")) {
			throw new AuthException("Token refresh failed: " + errorText(token));
		}
		String msAccess = token.get("access_token").getAsString();
		String newRefresh = token.has("refresh_token") ? token.get("refresh_token").getAsString() : refreshToken;
		return completeMinecraftLogin(msAccess, newRefresh);
	}

	public void openBrowser(String url) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(url));
				return;
			}
		} catch (Exception ignored) {
		}
		try {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			ProcessBuilder pb;
			if (os.contains("win")) {
				pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
			} else if (os.contains("mac")) {
				pb = new ProcessBuilder("open", url);
			} else {
				pb = new ProcessBuilder("xdg-open", url);
			}
			pb.start();
		} catch (Exception e) {
			AccountSwitcherClient.LOGGER.warn("Could not open browser for {}", url);
		}
	}

	private JsonObject pollDeviceToken(String deviceCode) throws IOException {
		// Live Connect + launcher id: RFC device_code grant (URN). Short form is a fallback.
		String body = "client_id=" + enc(clientId)
				+ "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
				+ "&device_code=" + enc(deviceCode);
		JsonObject token = postForm(AuthConstants.TOKEN_URL, body);
		if (token.has("error") && "unsupported_grant_type".equals(token.get("error").getAsString())) {
			body = "client_id=" + enc(clientId)
					+ "&grant_type=device_code"
					+ "&device_code=" + enc(deviceCode);
			token = postForm(AuthConstants.TOKEN_URL, body);
		}
		return token;
	}

	// --- Shared tail of the pipeline -----------------------------------------------------------

	private MinecraftAuthResult completeMinecraftLogin(String msAccessToken, String refreshToken) throws Exception {
		XboxTokens xbl = authenticateXbox(msAccessToken);
		XboxTokens xsts = authorizeXsts(xbl.token());
		JsonObject mcLogin = loginMinecraft(xsts.userHash(), xsts.token());
		String mcToken = mcLogin.get("access_token").getAsString();
		long expiresAt = System.currentTimeMillis() + mcLogin.get("expires_in").getAsLong() * 1000L;

		ensureOwnsGame(mcToken);
		JsonObject profile = getProfile(mcToken);
		String username = profile.get("name").getAsString();
		UUID uuid = parseUndashedUuid(profile.get("id").getAsString());
		String skinHash = extractSkinHash(profile);

		return new MinecraftAuthResult(username, uuid, mcToken, refreshToken, xbl.userHash(), clientId, expiresAt, null, skinHash);
	}

	private XboxTokens authenticateXbox(String msAccessToken) throws IOException, AuthException {
		// LIVE MBI_SSL tokens use the raw RpsTicket; AAD XboxLive.signin tokens need "d=". Prefer raw.
		try {
			return authenticateXbox(msAccessToken, false);
		} catch (IOException first) {
			try {
				return authenticateXbox(msAccessToken, true);
			} catch (IOException second) {
				throw first;
			}
		}
	}

	private XboxTokens authenticateXbox(String msAccessToken, boolean withPrefix) throws IOException {
		JsonObject props = new JsonObject();
		props.addProperty("AuthMethod", "RPS");
		props.addProperty("SiteName", "user.auth.xboxlive.com");
		props.addProperty("RpsTicket", (withPrefix ? "d=" : "") + msAccessToken);

		JsonObject body = new JsonObject();
		body.add("Properties", props);
		body.addProperty("RelyingParty", "http://auth.xboxlive.com");
		body.addProperty("TokenType", "JWT");

		JsonObject response = postJson(AuthConstants.XBL_AUTH_URL, body.toString());
		String token = response.get("Token").getAsString();
		String uhs = response.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
		return new XboxTokens(token, uhs);
	}

	private XboxTokens authorizeXsts(String xblToken) throws IOException, AuthException {
		JsonObject props = new JsonObject();
		props.addProperty("SandboxId", "RETAIL");
		JsonArray tokens = new JsonArray();
		tokens.add(xblToken);
		props.add("UserTokens", tokens);

		JsonObject body = new JsonObject();
		body.add("Properties", props);
		body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
		body.addProperty("TokenType", "JWT");

		JsonObject response = postJson(AuthConstants.XSTS_AUTH_URL, body.toString());
		if (response.has("XErr")) {
			throw new AuthException("Xbox XSTS error: " + response.get("XErr").getAsString());
		}
		String token = response.get("Token").getAsString();
		String uhs = response.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
		return new XboxTokens(token, uhs);
	}

	private JsonObject loginMinecraft(String uhs, String xstsToken) throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
		return postJson(AuthConstants.MC_LOGIN_URL, body.toString());
	}

	private void ensureOwnsGame(String mcToken) throws IOException, AuthException {
		JsonObject entitlements = getJson(AuthConstants.MC_ENTITLEMENTS_URL, mcToken);
		JsonArray items = entitlements.has("items") ? entitlements.getAsJsonArray("items") : new JsonArray();
		if (items.isEmpty()) {
			throw new AuthException("This Microsoft account does not own Minecraft Java Edition");
		}
	}

	private JsonObject getProfile(String mcToken) throws IOException {
		return getJson(AuthConstants.MC_PROFILE_URL, mcToken);
	}

	private static String extractSkinHash(JsonObject profile) {
		if (!profile.has("skins")) {
			return null;
		}
		JsonArray skins = profile.getAsJsonArray("skins");
		for (JsonElement element : skins) {
			JsonObject skin = element.getAsJsonObject();
			if (skin.has("state") && "ACTIVE".equalsIgnoreCase(skin.get("state").getAsString()) && skin.has("url")) {
				String url = skin.get("url").getAsString();
				int slash = url.lastIndexOf('/');
				return slash >= 0 ? url.substring(slash + 1) : url;
			}
		}
		return null;
	}

	private static UUID parseUndashedUuid(String id) {
		String dashed = id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
		return UUID.fromString(dashed);
	}

	private static String reqString(JsonObject json, String key) throws AuthException {
		if (!json.has(key) || json.get(key).isJsonNull()) {
			throw new AuthException("Missing field in Microsoft response: " + key);
		}
		return json.get(key).getAsString();
	}

	private static String errorText(JsonObject json) {
		String error = json.has("error") ? json.get("error").getAsString() : "unknown_error";
		if (json.has("error_description")) {
			return error + ": " + json.get("error_description").getAsString();
		}
		return error;
	}

	// --- HTTP helpers --------------------------------------------------------------------------

	private static JsonObject postForm(String url, String body) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setConnectTimeout(15_000);
		connection.setReadTimeout(30_000);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		connection.setRequestProperty("Accept", "application/json");
		try (OutputStream out = connection.getOutputStream()) {
			out.write(body.getBytes(StandardCharsets.UTF_8));
		}
		return readJson(connection);
	}

	private static JsonObject postJson(String url, String jsonBody) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setConnectTimeout(15_000);
		connection.setReadTimeout(30_000);
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setRequestProperty("Accept", "application/json");
		try (OutputStream out = connection.getOutputStream()) {
			out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
		}
		int code = connection.getResponseCode();
		JsonObject json = readJson(connection);
		if (code >= 400 && !json.has("XErr")) {
			throw new IOException("HTTP " + code + " from " + url + ": " + json);
		}
		return json;
	}

	private static JsonObject getJson(String url, String bearer) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(15_000);
		connection.setReadTimeout(30_000);
		connection.setRequestProperty("Authorization", "Bearer " + bearer);
		connection.setRequestProperty("Accept", "application/json");
		return readJson(connection);
	}

	private static JsonObject readJson(HttpURLConnection connection) throws IOException {
		int code = connection.getResponseCode();
		InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
		if (stream == null) {
			stream = connection.getErrorStream();
		}
		if (stream == null) {
			return new JsonObject();
		}
		try (InputStream in = stream) {
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			if (text.isBlank()) {
				return new JsonObject();
			}
			return JsonParser.parseString(text).getAsJsonObject();
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private record XboxTokens(String token, String userHash) {
	}

	public static final class AuthException extends Exception {
		public AuthException(String message) {
			super(message);
		}
	}

	public static final class AuthCancelledException extends Exception {
		public AuthCancelledException(String message) {
			super(message);
		}
	}
}
