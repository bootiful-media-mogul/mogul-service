package com.joshlong.mogul.api.search.index;

import java.util.List;
import java.util.Map;

/**
 * Provides a generic subsystem for ingesting and searching documents. Higher levels of
 * abstraction can work in terms of the {@link DocumentChunk document} type, and the
 * {@code documentId}. You can correlate documents to other types with {@code metadata}.
 */
public interface IndexService {

	Document ingest(String title, String fullText);

	Document ingest(String title, String fullText, Map<String, Object> metadata);

	Document documentById(Long id);

	List<SearchHit> search(String query);

	List<SearchHit> search(String query, Map<String, Object> metadata);

}
