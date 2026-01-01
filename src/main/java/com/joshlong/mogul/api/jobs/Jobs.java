package com.joshlong.mogul.api.jobs;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

interface Jobs {

	Map<String, Job> jobs();

	CompletableFuture<Job.Result> launch(String jobName, Map<String, Object> context) throws JobLaunchException;

}
