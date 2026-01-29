package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.ai.AiClient;
import com.joshlong.mogul.api.compositions.CompositionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
class DefaultBlogServiceConfiguration {

	@Bean
	DefaultBlogService defaultBlogService(JdbcClient db, AiClient singularity, ApplicationEventPublisher publisher,
			CompositionService compositionService) {
		return new DefaultBlogService(db, singularity, publisher, compositionService);
	}

}
