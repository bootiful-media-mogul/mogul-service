package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.mogul.MogulService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
class IndexingJobRunner {

	private final AtomicBoolean running = new AtomicBoolean(true);

	private final Jobs jobs;

	private final MogulService mogulService;

	private final String jobName = "podcastIndexerJob";

	private final Job job;

	private final Logger log = LoggerFactory.getLogger(getClass());

	IndexingJobRunner(Jobs jobs, Map<String, Job> jobMap, MogulService mogulService) {
		this.jobs = jobs;
		this.job = jobMap.get(this.jobName);
		this.mogulService = mogulService;
	}

	@EventListener
	void runJobOnAuthentication(AuthenticationSuccessEvent event) throws Exception {
		if (this.running.compareAndSet(false, true)) {
			log.info("running the indexing job!");
			var name = event.getAuthentication().getName();
			var mogul = mogulService.getMogulByName(name);
			var ctx = Map.of(Job.MOGUL_ID_KEY, (Object) mogul.id());
			for (var key : this.job.requiredContextAttributes())
				Assert.state(ctx.containsKey(key), "the context must contain a value for the key [" + key + "]");
			this.jobs.launch(this.jobName, ctx);
		}
	}

}

@Service
class InMemoryJobs implements Jobs {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Map<String, Job> jobs;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	private final Map<Long, Map<String, Job>> jobsInFlight = new ConcurrentHashMap<>();

	InMemoryJobs(Map<String, Job> jobs) {
		this.jobs = jobs;
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
		try {
			var result = this.executor.submit(() -> {
				try {
					return jobsMap.computeIfAbsent(jobName, jobs::get).run(context);
				} //
				catch (Throwable e) {
					return Job.Result.error(context, e);
				}
			}).get();
			this.log.info("Job {} launched {} for mogulId {}", jobName,
					result.success() ? "successfully" : "unsuccessfully", mogulId);

		} //
		catch (Exception e) {
			throw new JobLaunchException(e.getMessage(), e);
		}

	}

}