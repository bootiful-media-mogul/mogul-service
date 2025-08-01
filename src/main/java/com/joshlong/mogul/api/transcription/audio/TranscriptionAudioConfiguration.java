package com.joshlong.mogul.api.transcription.audio;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TranscriptionAudioConfiguration {

	@Bean
	ChunkingTranscriber chunkingTranscriber(ApiProperties transcriptionProperties,
			OpenAiAudioTranscriptionModel transcriptionModel) {
		var root = transcriptionProperties.transcriptions().root();
		return new ChunkingTranscriber(transcriptionModel, root, (10 * 1024 * 1024));
	}

}
