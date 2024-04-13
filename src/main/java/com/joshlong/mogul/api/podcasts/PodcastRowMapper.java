package com.joshlong.mogul.api.podcasts;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

class PodcastRowMapper implements RowMapper<Podcast> {

	@Override
	public Podcast mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new Podcast(rs.getLong("mogul_id"), rs.getLong("id"), rs.getString("title"), rs.getDate("created"));
	}

}
