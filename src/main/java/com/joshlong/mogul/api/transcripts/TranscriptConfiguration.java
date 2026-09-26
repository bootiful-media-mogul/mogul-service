package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.TranscribableResolver;
import com.joshlong.mogul.api.processors.Processors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Configuration
class TranscriptConfiguration {

	@Bean
	DefaultTranscriptService defaultTranscriptService(JdbcClient db, TranscriptRowMapper transcriptRowMapper,
			ApplicationEventPublisher publisher, Map<String, TranscribableResolver<?>> repositories,
			Processors processors, TransactionTemplate transactionTemplate) {
		return new DefaultTranscriptService(transcriptRowMapper, db, repositories.values(), publisher, processors,
				transactionTemplate);
	}

	@Bean
	TranscriptRowMapper transcriptRowMapper() {
		return new TranscriptRowMapper();
	}

}
