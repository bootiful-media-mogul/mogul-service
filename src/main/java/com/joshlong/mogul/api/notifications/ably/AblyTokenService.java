package com.joshlong.mogul.api.notifications.ably;

import com.joshlong.mogul.api.ApiProperties;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.Auth;
import io.ably.lib.types.AblyException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AblyTokenServiceConfiguration {

	@Bean
	DefaultAblyTokenService defaultAblyTokenService(ApiProperties properties, AblyRealtime ablyRealtime) {
		return new DefaultAblyTokenService(properties.notifications().ably().apiKey(), ablyRealtime);
	}

}

public interface AblyTokenService {

	Auth.TokenRequest createTokenFor(String topicName, long ttl) throws AblyException;

}

class DefaultAblyTokenService implements AblyTokenService {

	private final AblyRealtime ably;

	private final String apiKey;

	DefaultAblyTokenService(String apiKey, AblyRealtime ably) {
		this.ably = ably;
		this.apiKey = apiKey;
	}

	@Override
	public Auth.TokenRequest createTokenFor(String topicName, long ttl) throws AblyException {
		var tokenParams = new Auth.TokenParams();
		tokenParams.capability = """
				{ "%s" :  [ "subscribe" ] }
				""".formatted(topicName);
		tokenParams.ttl = ttl;
		var authOptions = new Auth.AuthOptions();
		authOptions.key = this.apiKey;
		return this.ably.auth.createTokenRequest(tokenParams, authOptions);
	}

}