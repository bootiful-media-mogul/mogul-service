package com.joshlong.mogul.api.search.index;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;

public record Document(Long id, String type, URI uri, String title, Date created, String rawText,
		Map<String, Object> metadata, List<DocumentChunk> chunks) {
}
