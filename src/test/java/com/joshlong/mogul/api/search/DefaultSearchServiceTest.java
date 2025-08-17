package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.ApiApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.Charset;
import java.util.Map;

@SpringBootTest(classes = ApiApplication.class)
class DefaultSearchServiceTest {

	private final SearchService searchService;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	DefaultSearchServiceTest(@Autowired SearchService searchService) {
		this.searchService = searchService;
	}

	@Test
	void index(@Value("classpath:/transcript.txt") Resource resource, @Autowired JdbcTemplate template)
			throws Exception {
		template.execute("delete from document_chunk");
		template.execute("delete from document");

		var contentAsString = resource.getContentAsString(Charset.defaultCharset());
		var pdf = this.searchService.ingest("Transcript", contentAsString, Map.of("source", "pdf"));
		var podcast = this.searchService.ingest("Transcript", contentAsString, Map.of("source", "podcast"));
		var results = this.searchService.search("IPO");
		Assertions.assertFalse(results.isEmpty(), "there should be at least one result");
		this.logger.debug("found {} hits", results.size());

		var fuzzyResults = this.searchService.search("Pivtal");
		Assertions.assertFalse(fuzzyResults.isEmpty(), "there should be at least one fuzzy result");
		this.logger.debug("found {} fuzzy hits", fuzzyResults.size());

		var tanzu = this.searchService.search("Spring Boot Vmware");
		Assertions.assertFalse(tanzu.isEmpty(), "there should be at least one tanzu result");

		var transcriptPdf = this.searchService.search("IPO", Map.of("source", "pdf"));
		Assertions.assertEquals(1, transcriptPdf.size(), "there should be only one transcript pdf result");
		Assertions.assertEquals(pdf.id(), transcriptPdf.getFirst().documentId(),
				"the transcript pdf result should be the same as the pdf document id");
	}

}