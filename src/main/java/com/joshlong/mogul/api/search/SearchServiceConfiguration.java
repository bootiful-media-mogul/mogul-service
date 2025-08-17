package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.UriUtils;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
class SearchServiceConfiguration {

	@Bean
	DefaultSearchService searchService(EmbeddingModel embeddingModel, JdbcClient jdbcClient,
			DocumentRowMapper documentRowMapper, DocumentChunkRowMapper documentChunkRowMapper,
			SearchHitRowMapper searchHitRowMapper) {
		return new DefaultSearchService(jdbcClient, embeddingModel, documentChunkRowMapper, documentRowMapper,
				searchHitRowMapper);
	}

	@Bean
	DocumentChunkRowMapper documentChunkRowMapper() {
		return new DocumentChunkRowMapper();
	}

	@Bean
	DocumentRowMapper documentRowMapper(ObjectProvider<DefaultSearchService> defaultSearchService) {
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
