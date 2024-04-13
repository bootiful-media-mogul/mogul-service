package com.joshlong.mogul.api.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshlong.mogul.api.ApiProperties;
import org.springframework.ai.autoconfigure.openai.OpenAiChatProperties;
import org.springframework.ai.chat.ChatClient;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
class AiClientConfiguration {

	@Bean
	TranscriptionClient transcriptionClient(ObjectMapper om, ApiProperties properties, S3Client s3, AmqpTemplate amqp) {
		var input = properties.transcription().s3().inputBucket();
		var output = properties.transcription().s3().outputBucket();
		return new TranscriptionClient(om, s3, amqp, input, output);
	}

	@Bean
	DefaultAiClient aiClient(ChatClient aiClient, TranscriptionClient transcriptionClient,
			OpenAiChatProperties properties) {
		return new DefaultAiClient(new RestTemplateBuilder().build(), aiClient, properties.getApiKey(),
				transcriptionClient);
	}

}
