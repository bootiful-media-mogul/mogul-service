package com.joshlong.mogul.api.jobs;

import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.joshlong.mogul.api.jobs.ResultUtils.validate;

/**
 * represents a thing that the user can have run, given some context like the current
 * mogul, a podcast, or an episode
 */
public interface Job {

	String EXCEPTION_KEY = "exception";

	String MOGUL_ID_KEY = "mogulId";

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

@Configuration
class JobRunnerConfiguration {

	@Bean
	ApplicationRunner mogulJobApplicationRunner(Jobs jobs) {
		return _ -> {
			var log = LoggerFactory.getLogger(getClass());
			var message = new StringBuilder();
			var jobsList = jobs.jobs();
			message.append("there are %s jobs available to run.".formatted(jobsList.size()))
				.append(System.lineSeparator());
			for (var entry : jobsList.keySet()) {
				message.append("\tjob %s is available to run".formatted(entry)).append(System.lineSeparator());
			}
			log.info(message.toString());
		};
	}

}

abstract class ResultUtils {

	static Map<String, Object> validate(Map<String, Object> stringObjectMap) {
		var newMap = new HashMap<>(stringObjectMap == null ? new HashMap<>() : stringObjectMap);
		Assert.state(newMap.containsKey(Job.MOGUL_ID_KEY), "you must specify a key for the mogul");
		return newMap;
	}

}