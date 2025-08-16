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
 * the idea is that all things in the system produce text that we could search.
 */
// public interface SearchService {
//
// Collection<Searchable> search(String query, Map<String, String> context);
//
// Collection<Searchable> search(String query);
//
// void index(Searchable searchable);
// }

@Configuration
class SearchServiceConfiguration {

	@Bean
	SearchService searchService(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
		return new SearchService(embeddingModel, jdbcTemplate);
	}

}

@Transactional
class SearchService {

	private final EmbeddingModel embeddingModel;

	private final JdbcTemplate jdbcTemplate;

	SearchService(EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
		this.embeddingModel = embeddingModel;
		this.jdbcTemplate = jdbcTemplate;
	}

	public record HybridHit(long id, String text, double score) {
	}

	public void ingest(long documentId, String title, String fullText) {
		var chunks = this.chunk(fullText, 800, 0.15); // very simple tokenizer

		jdbcTemplate.update("""
				    insert into document(id, source_type, source_uri, title, created_at, raw_text )
				    values (?, ?, ? ,?, ?, ? )
				""", documentId, "text", null, title, new Date(), fullText);

		for (var i = 0; i < chunks.size(); i++) {
			var chunkText = chunks.get(i);
			var resp = embeddingModel.embed((chunkText));
			jdbcTemplate.update("""
					INSERT INTO document_chunk(document_id, chunk_index, text, emb)
					VALUES (?, ?, ?, ?)
					""", documentId, i, chunkText, new PGvector(resp));
		}
	}

	public List<HybridHit> search(String query) {
		var resp = this.embeddingModel.embed((query));
		var pgVec = new PGvector(resp);
		var hybridHitRowMapper = (RowMapper<HybridHit>) (rs, _) -> new HybridHit(rs.getLong("id"), rs.getString("text"),
				rs.getDouble("score"));
		var sql = """
				WITH fts AS (
				  SELECT id, ts_rank(tsv, plainto_tsquery('english', ?)) AS fts_score
				  FROM document_chunk
				  WHERE tsv @@ plainto_tsquery('english', ?)
				)
				SELECT dc.id, dc.text,
				       (1 - (dc.emb <=> CAST(? AS vector))) AS vec_score,
				       f.fts_score,
				       -- your tunable blended score (or just use vec_score)
				       0.7*(1 - (dc.emb <=> CAST(? AS vector))) + 0.3*f.fts_score AS score
				FROM fts f
				JOIN document_chunk dc ON f.id = dc.id
				ORDER BY score DESC
				LIMIT 20;
				""";
		var results = this.jdbcTemplate.query(sql, hybridHitRowMapper, query, query, pgVec, pgVec);
		if (results.isEmpty()) {
			// todo this isn't working as i imagined, and i don't know why
			jdbcTemplate.execute("SET pg_trgm.similarity_threshold = 0.1");
			results = jdbcTemplate.query("""
					   SELECT id, text, similarity(text, ?) AS score
					   FROM document_chunk
					   WHERE text % ?
					   ORDER BY score DESC
					""", hybridHitRowMapper, query, query);

		}
		return results;
	}

	/**
	 * extremely naive chunker: split every ~nWords
	 */
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

}
