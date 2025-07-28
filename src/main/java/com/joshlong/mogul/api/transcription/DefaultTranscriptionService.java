package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.Transcription;
import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultTranscriptionService implements TranscriptionService {

	private final JdbcClient db;

	private final TranscriptionRowMapper transcribableRowMapper;

	DefaultTranscriptionService(TranscriptionRowMapper transcribableRowMapper, JdbcClient db) {
		this.transcribableRowMapper = transcribableRowMapper;
		this.db = db;
	}

	private Transcription readThroughTranscriptionByKey(String clazz, String payloadKeyAsJson) {
		return CollectionUtils
			.firstOrNull(this.db.sql("select * from transcription where payload_class = ? and payload = ?")
				.params(clazz, payloadKeyAsJson)
				.query(this.transcribableRowMapper)
				.list());
	}

	// todo caching of some sort since this will be _lots_ of text being returned in an
	// N+1 fashion.
	@Override
	public <T extends Transcribable> Transcription transcribe(T payload) {

		var clazz = payload.getClass().getName();
		var payloadKeyAsJson = JsonUtils.write(payload.transcriptionKey());

		var transcription = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);

		if (null == transcription) {
			db.sql("insert into transcription(payload, payload_class) values (?,?)")
				.params(payloadKeyAsJson, clazz)
				.update();
			transcription = this.readThroughTranscriptionByKey(clazz, payloadKeyAsJson);
		}

		return transcription;
	}

}
