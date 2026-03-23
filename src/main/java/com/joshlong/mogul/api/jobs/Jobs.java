package com.joshlong.mogul.api.jobs;

import java.util.Map;
import java.util.function.Supplier;

public interface Jobs {

	Map<String, Job> jobs();

	JobExecution prepare(Long mogulId, String jobName, Map<String, Supplier<Object>> context) throws Exception;

	JobExecution getJobExecution(Long id);

	void launch(Long mogulId, Long jobExecutionId, Map<String, Supplier<Object>> context) throws JobLaunchException;

}
