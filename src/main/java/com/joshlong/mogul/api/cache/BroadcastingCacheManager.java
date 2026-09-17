package com.joshlong.mogul.api.cache;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * wraps a delegate {@link CacheManager} and turns every local eviction into one the rest
 * of the cluster hears about via RabbitMQ broadcast.
 */
class BroadcastingCacheManager implements CacheManager {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final CacheManager delegate;

	private final Consumer<CacheEviction> broadcaster;

	private final String node;

	private final Map<String, Cache> caches = new ConcurrentHashMap<>();

	BroadcastingCacheManager(CacheManager delegate, String node, Consumer<CacheEviction> broadcaster) {
		Assert.notNull(delegate, "the delegate must not be null");
		Assert.hasText(node, "the node must have text");
		Assert.notNull(broadcaster, "the broadcaster must not be null");
		this.delegate = delegate;
		this.node = node;
		this.broadcaster = broadcaster;
	}

	@Override
	public @Nullable Cache getCache(String name) {
		var wrapped = this.caches.get(name);
		if (wrapped != null)
			return wrapped;
		var actual = this.delegate.getCache(name);
		if (actual == null)
			return null;
		return this.caches.computeIfAbsent(name, _ -> new BroadcastingCache(actual));
	}

	@Override
	public Collection<String> getCacheNames() {
		return this.delegate.getCacheNames();
	}

	/**
	 * applies an eviction that arrived from somewhere else. it goes to the delegate and
	 * deliberately not through {@link #getCache(String)}: evicting through the wrapper
	 * would broadcast it straight back out again, and every node would answer every other
	 * node forever.
	 */
	void apply(CacheEviction eviction) {
		if (this.node.equals(eviction.node())) {
			// our own, arriving back through the fanout. already done locally.
			return;
		}
		var cache = this.delegate.getCache(eviction.cache());
		if (cache == null) {
			this.log.debug("no cache named [{}] here; ignoring an eviction from [{}]", eviction.cache(),
					eviction.node());
			return;
		}
		var key = eviction.typedKey();
		if (key == null) {
			cache.clear();
		}
		else {
			cache.evictIfPresent(key);
		}
		this.log.debug("applied {} from [{}]", eviction, eviction.node());
	}

	// we only do the eviction once we're sure the write's commit has completed.
	private void doBroadcastAfterCommit(CacheEviction eviction) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					broadcaster.accept(eviction);
				}
			});
			return;
		}
		this.broadcaster.accept(eviction);
	}

	private final class BroadcastingCache implements Cache {

		private final Cache delegate;

		private BroadcastingCache(Cache delegate) {
			this.delegate = delegate;
		}

		@Override
		public String getName() {
			return this.delegate.getName();
		}

		@Override
		public Object getNativeCache() {
			return this.delegate.getNativeCache();
		}

		@Override
		public Cache.@Nullable ValueWrapper get(Object key) {
			return this.delegate.get(key);
		}

		@Override
		public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
			return this.delegate.get(key, type);
		}

		@Override
		public <T> @Nullable T get(Object key, Callable<T> valueLoader) {
			return this.delegate.get(key, valueLoader);
		}

		@Override
		public CompletableFuture<?> retrieve(Object key) {
			return this.delegate.retrieve(key);
		}

		@Override
		public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
			return this.delegate.retrieve(key, valueLoader);
		}

		@Override
		public void put(Object key, @Nullable Object value) {
			this.delegate.put(key, value);
		}

		@Override
		public Cache.@Nullable ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
			return this.delegate.putIfAbsent(key, value);
		}

		@Override
		public void evict(Object key) {
			this.delegate.evict(key);
			doBroadcastAfterCommit(CacheEviction.of(node, this.getName(), key));
		}

		/**
		 * the broadcast goes out whether or not anything was here to evict. "present" is
		 * a question about this node only, and the entry the other nodes are holding is
		 * exactly the one that has to go.
		 */
		@Override
		public boolean evictIfPresent(Object key) {
			var evicted = this.delegate.evictIfPresent(key);
			doBroadcastAfterCommit(CacheEviction.of(node, this.getName(), key));
			return evicted;
		}

		@Override
		public void clear() {
			this.delegate.clear();
			doBroadcastAfterCommit(CacheEviction.clearing(node, this.getName()));
		}

		@Override
		public boolean invalidate() {
			var hadEntries = this.delegate.invalidate();
			doBroadcastAfterCommit(CacheEviction.clearing(node, this.getName()));
			return hadEntries;
		}

	}

}
