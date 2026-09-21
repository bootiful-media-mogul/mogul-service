package com.joshlong.mogul.api.cache;

import com.joshlong.mogul.api.ApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * the caches are local to a node, so an edit on one node is invisible to the others until
 * somebody tells them -- and with a day-long expiry, "until somebody tells them" is the
 * whole of the guarantee. these cover both halves of that conversation: that an eviction
 * announced elsewhere lands here, and that one performed here is announced.
 * <p>
 * the cache used is one no service asks for, so nothing else in the suite is disturbed by
 * entries appearing and vanishing in it.
 */
@SpringBootTest
class CacheEvictionBroadcastTest {

	private static final String CACHE = "cacheEvictionBroadcastTest";

	@Test
	void anEvictionFromAnotherNodeClearsTheEntryHere(@Autowired CacheManager cacheManager,
			@Autowired AmqpTemplate amqpTemplate, @Autowired ObjectMapper json, @Autowired ApiProperties properties) {
		var cache = cacheManager.getCache(CACHE);
		assertNotNull(cache, "the cache manager makes caches on demand");
		var key = System.nanoTime();
		cache.put(key, "cached on this node");
		assertNotNull(cache.get(key), "it is here to begin with");

		// a node that isn't this one, so the echo-suppression doesn't discard it.
		var elsewhere = CacheEviction.of(UUID.randomUUID().toString(), CACHE, key);
		amqpTemplate.convertAndSend(properties.amqp().cacheEvictions(), "", json.writeValueAsString(elsewhere));

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertNull(cache.get(key), "the eviction from the other node should have landed"));
	}

	@Test
	void anEvictionHereIsAnnouncedToTheOtherNodes(@Autowired CacheManager cacheManager,
			@Autowired AmqpTemplate amqpTemplate, @Autowired AmqpAdmin amqpAdmin, @Autowired ObjectMapper json,
			@Autowired ApiProperties properties) {
		var probe = this.bindProbeQueue(amqpAdmin, properties);
		try {
			var cache = cacheManager.getCache(CACHE);
			assertNotNull(cache);
			var key = System.nanoTime();
			cache.put(key, "cached on this node");
			cache.evictIfPresent(key);

			var announced = this.awaitEviction(amqpTemplate, json, probe);
			assertEquals(CACHE, announced.cache(), "the announcement names the cache");
			assertEquals(key, announced.typedKey(), "and carries the key back as a Long, not as text");
		} //
		finally {
			amqpAdmin.deleteQueue(probe);
		}
	}

	/**
	 * every service that evicts does so inside a transaction, and the broadcast is held
	 * back until that transaction commits -- announcing it earlier would invite another
	 * node to read the row before the write landed and cache the old value all over
	 * again. so the path that actually matters in production is this one, not the bare
	 * call above.
	 */
	@Test
	void anEvictionInsideATransactionIsAnnouncedOnceItCommits(@Autowired CacheManager cacheManager,
			@Autowired AmqpTemplate amqpTemplate, @Autowired AmqpAdmin amqpAdmin, @Autowired ObjectMapper json,
			@Autowired ApiProperties properties, @Autowired TransactionTemplate transactionTemplate) {
		var probe = this.bindProbeQueue(amqpAdmin, properties);
		try {
			var cache = cacheManager.getCache(CACHE);
			assertNotNull(cache);
			var key = System.nanoTime();
			cache.put(key, "cached on this node");
			transactionTemplate.execute(_ -> cache.evictIfPresent(key));

			var announced = this.awaitEviction(amqpTemplate, json, probe);
			assertEquals(key, announced.typedKey(), "the commit should have released the announcement");
		} //
		finally {
			amqpAdmin.deleteQueue(probe);
		}
	}

	/**
	 * stands in for a second node: its own queue on the same fanout, so it sees every
	 * announcement this one makes. durable and non-exclusive because a transient
	 * non-exclusive queue is refused outright by rabbit 4.x, and deleted by the caller.
	 */
	private String bindProbeQueue(AmqpAdmin amqpAdmin, ApiProperties properties) {
		var exchangeName = properties.amqp().cacheEvictions();
		FanoutExchange exchange = ExchangeBuilder.fanoutExchange(exchangeName).durable(true).build();
		var queue = QueueBuilder.durable("cache-eviction-probe-" + UUID.randomUUID()).build();
		amqpAdmin.declareExchange(exchange);
		amqpAdmin.declareQueue(queue);
		amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange));
		return queue.getName();
	}

	private CacheEviction awaitEviction(AmqpTemplate amqpTemplate, ObjectMapper json, String queue) {
		var received = new CacheEviction[1];
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			var body = amqpTemplate.receiveAndConvert(queue);
			assertNotNull(body, "nothing announced yet");
			received[0] = json.readValue((String) body, CacheEviction.class);
		});
		return received[0];
	}

}
