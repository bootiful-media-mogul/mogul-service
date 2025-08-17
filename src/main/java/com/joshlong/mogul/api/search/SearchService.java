package com.joshlong.mogul.api.search;

import com.pgvector.PGvector;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Provides a generic subsystem for ingesting and searching documents. Higher levels of
 * abstraction can work in terms of the {@link DocumentChunk document} type, and the
 * {@code documentId}. You can corellate documents to other types with {@code metadata}.
 */
public interface SearchService {

	void ingest(Long documentId, String title, String fullText);

	List<DocumentChunk> search(String query);

}

@Configuration
class SearchServiceConfiguration {

	@Bean
	SearchService searchService(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
		return new DefaultSearchService(embeddingModel, jdbcTemplate);
	}

}

@Transactional
class DefaultSearchService implements SearchService {

	private final EmbeddingModel embeddingModel;

	private final JdbcTemplate jdbc;

	private final RowMapper<DocumentChunk> hybridHitRowMapper = new HybridHitRowMapper();

	DefaultSearchService(EmbeddingModel embeddingModel, JdbcTemplate jdbc) {
		this.embeddingModel = embeddingModel;
		this.jdbc = jdbc;
	}

	@Override
	public void ingest(Long documentId, String title, String fullText) {
		var chunks = this.chunk(fullText, 800, 0.15); // very simple tokenizer
		this.jdbc.update("""
				    insert into document(id,  source_uri, title, created_at, raw_text )
				    values (?, ?, ? ,?, ? )
				""", documentId, null, title, new Date(), fullText);

		for (var i = 0; i < chunks.size(); i++) {
			var chunkText = chunks.get(i);
			var resp = this.embeddingModel.embed(chunkText);
			this.jdbc.update("""
					INSERT INTO document_chunk(document_id, chunk_index, text, emb)
					VALUES (?, ?, ?, ?)
					""", documentId, i, chunkText, new PGvector(resp));
		}
	}

	@Override
	public List<DocumentChunk> search(String query) {
		var vec = new PGvector(embeddingModel.embed(query));

		// 1) Primary hybrid search
		var sql = """
				WITH fts AS (
				    SELECT id, ts_rank(tsv, plainto_tsquery('english', ?)) AS fts_score
				    FROM document_chunk
				    WHERE tsv @@ plainto_tsquery('english', ?)
				)
				SELECT dc.id, dc.text,
				       (1 - (dc.emb <=> CAST(? AS vector))) AS vec_score,
				       f.fts_score,
				       0.7*(1 - (dc.emb <=> CAST(? AS vector))) + 0.3*f.fts_score AS score
				FROM fts f
				JOIN document_chunk dc ON f.id = dc.id
				ORDER BY score DESC
				LIMIT 20
				""";

		var results = jdbc.query(sql, hybridHitRowMapper, query, query, vec, vec);

		// 2) Fallback fuzzy search using pre-tokenized tokens[] array
		if (results.isEmpty()) {
			var fuzzySql = """
					SELECT id, text,
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
			results = jdbc.query(fuzzySql, hybridHitRowMapper, query, query);
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

	private static class HybridHitRowMapper implements RowMapper<DocumentChunk> {

		@Override
		public DocumentChunk mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
			return new DocumentChunk(rs.getLong("id"), rs.getString("text"), rs.getDouble("score"));
		}

	}

}
