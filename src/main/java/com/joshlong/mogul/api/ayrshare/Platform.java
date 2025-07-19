package com.joshlong.mogul.api.ayrshare;

public enum Platform {

	BLUESKY("bluesky"), FACEBOOK("facebook"), GOOGLE_BUSINESS_PROFILE("gmb"), INSTAGRAM("instagram"),
	LINKEDIN("linkedin"), PINTEREST("pinterest"), REDDIT("reddit"), SNAPCHAT("snapchat"), TELEGRAM("telegram"),
	THREADS("threads"), TIKTOK("tiktok"), X("twitter"), YOUTUBE("youtube");

	private final String platformCodename;

	Platform(String platformCodename) {
		this.platformCodename = platformCodename;
	}

	public static Platform of(String platformCode) {
		for (var p : Platform.values()) {
			if (p.platformCode().equalsIgnoreCase(platformCode))
				return p;
		}
		return null;
	}

	public static Platform[] of(Platform... platformCode) {
		return platformCode;
	}

	public String platformCode() {
		return this.platformCodename;
	}

}