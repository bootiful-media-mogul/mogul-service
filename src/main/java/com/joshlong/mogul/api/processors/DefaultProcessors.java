package com.joshlong.mogul.api.processors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

// todo encrypt the body values
class DefaultProcessors implements ApplicationEventPublisherAware, Processors {

	private final AtomicReference<ApplicationEventPublisher> applicationEventPublisher = new AtomicReference<>();

	private final MessageChannel requests;

	private final JsonMapper jsonMapper;

	private final TransactionTemplate transactionTemplate;

	DefaultProcessors(MessageChannel requests, TransactionTemplate transactionTemplate, JsonMapper jsonMapper) {
		this.requests = requests;
		this.jsonMapper = jsonMapper;
		this.transactionTemplate = transactionTemplate;
	}

	@Override
	public void process(String processorId, String correlationId, Map<String, Object> params) throws Exception {
		var bodyAsJson = this.jsonMapper.writer().writeValueAsString(params);
		var build = MessageBuilder.withPayload(bodyAsJson)//
			.setHeader("processor-id", processorId)//
			.setHeader("processor-request-id", correlationId)
			.build();
		var when = Instant.now();
		this.requests.send(build);
		this.transactionTemplate.executeWithoutResult(_ -> this.applicationEventPublisher.get()
			.publishEvent(new ProcessorLaunchedEvent(processorId, params, when)));
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher.set(applicationEventPublisher);
	}

}
