package com.joshlong.mogul.api.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * implemented on top of Spring Modulith's
 * {@link org.springframework.modulith.events.EventPublication event publication}
 * machinery. Jobs that aren't finished when the application shuts down can be replayed,
 * too.
 */
@Service
@Transactional
class EventPublicationBackedJobs implements Jobs {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Map<String, CompletableFuture<Job.Result>> jobsInFlight = new ConcurrentHashMap<>();

	private final ApplicationEventPublisher publisher;

	private final Map<String, Job> jobs = new ConcurrentHashMap<>();

	EventPublicationBackedJobs(Map<String, Job> jobs, ApplicationEventPublisher publisher) {
		this.publisher = publisher;
		this.jobs.putAll(jobs);
	}

	@ApplicationModuleListener
	void handleJob(EventPublicationBackedJobsRunnerEvent jobs) {
		this.log.info("Received job launch request for job '{}' with context: {}", jobs.jobName(), jobs.context());
		var job = this.jobs.get(jobs.jobName());
		var context = jobs.context();
		this.log.info("Received job launch request for job '{}' with context: {}", jobs.jobName(), jobs.context());
		var result = (Job.Result) null;
		try {
			result = job.run(context);
		} //
		catch (Throwable e) {
			result = Job.Result.error(context, e);
		} //
		finally {
			if (this.jobsInFlight.containsKey(jobs.key())) {
				var completableFuture = this.jobsInFlight.get(jobs.key());
				if (completableFuture != null) {
					completableFuture.complete(result);
				}
				this.jobsInFlight.remove(jobs.key());
			}
		}
	}

	@Override
	public Map<String, Job> jobs() {
		return Map.copyOf(this.jobs);
	}

	private String keyFor(Long mogulId, String jobName, Map<String, Object> context) {
		var key = new StringBuilder();
		for (var e : context.entrySet()) {
			key.append(e.getKey()).append(e.getValue()).append("_");
		}
		return Objects.requireNonNull(mogulId) + Objects.requireNonNull(jobName) + key;
	}

	private boolean validate(String jobName, Map<String, Object> context) {
		try {
			Assert.state(!context.isEmpty(), "you must provide at least a valid mogulId");
			Assert.state(jobs.containsKey(jobName), "the job name doesn't exist");
			Assert.hasText(jobName, "there must be a valid jobName");
			var mogulId = (Long) context.get(Job.MOGUL_ID_KEY);
			Assert.notNull(mogulId, "the context must contain a valid mogulId");
			var job = jobs.get(jobName);
			Assert.notNull(job, "the job named [" + jobName + "] does not exist!");
			for (var required : job.requiredContextAttributes()) {
				Assert.isTrue(context.containsKey(required),
						"the context must contain a value" + " for the required attribute [" + required + "]");
			}
		} //
		catch (Throwable e) {
			return false;
		}
		return true;
	}

	@Override
	public CompletableFuture<Job.Result> launch(String jobName, Map<String, Object> context) throws JobLaunchException {

		if (!validate(jobName, context))
			return CompletableFuture.completedFuture(Job.Result.error(context, null));

		// run
		var mogulId = (Long) context.get(Job.MOGUL_ID_KEY);
		var key = this.keyFor(mogulId, jobName, context);
		return this.jobsInFlight.computeIfAbsent(key, _ -> {
			var cf = new CompletableFuture<Job.Result>();
			publisher.publishEvent(new EventPublicationBackedJobsRunnerEvent(key, jobName, context));
			return cf;
		});
	}

}
