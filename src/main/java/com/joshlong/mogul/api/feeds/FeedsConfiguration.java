package com.joshlong.mogul.api.feeds;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class FeedsConfiguration {

	@Bean
	Feeds feeds(ApiProperties properties) {
		return new Feeds(properties.feeds().elementPrefix(), properties.feeds().namespace());
	}

}
