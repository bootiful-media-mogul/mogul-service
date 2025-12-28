package com.joshlong.mogul.api.cache;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * the idea here is simple, but i couldn't try it out on the plane: what if we implemented
 * a {@link CacheManager} in terms of PostgreSQL's "un-logged" tables. Un-logged tables
 * disable the write-ahead-log, so there are no guarantees about consistency and data
 * integrity, but that's fine for cache semantics. Such a table can go 2.5x faster than
 * the same writes to a logged table.
 *
 * @author Josh Long
 */
class PostgresqlUnloggedTableCacheManager implements CacheManager {

	private final JdbcClient db;

	private final TransactionTemplate tx;

	PostgresqlUnloggedTableCacheManager(DataSource dataSource) {

		Assert.notNull(dataSource, "the dataSource must not be null");

		this.db = JdbcClient.create(dataSource);

		var txManager = new DataSourceTransactionManager(dataSource);
		txManager.afterPropertiesSet();

		this.tx = new TransactionTemplate(txManager);
		this.tx.afterPropertiesSet();
	}

	@Override
	public void resetCaches() {
		// execute in batch, at least
		this.tx.execute(_ -> {
			CacheManager.super.resetCaches();
			return null;
		});
	}

	@Override
	public @Nullable Cache getCache(String name) {
		return new PostgresqlUnloggedTableCache(name);
	}

	@Override
	public Collection<String> getCacheNames() {
		return List.of();
	}

	static class PostgresqlUnloggedTableCache implements Cache {

		private final String cacheName;

		PostgresqlUnloggedTableCache(String cacheName) {
			this.cacheName = cacheName;
		}

		@Override
		@NullMarked
		public String getName() {
			return this.cacheName;
		}

		@Override
		@NullMarked
		public Object getNativeCache() {
			return null;
		}

		@Override
		public @Nullable ValueWrapper get(Object key) {
			return null;
		}

		@Override
		public @Nullable <T> T get(Object key, @Nullable Class<T> type) {
			return null;
		}

		@Override
		public @Nullable <T> T get(Object key, Callable<T> valueLoader) {
			return null;
		}

		@Override
		public void put(Object key, @Nullable Object value) {

		}

		@Override
		public void evict(Object key) {

		}

		@Override
		public void clear() {

		}

	}

}
