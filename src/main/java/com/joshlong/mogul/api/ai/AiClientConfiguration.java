package com.joshlong.mogul.api.ai;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiEmbeddingDeserializer;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.Set;

@Configuration
@ImportRuntimeHints(AiClientConfiguration.Hints.class)
class AiClientConfiguration {

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}

	@Bean
	DefaultAiClient aiClient(ImageModel imageModel, ChatClient chatClient) {
		return new DefaultAiClient(imageModel, chatClient);
	}

	// todo remove these. these should be in Spring AI. i submitted a PR, but
	// unfortunately it won't have landed before Spring AI 2.0 M1
	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			for (var c : Set.of(OpenAiApi.Embedding.class, OpenAiApi.EmbeddingList.class,
					OpenAiEmbeddingDeserializer.class)) {
				hints.reflection().registerType(c, MemberCategory.values());
			}
		}

	}

}
