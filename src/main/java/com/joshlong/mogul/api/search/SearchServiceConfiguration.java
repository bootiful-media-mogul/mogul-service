package com.joshlong.mogul.api.search;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration
class SearchServiceConfiguration {

	@Bean
	SearchService searchService(EmbeddingModel embeddingModel, JdbcClient jdbcClient) {
		return new DefaultSearchService(jdbcClient, embeddingModel);
	}

}
