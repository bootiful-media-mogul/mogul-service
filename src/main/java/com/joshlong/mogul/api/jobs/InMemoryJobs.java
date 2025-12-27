package com.joshlong.mogul.api.jobs;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
class InMemoryJobs implements Jobs {

	private final Map<String, Job> jobs;

	private final ApplicationEventPublisher publisher;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final Map<Long, Map<String, Job>> jobsInFlight = new ConcurrentHashMap<>();

	InMemoryJobs(Map<String, Job> jobs, ApplicationEventPublisher publisher) {
		this.jobs = jobs;
		this.publisher = publisher;
	}

	@Override
	public Map<String, Job> jobs() {
		return new ConcurrentHashMap<>(this.jobs);
	}

	@Override
	public void launch(String jobName, Map<String, Object> context) throws JobLaunchException {
		Assert.state(!context.isEmpty(), "you must provide at least a valid mogulId");
		Assert.state(jobs.containsKey(jobName), "the job name doesn't exist");
		Assert.hasText(jobName, "there must be a valid jobName");
		var mogulId = (Long) context.get(Job.MOGUL_ID_KEY);
		var jobsMap = this.jobsInFlight.computeIfAbsent(mogulId, _ -> new ConcurrentHashMap<>());
		if (jobsMap.containsKey(jobName)) {
			throw new JobLaunchException("job-already-running");
		}
		executor.submit(new Runnable() {
			@Override
			public void run() {

			}
		});

	}

	private Job.Result run(String jn, Map<String, Object> map) {
		var job = jobs.get(jn);
		try {
			return job.run(map);
		} //
		catch (Exception e) {
			return Job.Result.error(map, e);
		}
	}

}

class JobLaunchException extends Exception {

	private final String message;

	JobLaunchException(String message) {
		super(message);
		this.message = message;
	}

	public String getMessage() {
		return this.message;
	}

}