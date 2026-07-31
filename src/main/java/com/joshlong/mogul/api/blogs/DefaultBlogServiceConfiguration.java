package com.joshlong.mogul.api.blogs;

import com.joshlong.mogul.api.ai.AiClient;
import com.joshlong.mogul.api.compositions.CompositionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
class DefaultBlogServiceConfiguration {

	@Lazy
	@Bean
	DefaultBlogService defaultBlogService(JdbcClient db, AiClient singularity, ApplicationEventPublisher publisher,
			CompositionService compositionService, TransactionTemplate transactionTemplate) {
		return new DefaultBlogService(db, singularity, publisher, compositionService, transactionTemplate);
	}

}
