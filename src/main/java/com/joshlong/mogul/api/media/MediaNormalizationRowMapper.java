package com.joshlong.mogul.api.media;

import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

class MediaNormalizationRowMapper implements RowMapper<MediaNormalization> {

	private static final TypeReference<Map<String, Object>> CONTEXT = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;

	MediaNormalizationRowMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public MediaNormalization mapRow(ResultSet rs, int rowNum) throws SQLException {
		var successful = rs.getObject("successful") == null ? null : rs.getBoolean("successful");
		return new MediaNormalization(rs.getLong("id"), rs.getString("correlation_id"),
				rs.getLong("input_managed_file_id"), rs.getLong("output_managed_file_id"),
				this.context(rs.getString("context")), rs.getTimestamp("created"), rs.getTimestamp("completed"),
				successful, rs.getString("error"));
	}

	private Map<String, Object> context(String json) {
		// the ids in here went in as Longs and json has no memory of that. the listeners
		// downstream cast them straight back to Long, so read every integral number as
		// one and spare them a ClassCastException that only shows up for small ids.
		return this.jsonMapper.reader().with(DeserializationFeature.USE_LONG_FOR_INTS).forType(CONTEXT).readValue(json);
	}

}
