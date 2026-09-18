package com.joshlong.mogul.api.jobs;

import java.util.Map;

public interface Jobs {

	/**
	 * the jobs this node knows how to run, by name.
	 */
	Map<String, Job> jobs();

	/**
	 * hands the job to JobRunr, which persists it, hands it to exactly one node, and
	 * retries it if that node dies mid-run. returns as soon as it is enqueued -- the work
	 * happens on a background thread, quite possibly on another replica.
	 */
	void launch(Long mogulId, String jobName, Map<String, Object> context) throws JobException;

}
