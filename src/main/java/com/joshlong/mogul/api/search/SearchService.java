package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.UriUtils;
import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

/**
 * Provides a generic subsystem for ingesting and searching documents. Higher levels of
 * abstraction can work in terms of the {@link DocumentChunk document} type, and the
 * {@code documentId}. You can correlate documents to other types with {@code metadata}.
 */
public interface SearchService {

	Document ingest(String title, String fullText);

	Document ingest(String title, String fullText, Map<String, Object> metadata);

	List<DocumentChunk> search(String query);

	List<DocumentChunk> search(String query, Map<String, Object> metadata);

}

@Configuration
class SearchServiceConfiguration {

	@Bean
	SearchService searchService(EmbeddingModel embeddingModel, JdbcClient jdbcClient) {
		return new DefaultSearchService(jdbcClient, embeddingModel);
	}

}

@Transactional
class DefaultSearchService implements SearchService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final EmbeddingModel embeddingModel;

	private final JdbcClient jdbcClient;

	private final RowMapper<Document> documentRowMapper = new DocumentRowMapper(this::documentChunks);

	private final RowMapper<DocumentChunk> documentChunkRowMapper = new DocumentChunkRowMapper();

	DefaultSearchService(JdbcClient jdbc, EmbeddingModel embeddingModel) {
		this.embeddingModel = embeddingModel;
		this.jdbcClient = jdbc;
	}

	private List<DocumentChunk> documentChunks(Long documentId) {
		return jdbcClient //
			.sql("select dc.* , 0 as score  from document_chunk   dc where dc.document_id = ? ") //
			.params(documentId) //
			.query(this.documentChunkRowMapper) //
			.list();
	}

	@Override
	public Document ingest(String title, String fullText) {
		return this.ingest(title, fullText, Map.of());
	}

	@Override
	public Document ingest(String title, String fullText, Map<String, Object> metadata) {
		var chunks = this.chunk(fullText, 800, 0.15); // very simple tokenizer

		metadata = metadata == null ? Map.of() : metadata;
		var gkh = new GeneratedKeyHolder();
		this.jdbcClient.sql("""
				   INSERT INTO document(  source_uri, title, created_at, raw_text, metadata)
				   VALUES ( ?, ?, ?, ?, ?::jsonb)
				   returning  id
				""").params("text", title, new Date(), fullText, JsonUtils.write(metadata)).update(gkh);
		var documentId = ((Integer) Objects.requireNonNull(gkh.getKeys()).values().iterator().next()).longValue();
		for (var i = 0; i < chunks.size(); i++) {
			var chunkText = chunks.get(i);
			var resp = this.embeddingModel.embed(chunkText);
			this.jdbcClient.sql("""
					INSERT INTO document_chunk(document_id, chunk_index, text, emb)
					VALUES (?, ?, ?, ?)
					""").params(documentId, i, chunkText, new PGvector(resp)).update();
		}

		return this.byId(documentId);
	}

	private static class DocumentRowMapper implements RowMapper<Document> {

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

	private Document byId(Long documentId) {
		var documentList = this.jdbcClient //
			.sql("""
					 select * from document d join document_chunk dc on d.id = dc.document_id where d.id = ?
					""") //
			.params(documentId) //
			.query(this.documentRowMapper) //
			.list();
		return CollectionUtils.firstOrNull(documentList);
	}

	@Override
	public List<DocumentChunk> search(String query) {
		return this.search(query, null);
	}

	@Override
	public List<DocumentChunk> search(String query, Map<String, Object> metadata) {
		var vec = new PGvector(this.embeddingModel.embed(query));
		var hasMetadata = metadata != null && !metadata.isEmpty();
		var metadataSql = hasMetadata ? """
				    JOIN document d ON dc.document_id = d.id
				    WHERE d.metadata @> ?::jsonb
				""" : "";

		// 1) Primary hybrid search
		var sql = """
				WITH fts AS (
				    SELECT id, ts_rank(tsv, plainto_tsquery('english', ?)) AS fts_score
				    FROM document_chunk
				    WHERE tsv @@ plainto_tsquery('english', ?)
				)
				SELECT  dc.document_id  as document_id ,
				        dc.id,
				        dc.text,
				        (1 - (dc.emb <=> CAST(? AS vector))) AS vec_score,
				        f.fts_score,
				        0.7*(1 - (dc.emb <=> CAST(? AS vector))) + 0.3 * f.fts_score AS score
				FROM fts f
				JOIN document_chunk dc ON f.id = dc.id
				%s
				ORDER BY score DESC
				LIMIT 20
				""";
		sql = sql.formatted(metadataSql);

		var values = hasMetadata ? List.of(query, query, vec, vec, JsonUtils.write(metadata))
				: List.of(query, query, vec, vec);
		var results = this.jdbcClient.sql(sql).params(values).query(this.documentChunkRowMapper).list();

		// 2) Fallback fuzzy search using pre-tokenized tokens[] array
		if (results.isEmpty()) {
			var fuzzySql = """
					SELECT document_id , id, text,
					       (
					           SELECT MAX(similarity(w, ?))
					           FROM unnest(tokens) AS w
					       ) AS score
					FROM document_chunk
					WHERE (
					   SELECT MAX(similarity(w, ?))
					   FROM unnest(tokens) AS w
					) > 0.20
					ORDER BY score DESC
					LIMIT 20
					""";
			results = jdbcClient.sql(fuzzySql).params(query, query).query(this.documentChunkRowMapper).list();
		}

		return results;
	}

	private List<String> chunk(String text, int tokensPerChunk, double overlap) {
		var words = text.split("\\s+");
		var step = (int) (tokensPerChunk * (1 - overlap));
		var chunks = new ArrayList<String>();
		for (var i = 0; i < words.length; i += step) {
			var end = Math.min(words.length, i + tokensPerChunk);
			chunks.add(String.join(" ", Arrays.copyOfRange(words, i, end)));
		}
		return chunks;
	}

	private static class DocumentChunkRowMapper implements RowMapper<DocumentChunk> {

		@Override
		public DocumentChunk mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
			return new DocumentChunk(rs.getLong("id"), rs.getString("text"), rs.getDouble("score"),
					rs.getLong("document_id"));
		}

	}

}
