package com.accountswitcher.util;

import java.util.concurrent.TimeUnit;

public final class TimeFormat {
	private TimeFormat() {
	}

	public static String relative(long epochMs) {
		if (epochMs <= 0L) {
			return "Never";
		}
		long delta = Math.max(0L, System.currentTimeMillis() - epochMs);
		if (delta < TimeUnit.MINUTES.toMillis(1)) {
			return "Just now";
		}
		if (delta < TimeUnit.HOURS.toMillis(1)) {
			long minutes = TimeUnit.MILLISECONDS.toMinutes(delta);
			return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
		}
		if (delta < TimeUnit.DAYS.toMillis(1)) {
			long hours = TimeUnit.MILLISECONDS.toHours(delta);
			return hours + (hours == 1 ? " hour ago" : " hours ago");
		}
		long days = TimeUnit.MILLISECONDS.toDays(delta);
		return days + (days == 1 ? " day ago" : " days ago");
	}
}
