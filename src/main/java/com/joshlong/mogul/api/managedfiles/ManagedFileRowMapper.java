package com.joshlong.mogul.api.managedfiles;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

class ManagedFileRowMapper implements RowMapper<ManagedFile> {

	@Override
	public ManagedFile mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new ManagedFile(rs.getLong("mogul_id"), //
				rs.getLong("id"), //
				rs.getString("bucket"), //
				rs.getString("storage_filename"), //
				rs.getString("folder"), //
				rs.getString("filename"), //
				rs.getTimestamp("created"), //
				rs.getBoolean("written"), //
				rs.getLong("size"), //
				rs.getString("content_type"), //
				rs.getBoolean("visible"));
	}

}
