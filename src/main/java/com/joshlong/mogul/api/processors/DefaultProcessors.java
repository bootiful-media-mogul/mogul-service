package com.joshlong.mogul.api.processors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// todo encrypt the body values
@ImportRuntimeHints(DefaultProcessors.Hints.class)
class DefaultProcessors implements Processors {

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			for (var pr : new Class<?>[] { ProcessorResponse.class, ProcessorRequest.class }) {
				hints.reflection().registerType(pr, MemberCategory.values());
			}
		}

	}

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MessageChannel requests;

	private final JsonMapper jsonMapper;

	private final ApplicationEventPublisher publisher;

	private final TransactionTemplate transactionTemplate;

	DefaultProcessors(MessageChannel requests, JsonMapper jsonMapper, ApplicationEventPublisher publisher,
			TransactionTemplate transactionTemplate) {
		this.requests = requests;
		this.jsonMapper = jsonMapper;
		this.publisher = publisher;
		this.transactionTemplate = transactionTemplate;
	}

	@Override
	public void process(String processorId, Map<String, Object> context) throws Exception {
		var correlationId = UUID.randomUUID().toString();
		var envelope = new ProcessorRequest(processorId, correlationId, context);
		var message = MessageBuilder.withPayload(this.jsonMapper.writeValueAsString(envelope))//
			.setHeader(ProcessorHeaders.PROCESSOR_ID, processorId)//
			.setHeader(ProcessorHeaders.PROCESSOR_REQUEST_ID, correlationId)//
			.build();
		var when = Instant.now();
		this.log.debug("launching processor [{}] with correlation id [{}]", processorId, correlationId);
		this.requests.send(message);
		this.publish(new ProcessorLaunchedEvent(processorId, correlationId, context, when));
	}

	void complete(ProcessorResponse response) {
		this.log.debug("processor [{}] finished [{}]: success = {}", response.processorId(), response.correlationId(),
				response.success());
		this.publish(new ProcessorCompletedEvent(response.processorId(), response.correlationId(), response.context(),
				Instant.now(), response.success(), response.error()));
	}

	private void publish(Object event) {
		this.transactionTemplate.executeWithoutResult(_ -> this.publisher.publishEvent(event));
	}

}
