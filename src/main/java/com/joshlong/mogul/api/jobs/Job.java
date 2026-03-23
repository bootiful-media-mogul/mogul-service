package com.joshlong.mogul.api.jobs;

import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Jobs are a way to run processes on the system. they're a way to migrate data. if we
 * deliver a feature that requires processing of existing data, model them as a job.
 */
public interface Job {

	// well-known headers.
	String EXCEPTION_KEY = "exception";

	String MOGUL_ID_KEY = "mogulId";

	String PODCAST_ID_KEY = "podcastId";

	String BLOG_ID_KEY = "blogId";

	String MANAGED_FILE_ID_KEY = "managedFileId";

	@NonNull
	default Set<String> requiredContextAttributes() {
		return new HashSet<>(Set.of(MOGUL_ID_KEY));
	}

	JobExecutionResult run(JobExecutionContext context) throws Exception;

}
