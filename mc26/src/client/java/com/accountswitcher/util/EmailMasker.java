package com.accountswitcher.util;

public final class EmailMasker {
	private EmailMasker() {
	}

	public static String mask(String email) {
		if (email == null || email.isBlank()) {
			return "Microsoft account";
		}
		int at = email.indexOf('@');
		if (at <= 0) {
			return email.charAt(0) + "****";
		}
		String local = email.substring(0, at);
		String domain = email.substring(at);
		if (local.length() == 1) {
			return local.charAt(0) + "****" + domain;
		}
		return local.charAt(0) + "****" + domain;
	}
}
