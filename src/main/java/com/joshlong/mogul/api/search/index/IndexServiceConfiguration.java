package com.joshlong.mogul.api.search.index;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.SQLException;

@Configuration
class IndexServiceConfiguration {

	@Bean
	DefaultIndexService defaultIndexService(EmbeddingModel embeddingModel, JdbcClient jdbcClient,
			DocumentRowMapper documentRowMapper, DocumentChunkRowMapper documentChunkRowMapper,
			SearchHitRowMapper searchHitRowMapper) {
		return new DefaultIndexService(jdbcClient, embeddingModel, documentChunkRowMapper, documentRowMapper,
				searchHitRowMapper);
	}

	@Bean
	DocumentChunkRowMapper documentChunkRowMapper() {
		return new DocumentChunkRowMapper();
	}

	@Bean
	DocumentRowMapper documentRowMapper(ObjectProvider<DefaultIndexService> defaultSearchService) {
		return new DocumentRowMapper(documentId -> defaultSearchService.getObject().documentChunks(documentId));
	}

	@Bean
	SearchHitRowMapper searchHitRowMapper(DocumentChunkRowMapper documentChunkRowMapper) {
		return new SearchHitRowMapper(resultSet -> {
			try {
				return documentChunkRowMapper.mapRow(resultSet, 0);
			}
			catch (SQLException e) {
				throw new RuntimeException(e);
			}
		});
	}

}
