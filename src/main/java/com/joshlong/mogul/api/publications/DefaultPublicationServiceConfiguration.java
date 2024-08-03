package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Settings;
import com.joshlong.mogul.api.mogul.MogulService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
class DefaultPublicationServiceConfiguration {

	@Bean
	Executor executor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}

	@Bean
	DefaultPublicationService defaultPublicationService(Executor executor, JdbcClient client, MogulService mogulService,
			TransactionTemplate tt, TextEncryptor textEncryptor, Settings settings,
			ApplicationEventPublisher publisher) {
		var lookup = new SettingsLookupClient(settings);
		return new DefaultPublicationService(executor, publisher, client, mogulService, textEncryptor, tt, lookup);
	}

}
