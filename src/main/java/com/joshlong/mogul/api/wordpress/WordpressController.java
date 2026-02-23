package com.joshlong.mogul.api.wordpress;

import com.joshlong.mogul.api.security.TokenPropagatingFilter;
import graphql.GraphQLError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.joshlong.mogul.api.wordpress.WordPressConfiguration.WORDPRESS_REST_CLIENT;
import static com.joshlong.mogul.api.wordpress.WordPressConfiguration.WORDPRESS_TOKEN_CONTEXT_KEY;

abstract class WordPressToken {

	static String get() {
		var credentials = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getDetails();
		if (credentials instanceof Map<?, ?> map) {
			return (String) map.get(WORDPRESS_TOKEN_CONTEXT_KEY);
		}
		throw new IllegalStateException("No WordPress token found in authentication details");
	}

}

@Configuration
class WordPressConfiguration {

	private final Logger log = LoggerFactory.getLogger(getClass());

	static final String WORDPRESS_REST_CLIENT = "wordpressRestClient";

	static final String WORDPRESS_TOKEN_CONTEXT_KEY = "wordpress-token";

	static final String WORDPRESS_TOKEN_HEADER = "X-WordPress-Token";

	@Bean
	TokenPropagatingFilter wordPressTokenInstallingFilter() {
		return new TokenPropagatingFilter(WORDPRESS_TOKEN_HEADER, WORDPRESS_TOKEN_CONTEXT_KEY);
	}

	@Bean(WORDPRESS_REST_CLIENT)
	RestClient wordPressRestClient(RestClient.Builder builder) {
		return builder.baseUrl("https://public-api.wordpress.com/rest/v1.1")
			.requestInterceptor((request, body, execution) -> {
				var token = WordPressToken.get();
				if (StringUtils.hasText(token)) {
					if (this.log.isDebugEnabled())
						this.log.debug("setting outbound RestClient call's WP bearer token to {}", token);

					request.getHeaders().setBearerAuth(token);
				}

				return execution.execute(request, body);
			})
			.build();
	}

	@Bean
	WebGraphQlInterceptor headerInterceptor() {
		return (request, chain) -> {
			var wpToken = request.getHeaders().getFirst(WORDPRESS_TOKEN_HEADER);
			request.configureExecutionInput((_, builder) -> builder.graphQLContext(ctx -> {
				if (wpToken != null) {
					ctx.put(WORDPRESS_TOKEN_CONTEXT_KEY, wpToken);
				}
			}).build());
			return chain.next(request);
		};
	}

}

@Controller
class WordpressController {

	private final RestClient wordPressRestClient;

	private final Logger log = LoggerFactory.getLogger(getClass());

	WordpressController(@Qualifier(WORDPRESS_REST_CLIENT) RestClient wordPressRestClient) {
		this.wordPressRestClient = wordPressRestClient;
	}

	private <T> T safeGet(JsonNode node, String key, Function<JsonNode, T> supplier,
			Function<JsonNode, T> emptySupplier) {
		return node.has(key) ? supplier.apply(node.get(key)) : emptySupplier.apply(node);
	}

	@QueryMapping
	WordPressStatus wordPressStatus(
			@ContextValue(value = WORDPRESS_TOKEN_CONTEXT_KEY, required = false) String wpToken) {

		if (wpToken != null) {
			this.log.info("calling HTTP endpoint with token: {}", wpToken);
			var jsonNode = this.wordPressRestClient //
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

}

record WordPressStatus(String avatar_URL, boolean connected, String email, String displayName) {
}

class WordPressNotConnectedException extends RuntimeException {

}

@ControllerAdvice
class GraphQlExceptionAdvice {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@GraphQlExceptionHandler
	GraphQLError handleWordPressNotConnected(WordPressNotConnectedException ex) {
		this.log.warn("WordPress account not connected!", ex);
		return GraphQLError.newError() //
			.errorType(ErrorType.FORBIDDEN) //
			.message("WordPress account not connected") //
			.extensions(Map.of("code", "WORDPRESS_NOT_CONNECTED", "action", "connect_wordpress")) //
			.build();
	}

}
