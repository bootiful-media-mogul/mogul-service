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

class SearchHitRowMapper implements RowMapper<SearchHit> {

	private final Function<ResultSet, DocumentChunk> documentChunkFunction;

	SearchHitRowMapper(Function<ResultSet, DocumentChunk> documentChunkFunction) {
		this.documentChunkFunction = documentChunkFunction;
	}

	@Override
	public SearchHit mapRow(ResultSet rs, int rowNum) throws SQLException {
		var documentChunk = this.documentChunkFunction.apply(rs);
		return new SearchHit(documentChunk, rs.getDouble("score"));
	}

}

class DocumentRowMapper implements RowMapper<Document> {

	private final ParameterizedTypeReference<Map<String, Object>> mapParameterizedTypeReference = new ParameterizedTypeReference<>() {
	};

	private final Function<Long, List<DocumentChunk>> documentChunkFunction;

	DocumentRowMapper(Function<Long, List<DocumentChunk>> documentChunkFunction) {
		this.documentChunkFunction = documentChunkFunction;
	}

	@Override
	public Document mapRow(ResultSet rs, int rowNum) throws SQLException {
		var createdAt = rs.getDate("created_at");
		var id = rs.getLong("id");
		var metadata = JsonUtils.read(rs.getString("metadata"), this.mapParameterizedTypeReference);
		return new Document(id, rs.getString("source_type"), UriUtils.uri(rs.getString("source_uri")),
				rs.getString("title"), createdAt, rs.getString("raw_text"), metadata,
				this.documentChunkFunction.apply(id));
	}

}

class DocumentChunkRowMapper implements RowMapper<DocumentChunk> {

	@Override
	public DocumentChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentChunk(rs.getLong("id"), rs.getString("text"), rs.getLong("document_id"));
	}

}