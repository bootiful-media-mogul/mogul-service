package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.PublishableResolver;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.settings.Settings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Map;

@Configuration
class DefaultPublicationServiceConfiguration {

	@Bean
	DefaultPublicationService defaultPublicationService(JdbcClient client, MogulService mogulService,
			ApplicationEventPublisher applicationEventPublisher, TransactionTemplate transactionTemplate,
			TextEncryptor textEncryptor, Settings settings, Map<String, PublishableResolver<?>> resolverMap) {
		var lookup = new SettingsLookupClient(settings);
		return new DefaultPublicationService(client, mogulService, textEncryptor, transactionTemplate, lookup,
				resolverMap, applicationEventPublisher);
	}

	/**
	 * surfaces the gateway-supplied {@link PublicationService#BASE_URL_HEADER base-url
	 * header} into the GraphQL context so the {@code publish} mutation can read it (via
	 * {@code @ContextValue}) and hand it to plugins.
	 */
	@Bean
	WebGraphQlInterceptor baseUrlGraphQlInterceptor() {
		return (request, chain) -> {
			var baseUrl = request.getHeaders().getFirst(PublicationService.BASE_URL_HEADER);
			request.configureExecutionInput((_, builder) -> builder.graphQLContext(ctx -> {
				if (StringUtils.hasText(baseUrl))
					ctx.put(PublicationService.BASE_URL, baseUrl);
			}).build());
			return chain.next(request);
		};
	}

}
