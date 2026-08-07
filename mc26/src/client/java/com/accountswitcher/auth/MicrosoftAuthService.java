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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Microsoft OAuth (device code) → Xbox Live → XSTS → Minecraft Services.
 * Opens the system browser so the player never copies tokens by hand.
 */
public final class MicrosoftAuthService {
	private final String clientId;

	public MicrosoftAuthService(String clientId) {
		this.clientId = clientId;
	}

	public DeviceCodeChallenge beginDeviceCode() throws IOException {
		String body = "client_id=" + enc(clientId) + "&scope=" + enc(AuthConstants.SCOPE);
		JsonObject json = postForm(AuthConstants.DEVICE_CODE_URL, body);
		String userCode = json.get("user_code").getAsString();
		String verificationUri = json.has("verification_uri")
				? json.get("verification_uri").getAsString()
				: "https://www.microsoft.com/link";
		String verificationUriComplete = json.has("verification_uri_complete")
				? json.get("verification_uri_complete").getAsString()
				: null;
		String deviceCode = json.get("device_code").getAsString();
		int interval = json.has("interval") ? json.get("interval").getAsInt() : 5;
		int expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 900;
		return new DeviceCodeChallenge(
				userCode,
				verificationUri,
				verificationUriComplete,
				deviceCode,
				interval,
				System.currentTimeMillis() + expiresIn * 1000L
		);
	}

	public void openBrowser(String url) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(URI.create(url));
				return;
			}
		} catch (Exception ignored) {
		}
		// Fallback: try OS-specific open
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

	/**
	 * Polls until the user completes login, then runs the Minecraft auth chain.
	 */
	public MinecraftAuthResult pollAndAuthenticate(DeviceCodeChallenge challenge, Callable<Boolean> cancelled) throws Exception {
		while (System.currentTimeMillis() < challenge.getExpiresAtEpochMs()) {
			if (Boolean.TRUE.equals(cancelled.call())) {
				throw new AuthCancelledException("Authentication cancelled");
			}
			Thread.sleep(Math.max(1, challenge.getIntervalSeconds()) * 1000L);
			JsonObject tokenResponse = tryPollToken(challenge.getDeviceCode());
			if (tokenResponse == null) {
				continue;
			}
			if (tokenResponse.has("error")) {
				String error = tokenResponse.get("error").getAsString();
				if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
					if ("slow_down".equals(error)) {
						Thread.sleep(3000L);
					}
					continue;
				}
				throw new AuthException("Microsoft login failed: " + error);
			}
			String msAccess = tokenResponse.get("access_token").getAsString();
			String refresh = tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").getAsString() : "";
			return completeMinecraftLogin(msAccess, refresh);
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
		JsonObject tokenResponse = postForm(AuthConstants.TOKEN_URL, body);
		if (tokenResponse.has("error")) {
			throw new AuthException("Token refresh failed: " + tokenResponse.get("error").getAsString());
		}
		String msAccess = tokenResponse.get("access_token").getAsString();
		String newRefresh = tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").getAsString() : refreshToken;
		return completeMinecraftLogin(msAccess, newRefresh);
	}

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

		return new MinecraftAuthResult(
				username,
				uuid,
				mcToken,
				refreshToken,
				xbl.userHash(),
				clientId,
				expiresAt,
				null,
				skinHash
		);
	}

	private JsonObject tryPollToken(String deviceCode) throws IOException {
		String body = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
				+ "&client_id=" + enc(clientId)
				+ "&device_code=" + enc(deviceCode);
		return postFormAllowError(AuthConstants.TOKEN_URL, body);
	}

	private XboxTokens authenticateXbox(String msAccessToken) throws IOException {
		JsonObject props = new JsonObject();
		props.addProperty("AuthMethod", "RPS");
		props.addProperty("SiteName", "user.auth.xboxlive.com");
		props.addProperty("RpsTicket", "d=" + msAccessToken);

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

	private static JsonObject postForm(String url, String body) throws IOException {
		JsonObject json = postFormAllowError(url, body);
		if (json.has("error") && !json.has("access_token") && !json.has("device_code")) {
			throw new IOException("HTTP form error: " + json);
		}
		return json;
	}

	private static JsonObject postFormAllowError(String url, String body) throws IOException {
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
