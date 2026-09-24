package com.joshlong.mogul.api.processors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/// todo delete this as soon as u have a workign e2e
@Configuration
class ProcessorTests implements ApplicationRunner {

	private final Processors processors;

	ProcessorTests(Processors processors) {
		this.processors = processors;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		this.processors.process("mediaNormalizationProcessor", "123", Map.of("s3", "http://adobe.com", "id", 2L));
		IO.println("Done processing media normalization request!");
	}

}
