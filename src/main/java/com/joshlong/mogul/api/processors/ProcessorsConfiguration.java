package com.joshlong.mogul.api.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.core.GenericHandler;
import org.springframework.integration.dsl.DirectChannelSpec;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.messaging.MessageChannel;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * the client half of the {@code processors} module: requests go out on one queue, replies
 * come back on another, and a {@link ProcessorCompletedEvent} is published for whichever
 * part of the api cares about that {@code processorId}.
 */
@Configuration
class ProcessorsConfiguration {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Bean
	InitializingBean processorsAmqpInitialization(AmqpAdmin amqpAdmin) {
		return () -> {
			this.register(amqpAdmin, ProcessorHeaders.PROCESSOR_REPLIES);
			this.register(amqpAdmin, ProcessorHeaders.PROCESSOR_REQUESTS);
		};
	}

	private void register(AmqpAdmin amqpAdmin, String name) {

		var queue = QueueBuilder.durable(name).build();
		amqpAdmin.declareQueue(queue);

		var exchange = ExchangeBuilder.directExchange(name).build();
		amqpAdmin.declareExchange(exchange);

		var noargs = BindingBuilder.bind(queue)//
			.to(exchange)//
			.with(name)//
			.noargs();
		amqpAdmin.declareBinding(noargs);
	}

	@Bean
	DefaultProcessors defaultProcessors(@Qualifier(ProcessorHeaders.PROCESSOR_REQUESTS) MessageChannel requests,
			TransactionTemplate transactionTemplate, JsonMapper jsonMapper) {
		return new DefaultProcessors(requests, transactionTemplate, jsonMapper);
	}

	@Bean(name = ProcessorHeaders.PROCESSOR_REQUESTS)
	DirectChannelSpec processorRequests() {
		return MessageChannels.direct();
	}

	@Bean
	IntegrationFlow processorRequestsIntegrationFlow(
			@Qualifier(ProcessorHeaders.PROCESSOR_REQUESTS) MessageChannel processorRequests, //
			AmqpTemplate template //
	) {
		var amqpOutbound = Amqp.outboundAdapter(template).routingKey(ProcessorHeaders.PROCESSOR_REQUESTS);
		return IntegrationFlow.from(processorRequests).handle(amqpOutbound).get();
	}

	@Bean
	IntegrationFlow processorRepliesIntegrationFlow(JsonMapper jsonMapper, TransactionTemplate transactionTemplate,
			ApplicationEventPublisher applicationEventPublisher, ConnectionFactory connectionFactory) {
		var inboundAdapter = Amqp.inboundAdapter(connectionFactory, ProcessorHeaders.PROCESSOR_REPLIES);
		// a processor's context crosses the wire as json, where `2` and `2L` are the same
		// thing. the listeners downstream of this cast those values back to the Long ids
		// they were when they went out, so read every integral number as a Long and spare
		// them a ClassCastException that only shows up for small ids.
		var reader = jsonMapper.reader()//
			.with(DeserializationFeature.USE_LONG_FOR_INTS)//
			.forType(ProcessorResponse.class);
		return IntegrationFlow//
			.from(inboundAdapter)//
			.handle((GenericHandler<String>) (payload, _) -> {
				ProcessorResponse response = reader.readValue(payload);
				this.log.debug("processor [{}] finished [{}]: success = {}", response.processorId(),
						response.correlationId(), response.success());
				var event = new ProcessorCompletedEvent(response.processorId(), response.correlationId(),
						response.context(), Instant.now(), response.success());
				// @ApplicationModuleListener only sees events published inside a
				// transaction, and an AMQP listener thread has none of its own.
				transactionTemplate.executeWithoutResult(_ -> applicationEventPublisher.publishEvent(event));
				return null;
			})
			.get();
	}

}
