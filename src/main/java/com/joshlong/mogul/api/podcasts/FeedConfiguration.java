package com.joshlong.mogul.api.podcasts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.adapter.ForwardedHeaderTransformer;

@Configuration
class FeedConfiguration {

	@Bean
	ForwardedHeaderTransformer forwardedHeaderTransformer() {
		return new ForwardedHeaderTransformer();
	}

}
