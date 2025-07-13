package com.joshlong.mogul.api.ayrshare;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * client [for this api](https://www.ayrshare.com/docs/apis/post/post)
 */
public interface Ayrshare {

	enum Platform {

		BLUESKY("bluesky"), FACEBOOK("facebook"), META("facebook"), GOOGLE_BUSINESS_PROFILE("gmb"),
		INSTAGRAM("instagram"), LINKEDIN("linkedin"), PINTEREST("pinterest"), REDDIT("reddit"), SNAPCHAT("snapchat"),
		TELEGRAM("telegram"), THREADS("threads"), TIKTOK("tiktok"), X("twitter"), TWITTER("twitter"),
		YOUTUBE("youtube");

		private final String platformCodename;

		Platform(String platformCodename) {
			this.platformCodename = platformCodename;
		}

		public String platformCode() {
			return this.platformCodename;
		}

		public static Platform[] of(Platform... platformCode) {
			return platformCode;
		}

	}

	class PostContext {

		final AtomicReference<Instant> scheduledDate = new AtomicReference<>();

		final AtomicReference<String> idempotencyKey = new AtomicReference<>();

		final List<URI> mediaUris = new ArrayList<>();

		public PostContext scheduledDate(Instant scheduledDate) {
			this.scheduledDate.set(scheduledDate);
			return this;
		}

		public PostContext idempotencyKey(String idempotencyKey) {
			this.idempotencyKey.set(idempotencyKey);
			return this;
		}

		public PostContext media(URI... mediaUris) {
			if (mediaUris != null)
				this.mediaUris.addAll(Arrays.asList(mediaUris));
			return this;
		}

	}

	record Response(String status, Instant scheduleDate, String id, String refId, String post, boolean validate) {
	}

	default Platform[] platforms() {
		return Platform.values();
	}

	Response post(String post, Platform[] platforms, Consumer<PostContext> contextConsumer);

	default Response post(String post, Ayrshare.Platform[] platforms) {
		return this.post(post, platforms, null);
	}

}
