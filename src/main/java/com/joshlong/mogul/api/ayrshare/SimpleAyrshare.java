package com.joshlong.mogul.api.ayrshare;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@ImportRuntimeHints(SimpleAyrshare.Hints.class)
public class SimpleAyrshare implements Ayrshare {

	private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final RestClient http;

	public SimpleAyrshare(String apiKey) {
		this.http = RestClient.builder().baseUrl("https://api.ayrshare.com/api/").defaultHeaders(h -> {
			h.setBearerAuth(apiKey);
			h.setContentType(MediaType.APPLICATION_JSON);
		}).build();

	}

	private static Response.Status from(String string) {
		if (string != null) {
			if (string.equalsIgnoreCase("success")) {
				return Response.Status.SUCCESS;
			}
			if (string.equalsIgnoreCase("scheduled")) {
				return Response.Status.SCHEDULED;
			}
		}
		return Response.Status.ERRORS;
	}

	private static <T> T nullOrNode(JsonNode node, String name, Function<JsonNode, T> function) {
		Assert.notNull(node, "the node against which to evaluate tests should not be null");
		var jsonNode = node.get(name);
		if (jsonNode == null || jsonNode.isNull()) {
			return null;
		}
		Assert.notNull(function, "the mapping function should not be null");
		return function.apply(jsonNode);
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
		var postIdsResult = nullOrNode(json, "postIds", (Function<JsonNode, Map<Platform, Response.Post>>) jsonNode -> {
			var postIds = new HashMap<Platform, Response.Post>();
			jsonNode.iterator().forEachRemaining(n -> {
				var status1 = from(nullOrNode(n, "status", JsonNode::asText));
				var id1 = nullOrNode(n, "id", JsonNode::asText);
				var postUrl = nullOrNode(n, "postUrl", jn -> URI.create(jn.asText()));
				var platform = nullOrNode(n, "platform", jn -> Platform.of(jn.asText()));
				var type = nullOrNode(n, "type", JsonNode::asText);
				var cid = nullOrNode(n, "cid", JsonNode::asText);
				var value = new Response.Post(type, status1, id1, cid, postUrl, platform);
				postIds.put(platform, value);
			});
			return postIds;
		});
		return new Response(from(status), postIdsResult, scheduleDate, id, refId, post, Boolean.TRUE.equals(validate));
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			var mcs = MemberCategory.values();
			for (var c : new Class<?>[] { JsonNode.class, Response.class, Ayrshare.PostContext.class,
					Ayrshare.Response.class, Ayrshare.Response.Post.class, Ayrshare.Response.Status.class,
					Ayrshare.Platform.class })
				hints.reflection().registerType(c, mcs);

		}

	}

}
