package com.joshlong.mogul.api.notifications.ably;

import com.joshlong.mogul.api.ApiProperties;
import io.ably.lib.realtime.AblyRealtime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(AblyHints.class)
class AblyConfiguration {

	@Bean
	AblyRealtime ablyRealtime(ApiProperties mogulProperties) throws Exception {
		return new AblyRealtime(mogulProperties.notifications().ably().apiKey());
	}

}
