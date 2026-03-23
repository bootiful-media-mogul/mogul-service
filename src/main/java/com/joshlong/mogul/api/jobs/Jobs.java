package com.joshlong.mogul.api.jobs;

import java.util.Map;
import java.util.function.Supplier;

public interface Jobs {

	Map<String, Job> jobs();

	JobExecution prepareJobExecution(Long mogulId, String jobName, Map<String, Supplier<Object>> context)
			throws Exception;

	JobExecution getJobExecution(Long id);

	void launchJobExecution(Long mogulId, Long jobExecutionId, Map<String, Supplier<Object>> context)
			throws JobLaunchException;

}
