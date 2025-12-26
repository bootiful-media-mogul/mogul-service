package com.joshlong.mogul.api.search.jdbc;

import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.pgvector.PGvector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.Cache;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * this maintains the low level aggregation of documents and their respective chunks.
 * provides the low-level atoms for indexing documents, searching for documents, etc.
 */
@Deprecated
@Transactional
class Index {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final EmbeddingModel embeddingModel;

	private final JdbcClient jdbcClient;

	private final DocumentChunkRowMapper documentChunkRowMapper;

	private final DocumentRowMapper documentRowMapper;

	private final SearchHitRowMapper searchHitRowMapper;

	// new support for caching
	private final Cache documentsCache, documentChunksCache;

	Index(JdbcClient jdbc, EmbeddingModel embeddingModel, DocumentChunkRowMapper documentChunkRowMapper,
			DocumentRowMapper documentRowMapper, SearchHitRowMapper searchHitRowMapper, Cache documentsCache,
			Cache documentChunksCache) {
		this.embeddingModel = embeddingModel;
		this.jdbcClient = jdbc;
		this.documentChunkRowMapper = documentChunkRowMapper;
		this.documentRowMapper = documentRowMapper;
		this.searchHitRowMapper = searchHitRowMapper;

		// new
		this.documentsCache = documentsCache;
		this.documentChunksCache = documentChunksCache;
	}

	/**
	 * package private so we can use it in the {@link IndexServiceConfiguration} class for
	 * wiring purposes.
	 * <p>
	 * TODO could we cache this?
	 */
	protected List<DocumentChunk> documentChunks(Long documentId) {
		return this.documentChunksCache.get(documentId, () -> jdbcClient //
			.sql("select dc.* from document_chunk dc where dc.document_id = ? ") //
			.params(documentId) //
			.query(this.documentChunkRowMapper) //
			.list());
	}

	Document ingest(String title, String fullText) {
		return this.ingest(title, fullText, Map.of());
	}

	private String keyFor(Map<String, Object> map) {
		var lhm = new LinkedHashMap<String, Object>();
		for (var k : map.keySet()) {
			lhm.put(k, map.get(k));
		}
		return JsonUtils.write(lhm);
	}

	Document ingest(String title, String fullText, Map<String, Object> metadata) {

		// todo is it better if we put the title in fullText?
		fullText = title + " " + fullText;

		if (metadata == null)
			metadata = Map.of();

		var key = this.keyFor(metadata);

		// todo find all documents with this key and delete the document and
		// document_chunks, accordingly
		var allExistingDocuments = this.jdbcClient //
			.sql("select id from document where key = ? ")//
			.params(key)//
			.query((RowMapper<Number>) (rs, rowNum) -> rs.getLong(1)) //
			.list();
		if (!allExistingDocuments.isEmpty()) {
			this.jdbcClient.sql("delete from document where key = ? ").params(key).update();
		}
		// ok now we will have the freshest of records
		var chunks = this.chunk(fullText, 800, 0.15); // very simple tokenizer
		var finalMetadata = metadata;
		var gkh = new GeneratedKeyHolder();
		this.jdbcClient //
			.sql("""
					   INSERT INTO document( key, source_uri, title, created_at, raw_text, metadata)
					   VALUES ( ?, ?, ?, ?, ?, ?::jsonb)
					   returning id
					""") //
			.params(key, "text", title, new Date(), fullText, JsonUtils.write(finalMetadata)) //
			.update(gkh);
		var documentId = ((Integer) Objects.requireNonNull(gkh.getKeys()).values().iterator().next()).longValue();
		for (var i = 0; i < chunks.size(); i++) {
			var chunkText = chunks.get(i);
			var resp = this.embeddingModel.embed(chunkText);
			this.jdbcClient //
				.sql("""
						INSERT INTO document_chunk(document_id, chunk_index, text, embedding)
						VALUES (?, ?, ?, ?)
						""") //
				.params(documentId, i, chunkText, new PGvector(resp)) //
				.update();
		}

		this.documentsCache.evictIfPresent(documentId);
		return this.documentById(documentId);
	}

	Document documentById(Long documentId) {
		return this.documentsCache.get(documentId, () -> CollectionUtils.firstOrNull(this.jdbcClient //
			.sql("""
					     select * from document d join document_chunk dc on d.id = dc.document_id where d.id = ?
					""") //
			.params(documentId) //
			.query(this.documentRowMapper) //
			.list()));
	}

	List<IndexHit> search(String query) {
		return this.search(query, null);
	}

	List<IndexHit> search(String query, Map<String, Object> metadata) {
		var vec = new PGvector(this.embeddingModel.embed(query));
		var hasMetadata = metadata != null && !metadata.isEmpty();
		var sql = this.buildSearchSqlWithMetadata(hasMetadata);
		var values = hasMetadata ? List.of(query, query, vec, vec, JsonUtils.write(metadata))
				: List.of(query, query, vec, vec);
		var results = this.jdbcClient.sql(sql).params(values).query(this.searchHitRowMapper).list();

		this.log.debug("phase 1: there are {} results {}", results.size(), results);

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
			results = jdbcClient //
				.sql(fuzzySql) //
				.params(query, query) //
				.query(this.searchHitRowMapper) //
				.list();

			this.log.debug("phase 2: there are {} results {}", results.size(), results);
		}
		return this.dedupe(results);
	}

	private List<IndexHit> dedupe(List<IndexHit> results) {
		return new ArrayList<>(new HashSet<>(results));
	}

	private String buildSearchSqlWithMetadata(boolean hasMetadata) {
		var metadataSql = hasMetadata ? //
				"""
						    JOIN document d ON dc.document_id = d.id
						    WHERE d.metadata @> ?::jsonb
						""" //
				: //
				"";

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
				        (1 - (dc.embedding <=> CAST(? AS vector))) AS vec_score,
				        f.fts_score,
				        0.7*(1 - (dc.embedding <=> CAST(? AS vector))) + 0.3 * f.fts_score AS score
				FROM fts f
				JOIN document_chunk dc ON f.id = dc.id
				%s
				ORDER BY score DESC
				LIMIT 20
				""";
		sql = sql.formatted(metadataSql);
		return sql;
	}

	private List<String> chunk(String text, int tokensPerChunk, double overlap) {
		if (text == null || text.isBlank()) {
			return List.of();
		}

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
