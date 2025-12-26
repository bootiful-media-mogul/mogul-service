package com.joshlong.mogul.api.search.jdbc;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Deprecated
record Document(Long id, String type, URI uri, String title, Date created, String rawText, Map<String, Object> metadata,
		List<DocumentChunk> chunks) {
}
