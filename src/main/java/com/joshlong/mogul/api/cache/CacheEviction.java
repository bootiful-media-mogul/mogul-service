package com.joshlong.mogul.api.cache;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

/**
 * one cache entry, gone on the node named in {@code node}. a null {@link #key()} means
 * the whole cache was cleared rather than a single entry evicted.
 */
public record CacheEviction(String node, String cache, @Nullable String key, @Nullable String keyType) {

	private static final String LONG = "long";

	private static final String STRING = "string";

	static CacheEviction of(String node, String cache, Object key) {
		Assert.notNull(key, "the key must not be null");
		if (key instanceof Long l)
			return new CacheEviction(node, cache, Long.toString(l), LONG);
		if (key instanceof String s)
			return new CacheEviction(node, cache, s, STRING);
		throw new IllegalArgumentException("can't broadcast an eviction for a key of type [" + key.getClass().getName()
				+ "]; only Long and String keys are supported");
	}

	static CacheEviction clearing(String node, String cache) {
		return new CacheEviction(node, cache, null, null);
	}

	@Nullable
	Object typedKey() {
		if (this.key == null)
			return null;
		if (LONG.equals(this.keyType))
			return Long.valueOf(this.key);
		if (STRING.equals(this.keyType))
			return this.key;
		throw new IllegalStateException("unknown key type [" + this.keyType + "]");
	}

}
