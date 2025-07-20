package com.joshlong.mogul.api.mogul;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;

@Configuration
@ImportRuntimeHints({ MogulConfiguration.MogulHints.class })
class MogulConfiguration {

	@Bean
	DefaultMogulService defaultMogulService(TransactionTemplate tt, JdbcClient db, ApplicationEventPublisher publisher,
			@Value("${auth0.userinfo}") String authUserInfo, ApiProperties apiProperties) {
		return new DefaultMogulService(authUserInfo, db, publisher, tt, apiProperties.cache().maxEntries());
	}

	static class MogulHints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
			for (var c : Set.of(Mogul.class, MogulCreatedEvent.class))
				hints.reflection().registerType(c, MemberCategory.values());
		}

	}

}
