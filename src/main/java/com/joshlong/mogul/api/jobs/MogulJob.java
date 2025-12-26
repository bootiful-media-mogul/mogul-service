package com.joshlong.mogul.api.jobs;

import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;

import static com.joshlong.mogul.api.jobs.ResultUtils.validate;

/**
 * represents a thing that can be run on behalf of a
 * {@link com.joshlong.mogul.api.mogul.Mogul} , and given some context
 */
public interface MogulJob {

	String EXCEPTION_KEY = "exception";

	String MOGUL_ID_KEY = "mogulId";

	Result run(Map<String, Object> context) throws Exception;

	record Result(boolean success, Map<String, Object> context) {

		public static MogulJob.Result error(Map<String, Object> context, Throwable throwable) {
			var newContextMap = validate(context);
			newContextMap.put(MogulJob.EXCEPTION_KEY, throwable);
			return new MogulJob.Result(false, newContextMap);
		}

		public static MogulJob.Result ok(Map<String, Object> context) {
			return new MogulJob.Result(true, validate(context));
		}
	}

}

@Transactional
class MogulJobLaunchEventListener {

	private final Map<String, MogulJob> mogulJobMap;

	MogulJobLaunchEventListener(Map<String, MogulJob> mogulJobMap) {
		this.mogulJobMap = mogulJobMap;
	}

	@EventListener
	void onMogulJobLaunchEvent(MogulJobLaunchEvent event) throws Exception {
		Assert.hasText(event.jobName(), "the " + MogulJobLaunchEvent.class.getName() + " must specify a valid jobName");
		if (this.mogulJobMap.containsKey(event.jobName())) {
			var mogulJob = this.mogulJobMap.get(event.jobName());
			mogulJob.run(event.context());
		}
	}

}

@Configuration
class MogulJobRunnerConfiguration {

	@Bean
	MogulJobLaunchEventListener mogulJobLaunchEventListener(Map<String, MogulJob> jobMap) {
		return new MogulJobLaunchEventListener(jobMap);
	}

	@Bean
	ApplicationRunner mogulJobApplicationRunner(Map<String, MogulJob> mogulJobMap) {
		return _ -> {
			var log = LoggerFactory.getLogger(getClass());
			var message = new StringBuilder();
			message.append("there are %s jobs available to run.".formatted(mogulJobMap.size()))
				.append(System.lineSeparator());
			for (var entry : mogulJobMap.entrySet()) {
				message.append("\tjob %s is available to run".formatted(entry.getKey())).append(System.lineSeparator());
			}
			log.info(message.toString());
		};
	}

}

abstract class ResultUtils {

	static Map<String, Object> validate(Map<String, Object> stringObjectMap) {
		var newMap = new HashMap<>(stringObjectMap == null ? new HashMap<>() : stringObjectMap);
		Assert.state(newMap.containsKey(MogulJob.MOGUL_ID_KEY), "you must specify a key for the mogul");
		return newMap;
	}

}