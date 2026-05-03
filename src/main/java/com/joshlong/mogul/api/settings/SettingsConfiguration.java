package com.joshlong.mogul.api.settings;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
class SettingsConfiguration {

	@Bean
	TextEncryptor textEncryptor(ApiProperties properties) {
		return Encryptors.text(properties.settings().password(), properties.settings().salt());
	}

	@Bean
	Settings settings(CacheManager cacheManager, JdbcClient jdbcClient, ApplicationEventPublisher publisher,
			TextEncryptor textEncryptor) {
		var mogulCategoryCache = cacheManager.getCache("mogulSettingsCategory");
		var mogulCategoryKeyCache = cacheManager.getCache("mogulSettingsCategoryKey");
		return new Settings(publisher, jdbcClient, textEncryptor, mogulCategoryCache, mogulCategoryKeyCache);
	}

}
