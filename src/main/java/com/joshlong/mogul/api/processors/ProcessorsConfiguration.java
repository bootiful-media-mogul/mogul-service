package com.joshlong.mogul.api.processors;

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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

@Configuration
class ProcessorsConfiguration {

	// todo extract these values out to properties since they need to be the same for both
	// this api and the media-processor module
	private static final String PROCESSOR_REQUESTS = "processor-requests";

	private static final String PROCESSOR_REPLIES = "processor-replies";

	@Bean
	InitializingBean processorsAmqpInitialization(AmqpAdmin amqpAdmin) {
		return () -> {
			this.register(amqpAdmin, PROCESSOR_REPLIES);
			this.register(amqpAdmin, PROCESSOR_REQUESTS);
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
	DefaultProcessors defaultProcessors(@Qualifier(PROCESSOR_REQUESTS) MessageChannel requests,
			TransactionTemplate transactionTemplate, JsonMapper jsonMapper) {
		return new DefaultProcessors(requests, transactionTemplate, jsonMapper);
	}

	@Bean(name = PROCESSOR_REQUESTS)
	DirectChannelSpec processorRequests() {
		return MessageChannels.direct();
	}

	@Bean
	IntegrationFlow processorRequestsIntegrationFlow(@Qualifier(PROCESSOR_REQUESTS) MessageChannel processorRequests, //
			AmqpTemplate template //
	) {
		var amqpOutbound = Amqp.outboundAdapter(template).routingKey(PROCESSOR_REQUESTS);
		return IntegrationFlow.from(processorRequests).handle(amqpOutbound).get();
	}

	@Bean
	IntegrationFlow processorRepliesIntegrationFlow(JsonMapper jsonMapper,
			ApplicationEventPublisher applicationEventPublisher, ConnectionFactory connectionFactory) {
		var inboundAdapter = Amqp.inboundAdapter(connectionFactory, PROCESSOR_REPLIES);

		return IntegrationFlow//
			.from(inboundAdapter)//
			// todo transform the replies into events and then publish them on another
			// well-known, qualifier-by-annotation message channel.
			// as applicationevents
			.handle((GenericHandler<String>) (payload, headers) -> {
				IO.println("payload: " + payload);
				headers.forEach((k, v) -> IO.println("header: " + k + " value: " + v));
				var obj = jsonMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
				});
				@SuppressWarnings("unchecked")
				var context = (Map<String, Object>) obj.get("context");
				var success = (boolean) context.getOrDefault("success", false);
				var processorId = (String) context.get("processorId");
				var correlationId = (String) context.get("correlationId");

				obj.forEach((k, v) -> IO.println("payload:: key: " + k + " value: " + v));

				var processorCompletedEvent = new ProcessorCompletedEvent(processorId, correlationId, context,
						Instant.now(), success);
				applicationEventPublisher.publishEvent(processorCompletedEvent);
				return null;
			})
			.get();
	}

}
