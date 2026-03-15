package com.joshlong.mogul.api.jobs;

import java.util.Map;

import static com.joshlong.mogul.api.jobs.ResultUtils.validate;

public record JobExecutionResult(boolean success, Map<String, Object> context) {

	public static JobExecutionResult error(Map<String, Object> context, Throwable throwable) {
		var newContextMap = validate(context);
		newContextMap.put(Job.EXCEPTION_KEY, throwable);
		return new JobExecutionResult(false, newContextMap);
	}

	public static JobExecutionResult ok(Map<String, Object> context) {
		return new JobExecutionResult(true, validate(context));
	}
}
