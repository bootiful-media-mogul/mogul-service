package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.Transcription;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("unchecked")
class TranscriptionRowMapper implements RowMapper<Transcription> {

	@Override
	public Transcription mapRow(ResultSet rs, int rowNum) throws SQLException {
		var payloadClass = (Class<? extends Transcribable>) ReflectionUtils.classForName(rs.getString("payload_class"));
		return new Transcription(rs.getLong("mogul_id"), rs.getLong("id"), rs.getDate("created"),
				rs.getDate("transcribed"), rs.getString("payload"), payloadClass, rs.getString("transcript"));
	}

}
