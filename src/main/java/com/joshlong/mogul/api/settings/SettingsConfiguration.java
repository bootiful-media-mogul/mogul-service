package com.joshlong.mogul.api.settings;

import com.joshlong.mogul.api.ApiProperties;
import com.joshlong.mogul.api.Settings;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.dsl.PublishSubscribeChannelSpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.StringUtils;

@Configuration
class SettingsConfiguration {

	@Bean
	TextEncryptor textEncryptor(ApiProperties properties) {
		return Encryptors.text(properties.settings().password(), properties.settings().salt());
	}

	@Bean
	Settings settings(JdbcClient jdbcClient, ApplicationEventPublisher publisher, TextEncryptor textEncryptor) {
		return new Settings(publisher, jdbcClient, textEncryptor);
	}

}
