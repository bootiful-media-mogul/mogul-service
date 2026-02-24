package com.joshlong.mogul.api.wordpress;

import com.joshlong.mogul.api.security.TokenPropagatingFilter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Set;

@Configuration
@ImportRuntimeHints(WordPressConfiguration.Hints.class)
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
		// todo we need to put baseUrl and sites in settings, too!
		// var site = "joshlong.dev";// should be a setting. we can obtain the id given
		// the site..
		var siteId = "252444194"; // should be a setting
		// get the site ID from
		// https://public-api.wordpress.com/rest/v1.1/sites/joshlong.dev
		var base = "https://public-api.wordpress.com/wp/v2/sites/" + siteId;
		this.log.info("setting up a RestClient for WordPress.com site {} at {}", siteId, base);
		return builder //
			.baseUrl(base) //
			.requestInterceptor((request, body, execution) -> {
				var token = WordPressToken.get();
				if (StringUtils.hasText(token)) {

					if (this.log.isDebugEnabled())
						this.log.debug("setting outbound RestClient call's" + " WP bearer token to {}", token);

					request.getHeaders().setBearerAuth(token);
				}
				return execution.execute(request, body);
			})
			.build();
	}

	@Bean
	DefaultWordPressDotComDotComClient wordPressClient(
			@Qualifier(WORDPRESS_REST_CLIENT) RestClient wordPressRestClient) {
		return new DefaultWordPressDotComDotComClient(wordPressRestClient);
	}

	@Bean
	WebGraphQlInterceptor headerInterceptor() {
		return (request, chain) -> {
			var wpToken = request.getHeaders().getFirst(WORDPRESS_TOKEN_HEADER);
			request.configureExecutionInput((_, builder) -> builder.graphQLContext(ctx -> {
				if (wpToken != null) {
					ctx.put(WORDPRESS_TOKEN_CONTEXT_KEY, wpToken);
				}
			}) //
				.build());
			return chain.next(request);
		};
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

			for (var clazz : Set.of(WordPressPostResponse.class, WordPressPost.class, WordPressMediaResponse.class)) {
				hints.reflection().registerType(clazz, MemberCategory.values());
			}
		}

	}

}
