package com.joshlong.mogul.api.transcripts.audio;

import com.joshlong.mogul.api.ApiProperties;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TranscriptAudioConfiguration {

	@Bean
	ChunkingTranscriber chunkingTranscriber(ApiProperties properties, OpenAiAudioTranscriptionModel transcriptModel) {
		var root = properties.transcripts().root();
		return new ChunkingTranscriber(transcriptModel, root, (10 * 1024 * 1024));
	}

}
