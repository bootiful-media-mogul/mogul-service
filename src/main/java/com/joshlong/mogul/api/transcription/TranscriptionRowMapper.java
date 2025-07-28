package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcription;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

class TranscriptionRowMapper implements RowMapper<Transcription> {

	@Override
	public Transcription mapRow(ResultSet rs, int rowNum) throws SQLException {
		return new Transcription(rs.getLong("id"), rs.getDate("created"), rs.getDate("transcribed"),
				rs.getString("payload"), ReflectionUtils.classForName(rs.getString("payload_class")),
				rs.getString("transcript"));
	}

}
