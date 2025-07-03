package com.joshlong.mogul.api.notifications.ably;

import com.joshlong.mogul.api.ApiProperties;
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.Auth;
import io.ably.lib.types.AblyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public interface AblyTokenService {

	Auth.TokenRequest createTokenFor(String topicName, long ttl) throws AblyException;

}

@Configuration
class AblyTokenServiceConfiguration {

	@Bean
	DefaultAblyTokenService defaultAblyTokenService(ApiProperties properties, AblyRealtime ablyRealtime) {
		return new DefaultAblyTokenService(properties.notifications().ably().apiKey(), ablyRealtime);
	}

}

class DefaultAblyTokenService implements AblyTokenService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final AblyRealtime ably;

	private final String apiKey;

	DefaultAblyTokenService(String apiKey, AblyRealtime ably) {
		this.ably = ably;
		this.apiKey = apiKey;
	}

	@Override
	public Auth.TokenRequest createTokenFor(String topicName, long ttl) throws AblyException {
		if (this.log.isDebugEnabled())
			this.log.debug("creating a token for {} with ttl of {} ms", topicName, ttl);

		var tokenParams = new Auth.TokenParams();
		// todo fix this! this is CHANNEL based limitations! i need to have a multitenant
		// channel reference

		tokenParams.capability = "{\"" + topicName + "\":[\"subscribe\"]}";

		if (this.log.isDebugEnabled())
			this.log.debug('\t' + "{}", tokenParams.capability);

		// todo im not sure if this is right. i was on a plane when i got this working.
		// is a 'topic' the same as a channel name? or the same as the 'name' of a given
		// message?
		tokenParams.ttl = ttl;// 60 * 60 * 1000L; // 1 hour
		var authOptions = new Auth.AuthOptions();
		authOptions.key = this.apiKey;
		return this.ably.auth.createTokenRequest(tokenParams, authOptions);
	}

}