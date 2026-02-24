package com.joshlong.mogul.api.wordpress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.function.Function;

/**
 * just enough API surface area to support the blog publishing feature. the trouble is
 * that <a href="https://www.wordpress.com">WordPress</a> is a bit of a mess, and its REST
 * API is completely different from the Wordpress API in self-hosted instances of
 * WordPress.
 */
class DefaultWordPressDotComClient implements WordPressClient {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final RestClient restClient;

	DefaultWordPressDotComClient(RestClient wordPressRestClient) {
		this.restClient = wordPressRestClient;
	}

	// Publish a post immediately
	@Override
	public WordPressPostResponse publishPost(WordPressPost post) {
		var jsonRespone = restClient.post() //
			.uri("/posts")//
			.body(post) //
			.retrieve()
			.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
				var body = new String(res.getBody().readAllBytes());
				this.log.error("4xx error: status={} body={}", res.getStatusCode(), body);
				throw new RuntimeException("WordPress API error: " + body);
			})
			.body(WordPressPostResponse.class); //
		this.log.info("the json response is {} ", jsonRespone);
		return jsonRespone;
	}

	@Override
	public WordPressPostResponse saveDraft(WordPressPost post) {
		var draft = new WordPressPost(post.title(), post.content(), WordPressPost.Status.PUBLISH, post.slug(),
				post.categories(), post.tags(), post.excerpt());
		return restClient.post().uri("/posts").body(draft).retrieve().body(WordPressPostResponse.class);
	}

	@Override
	public WordPressPostResponse updatePost(int postId, WordPressPost post) {
		return restClient.post() // WordPress uses POST for updates too (or PUT)
			.uri("/posts/{id}", postId)
			.body(post)
			.retrieve()
			.body(WordPressPostResponse.class);
	}

	private <T> T safeGet(JsonNode node, String key, Function<JsonNode, T> supplier,
			Function<JsonNode, T> emptySupplier) {
		return node.has(key) ? supplier.apply(node.get(key)) : emptySupplier.apply(node);
	}

	@Override
	public WordPressStatus status() {
		var wpToken = WordPressToken.get();
		if (StringUtils.hasText(wpToken)) {
			this.log.info("calling HTTP endpoint with token: {}", wpToken);
			var jsonNode = this.restClient //
				.get() //
				.uri("/me") //
				.retrieve() //
				.body(JsonNode.class);

			if (jsonNode != null) {
				var avatarUrl = safeGet(jsonNode, "avatar_URL", JsonNode::asText, null);
				var email = safeGet(jsonNode, "email", JsonNode::asString, jn -> null);
				var displayName = safeGet(jsonNode, "display_name", JsonNode::asString, jn -> null);
				return new WordPressStatus(avatarUrl, true, email, displayName);
			}
		}
		return new WordPressStatus(null, false, null, null);
	}

	@Override
	public WordPressMediaResponse uploadMedia(String filename, Resource data, MediaType mimeType) {
		return restClient.post()
			.uri("/media")
			.contentType(mimeType)
			.header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
			.body(data)
			.retrieve()
			.body(WordPressMediaResponse.class);
	}

}
