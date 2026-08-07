package com.accountswitcher.storage;

import java.util.UUID;

/**
 * In-memory account model. Secrets are held decrypted only while the process runs.
 */
public final class AccountRecord {
	private String id;
	private String username;
	private UUID uuid;
	private String microsoftEmail;
	private String accessToken;
	private String refreshToken;
	private String xuid;
	private String clientId;
	private String skinTextureHash;
	private long accessTokenExpiresAt;
	private long lastUsedAt;
	private long addedAt;
	private boolean favorite;
	private AccountStatus status = AccountStatus.UNKNOWN;

	public AccountRecord() {
		this.id = UUID.randomUUID().toString();
		this.addedAt = System.currentTimeMillis();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public UUID getUuid() {
		return uuid;
	}

	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public String getMicrosoftEmail() {
		return microsoftEmail;
	}

	public void setMicrosoftEmail(String microsoftEmail) {
		this.microsoftEmail = microsoftEmail;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public String getXuid() {
		return xuid;
	}

	public void setXuid(String xuid) {
		this.xuid = xuid;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getSkinTextureHash() {
		return skinTextureHash;
	}

	public void setSkinTextureHash(String skinTextureHash) {
		this.skinTextureHash = skinTextureHash;
	}

	public long getAccessTokenExpiresAt() {
		return accessTokenExpiresAt;
	}

	public void setAccessTokenExpiresAt(long accessTokenExpiresAt) {
		this.accessTokenExpiresAt = accessTokenExpiresAt;
	}

	public long getLastUsedAt() {
		return lastUsedAt;
	}

	public void setLastUsedAt(long lastUsedAt) {
		this.lastUsedAt = lastUsedAt;
	}

	public long getAddedAt() {
		return addedAt;
	}

	public void setAddedAt(long addedAt) {
		this.addedAt = addedAt;
	}

	public boolean isFavorite() {
		return favorite;
	}

	public void setFavorite(boolean favorite) {
		this.favorite = favorite;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public void setStatus(AccountStatus status) {
		this.status = status;
	}

	public boolean isAccessTokenExpired() {
		return accessTokenExpiresAt > 0 && System.currentTimeMillis() >= accessTokenExpiresAt - 60_000L;
	}

	public enum AccountStatus {
		LOGGED_IN,
		EXPIRED,
		INVALID,
		OFFLINE,
		UNKNOWN
	}
}
