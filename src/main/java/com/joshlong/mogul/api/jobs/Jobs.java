package com.joshlong.mogul.api.jobs;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

interface Jobs {

	Map<String, Job> jobs();

	JobExecution prepareJobExecution(Long mogulId, String jobName, Map<String, Supplier<Object>> context);

	CompletableFuture<JobExecutionResult> launchJobExecution(Long mogulId, Long jobExecutionId,
			Map<String, Object> context) throws JobLaunchException;

}
