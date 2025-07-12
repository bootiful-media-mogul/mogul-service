package com.joshlong.mogul.api.ayrshare;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * client [for this api](https://www.ayrshare.com/docs/apis/post/post)
 */

@ImportRuntimeHints(AyrshareClient.Hints.class)
public class AyrshareClient {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();

			for (var c : new String[] { JsonNode.class.getName(), Platform.class.getName() })
				hints.reflection().registerType(TypeReference.of(c), mcs);

		}

	}

	public static enum Platform {

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

	}

	private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

	private final RestClient http;

	public AyrshareClient(String apiKey) {
		this.http = RestClient.builder().baseUrl("https://api.ayrshare.com/api/").defaultHeaders(h -> {
			h.setBearerAuth(apiKey);
			h.setContentType(MediaType.APPLICATION_JSON);
		}).build();

	}

	// public static void main(String[] args) {
	// var ac = new AyrshareClient("");
	// var now = Instant.now();
	// var then = now.plus(3, ChronoUnit.MINUTES);
	//// var reply = ac.post(null, "this is a test @ " + now,
	//// new Platform[]{Platform.TWITTER}, new URI[0], null);
	//// System.out.println(reply);
	// var reply2 = ac
	// .post("this is another test" + now, new Platform[]{Platform.TWITTER},
	// ctx -> ctx.idempotencyKey("1"));
	// System.out.println(reply2);
	//
	// }
	//
	public static class PostContext {

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

	public AyrshareResponse post(String post, Platform[] platforms, Consumer<PostContext> contextConsumer) {
		var ctx = new PostContext();
		if (contextConsumer != null)
			contextConsumer.accept(ctx);
		return this.post(ctx.idempotencyKey.get(), post, platforms, ctx.mediaUris.toArray(new URI[0]),
				ctx.scheduledDate.get());
	}

	protected AyrshareResponse post(String idempotencyKey, String post, Platform[] platforms, URI[] mediaUris,
			Instant scheduledDate) {

		Assert.hasText(post, "the post should not be empty!");
		Assert.state(platforms.length > 0, "there should be at least one platform specified!");

		if (mediaUris == null)
			mediaUris = new URI[0];

		var body = new HashMap<String, Object>();
		body.put("post", post);

		if (StringUtils.hasText(idempotencyKey)) {
			body.put("idempotencyKey", idempotencyKey);
		}

		var platformsArray = Stream.of(platforms).map(Platform::platformCode).toArray(String[]::new);
		body.put("platforms", platformsArray);
		if (scheduledDate != null) {
			body.put("scheduleDate", ISO_INSTANT.format(scheduledDate));
		}

		var mediaUrlsArray = Stream.of(mediaUris).map(URI::toString).toArray(String[]::new);
		if (mediaUrlsArray.length > 0) {
			body.put("mediaUrls", mediaUrlsArray);
		}
		var json = this.http.post().uri("/post").body(body).retrieve().body(JsonNode.class);
		var status = json.get("status").asText();
		var id = json.get("id").asText();
		var refId = json.get("refId").asText();
		var validate = json.get("validate").asBoolean();
		var scheduleDate = json.get("scheduleDate") == null ? null
				: Instant.from(ISO_INSTANT.parse(json.get("scheduleDate").asText()));
		return new AyrshareResponse(status, scheduleDate, id, refId, post, validate);
	}

	public static record AyrshareResponse(String status, Instant scheduleDate, String id, String refId, String post,
			boolean validate) {
	}

}
