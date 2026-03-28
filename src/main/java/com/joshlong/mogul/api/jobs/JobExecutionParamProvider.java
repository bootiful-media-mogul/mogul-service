package com.joshlong.mogul.api.jobs;

import java.util.Map;
import java.util.function.Supplier;

public interface JobExecutionParamProvider {

	boolean supports(Job job);

	Map<String, Supplier<Object>> prepare(JobExecution jobExecution);

}
