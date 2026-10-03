package com.accountswitcher.auth;

import java.util.UUID;

/**
 * Result of a full Microsoft → Xbox → Minecraft authentication chain.
 */
public final class MinecraftAuthResult {
	private final String username;
	private final UUID uuid;
	private final String accessToken;
	private final String refreshToken;
	private final String xuid;
	private final String clientId;
	private final long accessTokenExpiresAt;
	private final String microsoftEmail;
	private final String skinTextureHash;

	public MinecraftAuthResult(
			String username,
			UUID uuid,
			String accessToken,
			String refreshToken,
			String xuid,
			String clientId,
			long accessTokenExpiresAt,
			String microsoftEmail,
			String skinTextureHash
	) {
		this.username = username;
		this.uuid = uuid;
		this.accessToken = accessToken;
		this.refreshToken = refreshToken;
		this.xuid = xuid;
		this.clientId = clientId;
		this.accessTokenExpiresAt = accessTokenExpiresAt;
		this.microsoftEmail = microsoftEmail;
		this.skinTextureHash = skinTextureHash;
	}

	public String getUsername() {
		return username;
	}

	public UUID getUuid() {
		return uuid;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public String getXuid() {
		return xuid;
	}

	public String getClientId() {
		return clientId;
	}

	public long getAccessTokenExpiresAt() {
		return accessTokenExpiresAt;
	}

	public String getMicrosoftEmail() {
		return microsoftEmail;
	}

	public String getSkinTextureHash() {
		return skinTextureHash;
	}
}
