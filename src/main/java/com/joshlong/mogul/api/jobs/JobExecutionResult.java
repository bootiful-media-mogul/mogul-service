package com.joshlong.mogul.api.jobs;

import java.util.Map;

public record JobExecutionResult(boolean success, Map<String, Object> context) {

	public static JobExecutionResult error(Throwable throwable) {
		var m = Map.of(Job.EXCEPTION_KEY, (Object) throwable);
		return new JobExecutionResult(false, m);
	}

	public static JobExecutionResult ok() {
		return ok(Map.of());
	}

	public static JobExecutionResult ok(Map<String, Object> context) {
		return new JobExecutionResult(true, context);
	}
}
