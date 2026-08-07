package com.accountswitcher.auth;

/**
 * Device-code challenge presented to the player while waiting for Microsoft login.
 */
public final class DeviceCodeChallenge {
	private final String userCode;
	private final String verificationUri;
	private final String verificationUriComplete;
	private final String deviceCode;
	private final int intervalSeconds;
	private final long expiresAtEpochMs;

	public DeviceCodeChallenge(
			String userCode,
			String verificationUri,
			String verificationUriComplete,
			String deviceCode,
			int intervalSeconds,
			long expiresAtEpochMs
	) {
		this.userCode = userCode;
		this.verificationUri = verificationUri;
		this.verificationUriComplete = verificationUriComplete;
		this.deviceCode = deviceCode;
		this.intervalSeconds = intervalSeconds;
		this.expiresAtEpochMs = expiresAtEpochMs;
	}

	public String getUserCode() {
		return userCode;
	}

	public String getVerificationUri() {
		return verificationUri;
	}

	/**
	 * Browser URL that prefers an OTC-prefilled link so the user does not type the code.
	 * Microsoft does not return {@code verification_uri_complete}; launchers append {@code ?otc=}
	 * when the URI is {@code https://www.microsoft.com/link}.
	 */
	public String getBrowserUri() {
		if (verificationUriComplete != null && !verificationUriComplete.isBlank()) {
			return verificationUriComplete;
		}
		String base = verificationUri == null ? "" : verificationUri.trim();
		if (userCode != null && !userCode.isBlank() && isMicrosoftLinkUri(base)) {
			String sep = base.contains("?") ? "&" : "?";
			return base + sep + "otc=" + userCode;
		}
		return base;
	}

	private static boolean isMicrosoftLinkUri(String uri) {
		String lower = uri.toLowerCase(java.util.Locale.ROOT);
		return lower.startsWith("https://www.microsoft.com/link")
				|| lower.startsWith("https://microsoft.com/link")
				|| lower.startsWith("http://www.microsoft.com/link")
				|| lower.startsWith("http://microsoft.com/link");
	}

	public String getDeviceCode() {
		return deviceCode;
	}

	public int getIntervalSeconds() {
		return intervalSeconds;
	}

	public long getExpiresAtEpochMs() {
		return expiresAtEpochMs;
	}
}
