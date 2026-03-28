package com.joshlong.mogul.api.jobs;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Transactional
class JobExecutor {

	private final Jobs jobs;

	private final IncompleteEventPublications eventPublications;

	private final JdbcClient db;

	private final ApplicationEventPublisher applicationEventPublisher;

	private final BiFunction<Long, Map<String, Object>, Void> contextAttributeWriterLambda;

	JobExecutor(Jobs jobs, IncompleteEventPublications eventPublications, JdbcClient jdbcClient,
			ApplicationEventPublisher applicationEventPublisher,
			BiFunction<Long, Map<String, Object>, Void> contextAttributeWriterLambda) {
		this.jobs = jobs;
		this.eventPublications = eventPublications;
		this.db = jdbcClient;
		this.applicationEventPublisher = applicationEventPublisher;
		this.contextAttributeWriterLambda = contextAttributeWriterLambda;
	}

	@Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
	void checkForIncompleteEvents() {
		this.eventPublications.resubmitIncompletePublications( //
				e -> e.getApplicationEvent() instanceof JobStartedEvent);
	}

	@ApplicationModuleListener
	void onJobStartedEvent(JobStartedEvent job) {
		var jobExecution = this.jobs.getJobExecution(job.jobExecutionId());
		var context = new JobExecutionWrappingJobExecutionContext(jobExecution);
		try {
			var jobName = jobExecution.jobName();
			Assert.state(this.jobs.jobs().containsKey(jobName), "the job doesn't exist in the jobs map!");
			var jobsInstance = this.jobs.jobs().get(jobName);
			var result = jobsInstance.run(context);
			this.recordJobExecutionResult(job, result);
		} //
		catch (Throwable e) {
			var ex = JobExecutionResult.error(e);
			this.recordJobExecutionResult(job, ex);
		}
	}

	private void recordJobExecutionResult(JobStartedEvent jobStartedEvent, JobExecutionResult executionResult) {
		this.db //
			.sql("update job_execution set stop = ? , success = ? where  id  = ?") //
			.params(new Date(), executionResult.success(), jobStartedEvent.jobExecutionId()) //
			.update();
		var jobExecutionId = jobStartedEvent.jobExecutionId();
		this.contextAttributeWriterLambda.apply(jobExecutionId, executionResult.context());
		this.applicationEventPublisher.publishEvent(new JobStoppedEvent(jobStartedEvent.jobExecutionId()));
	}

	static class JobExecutionWrappingJobExecutionContext implements JobExecutionContext {

		private final JobExecution delegate;

		JobExecutionWrappingJobExecutionContext(JobExecution delegate) {
			this.delegate = delegate;
		}

		@Override
		public <T> T getContextAttribute(String paramName, Class<T> type) {
			return this.delegate.getContextAttribute(paramName, type);
		}

		@Override
		public long getContextAttributeAsLong(String paramName) {
			return this.numberForParameter(paramName).longValue();
		}

		private Number numberForParameter(String paramName) {
			return this.delegate.getContextAttribute(paramName, Number.class);
		}

		@Override
		public int getContextAttributeAsInteger(String paramName) {
			return this.numberForParameter(paramName).intValue();
		}

		@Override
		public boolean getContextAttributeAsBoolean(String paramName) {
			return this.getContextAttribute(paramName, Boolean.class);
		}

		@Override
		public float getContextAttributeAsFloat(String paramName) {
			return this.numberForParameter(paramName).floatValue();
		}

		@Override
		public double getContextAttributeAsDouble(String paramName) {
			return this.numberForParameter(paramName).doubleValue();
		}

		@Override
		public String getContextAttributeAsString(String paramName) {
			return getContextAttribute(paramName, String.class);
		}

		@Override
		public <T> T getContextAttributeOrDefault(String paramName, Class<T> type, Supplier<T> defaultValue) {
			var result = this.delegate.getContextAttribute(paramName, type);
			if (null == result) {
				return defaultValue.get();
			}
			return result;
		}

		@Override
		public Long mogulId() {
			return this.getContextAttribute(Job.MOGUL_ID_KEY, Long.class);
		}

	}

}
