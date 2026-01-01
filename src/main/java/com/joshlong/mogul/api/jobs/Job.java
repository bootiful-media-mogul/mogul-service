package com.joshlong.mogul.api.jobs;

import org.jspecify.annotations.NonNull;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.joshlong.mogul.api.jobs.ResultUtils.validate;

/**
 * Jobs are a way to run processes on the system. they're a way to migrate data. if we
 * deliver a feature that requires processing of existing data, model them as a job.
 */
public interface Job {

	// well-known headers.
	String EXCEPTION_KEY = "exception";

	String MOGUL_ID_KEY = "mogulId";

	String PODCAST_ID_KEY = "podcastId";

	@NonNull
	default Set<String> requiredContextAttributes() {
		return new HashSet<>(Set.of(MOGUL_ID_KEY));
	}

	Result run(Map<String, Object> context) throws Exception;

	record Result(boolean success, Map<String, Object> context) {

		public static Job.Result error(Map<String, Object> context, Throwable throwable) {
			var newContextMap = validate(context);
			newContextMap.put(Job.EXCEPTION_KEY, throwable);
			return new Job.Result(false, newContextMap);
		}

		public static Job.Result ok(Map<String, Object> context) {
			return new Job.Result(true, validate(context));
		}
	}

}

abstract class ResultUtils {

	static Map<String, Object> validate(Map<String, Object> stringObjectMap) {
		var newMap = new HashMap<>(stringObjectMap == null ? new HashMap<>() : stringObjectMap);
		Assert.state(newMap.containsKey(Job.MOGUL_ID_KEY), "you must specify a key for the mogul");
		return newMap;
	}

}