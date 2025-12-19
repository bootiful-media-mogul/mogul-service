package com.joshlong.mogul.api.transcripts.audio;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;

@Configuration
class TranscriptAudioConfiguration {

	@Bean
	ChunkingTranscriber chunkingTranscriber(ApiProperties properties, OpenAiAudioTranscriptionModel transcriptModel) {
		var retryTemplate = RetryTemplate.builder().withTimeout(Duration.ofMinutes(2)).build();
		var root = properties.transcripts().root();
		return new ChunkingTranscriber(transcriptModel, root, retryTemplate, (10 * 1024 * 1024));
	}

}
