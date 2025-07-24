package com.joshlong.mogul.api.utils;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

public abstract class CollectionUtils {

	public static <T> T firstOrNull(Collection<T> collection) {
		return null == collection || collection.isEmpty() ? null : collection.iterator().next();
	}

	public static <T> T firstOrNull(Iterable<T> iterable) {
		if (iterable == null)
			return null;
		for (var t : iterable) {
			return t;
		}
		return null;
	}

	public static <K, V> ConcurrentMap<K, V> evictingConcurrentMap(int maxSize, Duration ttl,
			RemovalListener<K, V> removalListener) {
		return Caffeine.newBuilder()
			.maximumSize(maxSize)
			.expireAfterWrite(ttl)
			.evictionListener(removalListener)
			.build()
			.asMap();
	}

	public static <K, V> ConcurrentMap<K, V> evictingConcurrentMap(int maxSize, Duration ttl) {
		var rl = new RemovalListener<K, V>() {
			@Override
			public void onRemoval(@Nullable K key, @Nullable V value, RemovalCause cause) {
			}
		};
		return evictingConcurrentMap(maxSize, ttl, rl);
	}

}
