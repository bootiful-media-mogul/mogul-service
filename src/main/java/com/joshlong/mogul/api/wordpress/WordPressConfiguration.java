package com.joshlong.mogul.api.wordpress;

import com.joshlong.mogul.api.Settings;
import com.joshlong.mogul.api.mogul.MogulService;
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
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Set;
import java.util.function.Supplier;

@Configuration
@ImportRuntimeHints(WordPressConfiguration.Hints.class)
class WordPressConfiguration {

	static final String WORDPRESS_REST_CLIENT = "wordpressRestClient";

	static final String WORDPRESS_TOKEN_CONTEXT_KEY = "wordpress-token";

	static final String WORDPRESS_TOKEN_HEADER = "X-WordPress-Token";

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Bean
	DefaultWordPressClient wordPressClient(MogulService mogulService, Settings settings,
			@Qualifier(WORDPRESS_REST_CLIENT) RestClient wordPressRestClient) {
		var supplier = (Supplier<RestClient>) () -> wordPressRestClient.mutate()
			.baseUrl(this.baseUrlFor(settings, "/", mogulService.getCurrentMogul().id()))
			.build();
		return new DefaultWordPressClient(supplier);
	}

	private String baseUrlFor(Settings settings, String forwardSlash, Long mogulId) {
		var category = "wordpress";
		var base = settings.getValue(mogulId, category, "baseUrl");
		Assert.hasText(base, "the base URL must be set");
		if (!base.endsWith(forwardSlash)) {
			base = base + forwardSlash;
		}
		var siteId = settings.getValue(mogulId, category, "siteId");
		Assert.hasText(siteId, "the siteId must be set");
		var baseUrl = base + siteId;
		this.log.info("the base URL is {} for Mogul #{}", baseUrl, mogulId);
		return baseUrl;
	}

	@Bean
	TokenPropagatingFilter wordPressTokenInstallingFilter() {
		return new TokenPropagatingFilter(WORDPRESS_TOKEN_HEADER, WORDPRESS_TOKEN_CONTEXT_KEY);
	}

	@Bean(WORDPRESS_REST_CLIENT)
	RestClient wordPressRestClient(RestClient.Builder builder) {
		return builder //
			.requestInterceptor((request, body, execution) -> {
				var token = WordPressToken.get();
				if (StringUtils.hasText(token)) {
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
			}) //
				.build());
			return chain.next(request);
		};
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

			for (var clazz : Set.of(
					WordPressStatus.class ,
					WordPressPostResponse.class, WordPressPost.class, WordPressMediaResponse.class)) {
				hints.reflection().registerType(clazz, MemberCategory.values());
			}
		}

	}

}
