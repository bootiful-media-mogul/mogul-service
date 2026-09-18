package com.joshlong.mogul.api.jobs;

import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

import java.util.Map;

public record MogulJobRequest(String jobName, Long mogulId, Map<String, Object> context) implements JobRequest {

	@Override
	public Class<? extends JobRequestHandler<?>> getJobRequestHandler() {
		return MogulJobRequestHandler.class;
	}

}
