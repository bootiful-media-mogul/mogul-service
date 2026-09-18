package com.joshlong.mogul.api.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.joshlong.mogul.api.ApiProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.integration.amqp.dsl.Amqp;
import org.springframework.integration.core.GenericTransformer;
import org.springframework.integration.dsl.IntegrationFlow;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

@Configuration
@ImportRuntimeHints(CacheConfiguration.Hints.class)
class CacheConfiguration {

	// so we can exclude ourselves from eviction notifications we just published
	private final String node = UUID.randomUUID().toString();

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
			hints.reflection().registerType(CacheEviction.class, MemberCategory.values());
		}

	}

	@Bean
	FanoutExchange mogulCacheEvictionsExchange(ApiProperties properties) {
		return ExchangeBuilder //
			.fanoutExchange(properties.amqp().cacheEvictions())//
			.durable(true) //
			.build();
	}

	@Bean
	AnonymousQueue mogulCacheEvictionsQueue() {
		return new AnonymousQueue();
	}

	@Bean
	Binding mogulCacheEvictionsBinding(Queue mogulCacheEvictionsQueue, FanoutExchange mogulCacheEvictionsExchange) {
		return BindingBuilder.bind(mogulCacheEvictionsQueue).to(mogulCacheEvictionsExchange);
	}

	@Bean
	BroadcastingCacheManager cacheManager(ApiProperties properties, AmqpTemplate amqpTemplate, ObjectMapper json,
			FanoutExchange mogulCacheEvictionsExchange) {
		var log = LoggerFactory.getLogger(getClass());
		var caffeine = Caffeine.newBuilder()//
			.maximumSize(properties.cache().maxEntries())//
			.expireAfterWrite(Duration.ofDays(1))//
			.recordStats();
		var caffeineCacheManager = new CaffeineCacheManager();
		caffeineCacheManager.setCaffeine(caffeine);
		var exchange = mogulCacheEvictionsExchange.getName();
		log.info("this node is [{}]; broadcasting cache evictions to [{}].", this.node, exchange);
		return new BroadcastingCacheManager(caffeineCacheManager, this.node,
				eviction -> amqpTemplate.convertAndSend(exchange, "", json.writeValueAsString(eviction)));
	}

	@Bean
	IntegrationFlow mogulCacheEvictionsInboundIntegrationFlow(ConnectionFactory connectionFactory, ObjectMapper json,
			BroadcastingCacheManager cacheManager, Queue mogulCacheEvictionsQueue) {
		return IntegrationFlow //
			.from(Amqp.inboundAdapter(connectionFactory, mogulCacheEvictionsQueue)) //
			.transform(
					(GenericTransformer<String, CacheEviction>) source -> json.readValue(source, CacheEviction.class)) //
			.handle(CacheEviction.class, (payload, headers) -> {
				cacheManager.apply(payload);
				return null;
			}) //
			.get();
	}

}
