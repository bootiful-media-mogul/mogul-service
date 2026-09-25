package com.joshlong.mogul.api.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MessageChannel requests;

	private final JsonMapper jsonMapper;

	private final TransactionTemplate transactionTemplate;

	DefaultProcessors(MessageChannel requests, TransactionTemplate transactionTemplate, JsonMapper jsonMapper) {
		this.requests = requests;
		this.jsonMapper = jsonMapper;
		this.transactionTemplate = transactionTemplate;
	}

	@Override
	public void process(String processorId, String correlationId, Map<String, Object> context) throws Exception {
		var request = new ProcessorRequest(processorId, correlationId, context);
		var bodyAsJson = this.jsonMapper.writeValueAsString(request);
		var message = MessageBuilder.withPayload(bodyAsJson)//
			.setHeader(ProcessorHeaders.PROCESSOR_ID, processorId)//
			.setHeader(ProcessorHeaders.PROCESSOR_REQUEST_ID, correlationId)//
			.build();
		var when = Instant.now();
		this.log.debug("launching processor [{}] with correlation id [{}]", processorId, correlationId);
		this.requests.send(message);
		this.transactionTemplate.executeWithoutResult(_ -> this.applicationEventPublisher.get()
			.publishEvent(new ProcessorLaunchedEvent(processorId, correlationId, context, when)));
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher.set(applicationEventPublisher);
	}

}
