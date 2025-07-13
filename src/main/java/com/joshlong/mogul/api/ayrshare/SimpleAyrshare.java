package com.joshlong.mogul.api.ayrshare;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@ImportRuntimeHints(SimpleAyrshare.Hints.class)
public class SimpleAyrshare implements Ayrshare {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var c : new Class<?>[] { JsonNode.class, Response.class, Ayrshare.PostContext.class,
					Ayrshare.Platform.class })
				hints.reflection().registerType(c, mcs);

		}

	}

	private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

	private final RestClient http;

	public SimpleAyrshare(String apiKey) {
		this.http = RestClient.builder().baseUrl("https://api.ayrshare.com/api/").defaultHeaders(h -> {
			h.setBearerAuth(apiKey);
			h.setContentType(MediaType.APPLICATION_JSON);
		}).build();

	}

	@Override
	public Response post(String post, Ayrshare.Platform[] platforms, Consumer<Ayrshare.PostContext> contextConsumer) {
		var ctx = new Ayrshare.PostContext();
		if (contextConsumer != null)
			contextConsumer.accept(ctx);
		return this.doPost(ctx.idempotencyKey.get(), post, platforms, ctx.mediaUris.toArray(new URI[0]),
				ctx.scheduledDate.get());
	}

	protected Response doPost(String idempotencyKey, String post, Ayrshare.Platform[] platforms, URI[] mediaUris,
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

		var platformsArray = Stream.of(platforms).map(Ayrshare.Platform::platformCode).toArray(String[]::new);
		body.put("platforms", platformsArray);
		if (scheduledDate != null) {
			body.put("scheduleDate", ISO_INSTANT.format(scheduledDate));
		}

		var mediaUrlsArray = Stream.of(mediaUris).map(URI::toString).toArray(String[]::new);
		if (mediaUrlsArray.length > 0) {
			body.put("mediaUrls", mediaUrlsArray);
		}
		var json = this.http.post().uri("/post").body(body).retrieve().body(JsonNode.class);
		Assert.notNull(json, "the json response should not be null");
		var status = nullOrNode(json, "status", JsonNode::asText);
		var id = nullOrNode(json, "id", JsonNode::asText);
		var refId = nullOrNode(json, "refId", JsonNode::asText);
		var validate = nullOrNode(json, "validate", JsonNode::asBoolean);
		var scheduleDate = nullOrNode(json, "scheduleDate",
				j -> Instant.from(ISO_INSTANT.parse(j.get("scheduleDate").asText())));
		return new Response(status, scheduleDate, id, refId, post, Boolean.TRUE.equals(validate));
	}

	private static <T> T nullOrNode(JsonNode node, String name, Function<JsonNode, T> function) {
		var jsonNode = node.get(name);
		if (jsonNode == null || jsonNode.isNull()) {
			return null;
		}
		Assert.notNull(function, "the mapping function should not be null");
		return function.apply(jsonNode);
	}

}
