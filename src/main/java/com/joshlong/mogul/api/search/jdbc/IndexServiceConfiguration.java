package com.joshlong.mogul.api.search.jdbc;

import com.joshlong.mogul.api.search.SearchableRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.SQLException;
import java.util.Map;

@Profile("jdbc")
@Configuration
class IndexServiceConfiguration {

	@Bean
	JdbcSearchService defaultSearchService(Map<String, SearchableRepository<?, ?>> repositories, Index index) {
		return new JdbcSearchService(repositories, index);
	}

	@Bean
	Index defaultIndexService(CacheManager cacheManager, EmbeddingModel embeddingModel, JdbcClient jdbcClient,
			DocumentRowMapper documentRowMapper, DocumentChunkRowMapper documentChunkRowMapper,
			SearchHitRowMapper searchHitRowMapper) {
		var documentsCache = cacheManager.getCache("documents");
		var documentChunksCache = cacheManager.getCache("documentChunks");
		return new Index(jdbcClient, embeddingModel, documentChunkRowMapper, documentRowMapper, searchHitRowMapper,
				documentsCache, documentChunksCache);
	}

	@Bean
	DocumentChunkRowMapper documentChunkRowMapper() {
		return new DocumentChunkRowMapper();
	}

	@Bean
	DocumentRowMapper documentRowMapper(ObjectProvider<Index> defaultSearchService) {
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
