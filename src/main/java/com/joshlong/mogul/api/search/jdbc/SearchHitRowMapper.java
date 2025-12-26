package com.joshlong.mogul.api.search.jdbc;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Function;

@Deprecated
class SearchHitRowMapper implements RowMapper<IndexHit> {

	private final Function<ResultSet, DocumentChunk> documentChunkFunction;

	SearchHitRowMapper(Function<ResultSet, DocumentChunk> documentChunkFunction) {
		this.documentChunkFunction = documentChunkFunction;
	}

	@Override
	public IndexHit mapRow(ResultSet rs, int rowNum) throws SQLException {
		var documentChunk = this.documentChunkFunction.apply(rs);
		return new IndexHit(documentChunk, rs.getDouble("score"));
	}

}
