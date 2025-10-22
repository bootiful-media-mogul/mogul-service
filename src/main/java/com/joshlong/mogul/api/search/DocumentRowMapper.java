package com.joshlong.mogul.api.search;

import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.UriUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
