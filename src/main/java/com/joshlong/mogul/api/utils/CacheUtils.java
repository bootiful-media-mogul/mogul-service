package com.joshlong.mogul.api.utils;

import org.springframework.cache.Cache;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class CacheUtils {

	public static <T> Set<T> notPresentInCache(Cache cache, Collection<T> keys) {
		var notPresent = new HashSet<T>();
		for (var k : keys)
			if (cache.get(k) == null)
				notPresent.add(k);
		return notPresent;
	}

}
