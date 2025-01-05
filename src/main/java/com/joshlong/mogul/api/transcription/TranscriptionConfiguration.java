package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// @EnableConfigurationProperties(TranscriptionProperties.class)
class TranscriptionConfiguration {

	@Bean
	ChunkingTranscriber chunkingTranscriptionService(ApiProperties transcriptionProperties,
			OpenAiAudioTranscriptionModel transcriptionModel) {
		var root = transcriptionProperties.transcriptions().root();
		return new ChunkingTranscriber(transcriptionModel, root, (10 * 1024 * 1024));
	}

}
