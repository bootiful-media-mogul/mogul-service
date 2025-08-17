package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.ApiApplication;
import com.pgvector.PGvector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

@SpringBootTest(classes = ApiApplication.class)
class DefaultSearchServiceTest {

	private final SearchService searchService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	DefaultSearchServiceTest(@Autowired SearchService searchService) {
		this.searchService = searchService;
	}

	@Test
	@Disabled
	void debug(@Autowired EmbeddingModel em, @Autowired JdbcTemplate template) throws Exception {
		var sql = """
				SELECT id, (1 - (emb <=> CAST( ? AS vector))) AS vec_score
				FROM document_chunk ORDER BY vec_score DESC
				""";
		var vector = em.embed("IPO");
		var all = template.query(sql,
				(RowMapper<Object>) (rs, rowNum) -> Map.of("id", rs.getLong("id"), "score", rs.getDouble("vec_score")),
				new PGvector(vector));
		all.forEach(result -> logger.info(result.toString()));
	}

	@Test
	void index(@Value("classpath:/transcript.txt") Resource resource, @Autowired JdbcTemplate template)
			throws Exception {
		template.execute("delete from document_chunk");
		template.execute("delete from document");
		var contentAsString = resource.getContentAsString(Charset.defaultCharset());
		this.searchService.ingest(1L, "Transcript", contentAsString);
		var results = this.searchService.search("IPO");
		Assertions.assertFalse(results.isEmpty(), "there should be at least one result");
		this.logger.debug("found {} hits", results.size());

		var fuzzyResults = this.searchService.search("Pivtal");
		Assertions.assertFalse(fuzzyResults.isEmpty(), "there should be at least one fuzzy result");
		this.logger.debug("found {} fuzzy hits", fuzzyResults.size());

		var tanzu = this.searchService.search("Spring Boot Vmware");
		Assertions.assertFalse(tanzu.isEmpty(), "there should be at least one tanzu result");

	}

}