package com.joshlong.mogul.api.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.Assert;

import java.util.Map;

public class MogulJobRequestHandler implements org.jobrunr.jobs.lambdas.JobRequestHandler<MogulJobRequest> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Map<String, Job> jobs;

	private final ApplicationEventPublisher publisher;

	public MogulJobRequestHandler(Map<String, Job> jobs, ApplicationEventPublisher publisher) {
		this.jobs = jobs;
		this.publisher = publisher;
		Assert.notNull(this.jobs, "the jobs must not be null");
		Assert.notNull(this.publisher, "the publisher must not be null");
	}

	@Override
	public void run(MogulJobRequest request) {
		var jobName = request.jobName();
		var job = this.jobs.get(jobName);
		Assert.state(job != null, () -> "there is no job named [" + jobName + "] to run!");
		var context = new MapJobExecutionContext(request.mogulId(), request.context());
		this.publisher.publishEvent(new JobStartedEvent(jobName, request.mogulId()));
		var result = (JobExecutionResult) null;
		try {
			result = job.run(context);
		} //
		catch (Throwable throwable) {
			this.log.error("the job named [{}] for mogul [{}] failed", jobName, request.mogulId(), throwable);
			result = JobExecutionResult.error(throwable);
		}
		this.publisher.publishEvent(new JobStoppedEvent(jobName, request.mogulId(), result.success()));
		// rethrow on failure so that JobRunr sees it, records it, and applies its own
		// retry policy. swallowing it would leave a job marked succeeded that never did.
		if (!result.success()) {
			var cause = result.context().get(Job.EXCEPTION_KEY);
			throw new IllegalStateException("the job named [" + jobName + "] failed",
					cause instanceof Throwable throwable ? throwable : null);
		}
	}

}
