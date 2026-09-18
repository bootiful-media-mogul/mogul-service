package com.joshlong.mogul.api.jobs;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.postgres.PostgresStorageProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class DefaultJobs implements Jobs {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Map<String, Job> jobs;

	private final JobRequestScheduler jobScheduler;

	DefaultJobs(Map<String, Job> jobs, JobRequestScheduler jobScheduler) {
		this.jobs = jobs;
		this.jobScheduler = jobScheduler;
		Assert.notNull(this.jobs, "the jobs must not be null");
		Assert.notNull(this.jobScheduler, "the jobScheduler must not be null");
	}

	@Override
	public Map<String, Job> jobs() {
		return Map.copyOf(this.jobs);
	}

	@Override
	public void launch(Long mogulId, String jobName, Map<String, Object> context) throws JobException {
		Assert.notNull(mogulId, "the mogulId must not be null");
		if (!this.jobs.containsKey(jobName))
			throw new JobException("there is no job named [" + jobName + "]");
		var attributes = new HashMap<String, Object>(context == null ? Map.of() : context);
		// the mogul is the one attribute every job is promised, and the one the caller
		// has no business overriding.
		attributes.put(Job.MOGUL_ID_KEY, mogulId);
		var id = this.jobScheduler.enqueue(new MogulJobRequest(jobName, mogulId, attributes));
		this.log.debug("enqueued the job named [{}] for mogul [{}] as [{}]", jobName, mogulId, id);
	}

}

@ImportRuntimeHints(DefaultJobsConfiguration.Hints.class)
@Configuration
class DefaultJobsConfiguration {

	private static <T> Set<Class<? extends T>> findSubclasses(Class<T> targetInterface, String basePackage) {
		var subclasses = new HashSet<Class<? extends T>>();
		var provider = new ClassPathScanningCandidateComponentProvider(false);
		provider.addIncludeFilter(new AssignableTypeFilter(targetInterface));
		var candidates = provider.findCandidateComponents(basePackage);
		for (var bd : candidates) {
			try {
				var clazz = Class.forName(bd.getBeanClassName());
				if (!clazz.isInterface() && targetInterface.isAssignableFrom(clazz)) {
					subclasses.add(clazz.asSubclass(targetInterface));
				}
			} //
			catch (ClassNotFoundException e) {
				// Handle or log class loading errors
			}
		}

		return subclasses;
	}

	static class Hints implements RuntimeHintsRegistrar {

		@Override
		public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {

			// for (var c : findSubclasses(StorageProvider.class,
			// StorageProvider.class.getPackageName()))
			// IO.println("class: " + c.getName());

			hints.reflection().registerType(PostgresStorageProvider.class, MemberCategory.values());
		}

	}

}