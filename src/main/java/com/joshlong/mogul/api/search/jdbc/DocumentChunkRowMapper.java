package com.joshlong.mogul.api.search.jdbc;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

@Deprecated
class DocumentChunkRowMapper implements RowMapper<DocumentChunk> {

	@Override
	public DocumentChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new DocumentChunk(rs.getLong("id"), rs.getString("text"), rs.getLong("document_id"));
	}

}
