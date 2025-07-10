package com.joshlong.mogul.api.utils;

import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.concurrent.ConcurrentMap;

public abstract class CollectionUtils {

	public static <K, V> ConcurrentMap<K, V> evictingConcurrentMap(int maxSize, Duration ttl) {
		return Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl).<K, V>build().asMap();
	}

}
