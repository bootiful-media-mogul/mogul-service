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
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class ProcessorsConfiguration {

	@Bean
	InitializingBean processorsAmqpInitialization(AmqpAdmin amqpAdmin) {
		return () -> {
			this.doRegisterAmqpQueue(amqpAdmin, ProcessorHeaders.PROCESSOR_REPLIES);
			this.doRegisterAmqpQueue(amqpAdmin, ProcessorHeaders.PROCESSOR_REQUESTS);
		};
	}

	private void doRegisterAmqpQueue(AmqpAdmin amqpAdmin, String name) {

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
			JsonMapper jsonMapper, ApplicationEventPublisher publisher, TransactionTemplate transactionTemplate) {
		return new DefaultProcessors(requests, jsonMapper, publisher, transactionTemplate);
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
	IntegrationFlow processorRepliesIntegrationFlow(JsonMapper jsonMapper, DefaultProcessors processors,
			ConnectionFactory connectionFactory) {
		var inboundAdapter = Amqp.inboundAdapter(connectionFactory, ProcessorHeaders.PROCESSOR_REPLIES);
		var reader = jsonMapper.reader()//
			.with(DeserializationFeature.USE_LONG_FOR_INTS)//
			.forType(ProcessorResponse.class);
		return IntegrationFlow//
			.from(inboundAdapter)//
			.handle((GenericHandler<String>) (payload, _) -> {
				processors.complete(reader.readValue(payload));
				return null;
			})
			.get();
	}

}
