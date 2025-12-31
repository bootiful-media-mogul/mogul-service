package com.joshlong.mogul.api.jobs;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
class InMemoryJobs implements Jobs {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Map<String, Job> jobs;

	private final Map<Long, Map<String, CompletableFuture<Job.Result>>> jobsInFlightPerMogul = new ConcurrentHashMap<>();

	InMemoryJobs(Map<String, Job> jobs) {
		this.jobs = jobs;
	}

	@Override
	public Map<String, Job> jobs() {
		return new ConcurrentHashMap<>(this.jobs);
	}

	@Override
	public CompletableFuture<Job.Result> launch(String jobName, Map<String, Object> ctxt) {
		var context = new HashMap<>(ctxt);
		Assert.state(!context.isEmpty(), "you must provide at least a valid mogulId");
		Assert.state(jobs.containsKey(jobName), "the job name doesn't exist");
		Assert.hasText(jobName, "there must be a valid jobName");
		var mogulId = (Long) context.get(Job.MOGUL_ID_KEY);
		var job = jobs.get(jobName);
		Assert.notNull(job, "the job named [" + jobName + "] does not exist!");
		for (var required : job.requiredContextAttributes()) {
			Assert.isTrue(context.containsKey(required),
					"the context must contain a value" + " for the required attribute [" + required + "]");
		}
		var mogulInFlightJobsMap = this.jobsInFlightPerMogul.computeIfAbsent(mogulId, _ -> new ConcurrentHashMap<>());
		return mogulInFlightJobsMap.computeIfAbsent(jobName,
				_ -> this.future(jobName, mogulId, job, context, mogulInFlightJobsMap));
	}

	private @NonNull CompletableFuture<Job.Result> future(String jobName, Long mogulId, Job job,
			HashMap<String, Object> context, Map<String, CompletableFuture<Job.Result>> mogulInFlightJobsMap) {
		return CompletableFuture.supplyAsync(() -> {
			var result = (Job.Result) null;
			try {
				this.log.info("launching job {} for mogulId {}", jobName, mogulId);
				result = job.run(context);
			} //
			catch (Throwable e) {
				result = Job.Result.error(context, e);
			} //
			finally {
				mogulInFlightJobsMap.remove(jobName);
			}
			return result;
		});
	}

}