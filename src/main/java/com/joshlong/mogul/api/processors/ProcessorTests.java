package com.joshlong.mogul.api.processors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
class ProcessorTests implements ApplicationRunner {

	private final Processors processors;

	ProcessorTests(Processors processors) {
		this.processors = processors;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		this.processors.process("media-normalization", "123", Map.of("s3", "http://adobe.com", "id", 2L));
	}

}
