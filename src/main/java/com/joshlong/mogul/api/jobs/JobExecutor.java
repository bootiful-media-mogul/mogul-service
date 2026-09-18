package com.joshlong.mogul.api.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Transactional
class JobExecutor {

	/**
	 * an arbitrary but stable key for the sweep lock. advisory lock keys share a single
	 * namespace across the whole database, so it is spelled out here rather than derived
	 * from a hash of something that might one day collide.
	 */
	static final long INCOMPLETE_EVENTS_SWEEP_LOCK = 6_646_581L;

	private final Logger log = LoggerFactory.getLogger(getClass());

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

	/**
	 * every replica runs this schedule, and every replica reads the same
	 * {@code event_publication} rows -- so without a lock each one resubmits the same
	 * incomplete {@link JobStartedEvent} and the job runs once per node. an advisory lock
	 * elects a single sweeper for each tick; the rest find it taken and wait for the next
	 * minute, by which time the winner has finished.
	 * <p>
	 * the lock is the {@code _xact_} variant deliberately: it is released by the
	 * transaction that took it, so a node that dies mid-sweep cannot strand it, and it
	 * can never outlive its turn on a pooled connection the way a session-level lock can.
	 */
	@Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
	void checkForIncompleteEvents() {
		if (!this.claimSweep()) {
			this.log.debug("another node is sweeping incomplete event publications; skipping this tick");
			return;
		}
		this.eventPublications.resubmitIncompletePublications( //
				e -> e.getApplicationEvent() instanceof JobStartedEvent);
	}

	/**
	 * tries to become the one node that sweeps this tick. the lock is the {@code _xact_}
	 * variant deliberately: it is released by the transaction that took it, so a node
	 * that dies mid-sweep cannot strand it and it can never outlive its turn on a pooled
	 * connection the way a session-level lock can.
	 */
	boolean claimSweep() {
		// a transaction-scoped lock taken outside a transaction is released immediately
		// and guards nothing at all -- silently. fail loudly instead if the surrounding
		// @Transactional ever stops applying.
		Assert.state(TransactionSynchronizationManager.isActualTransactionActive(),
				"the sweep must run inside a transaction, or its advisory lock protects nothing");
		return this.db //
			.sql("select pg_try_advisory_xact_lock(?)") //
			.param(INCOMPLETE_EVENTS_SWEEP_LOCK) //
			.query(Boolean.class) //
			.single();
	}

	@ApplicationModuleListener
	void onJobStartedEvent(JobStartedEvent job) {
		var jobExecution = this.jobs.getJobExecution(job.jobExecutionId());
		var context = new JobExecutionWrappingJobExecutionContext(jobExecution);
		try {
			var jobName = jobExecution.jobName();
			var jobsInstance = this.jobs.jobs().get(jobName);
			Assert.state(jobsInstance != null, () -> "there is no job named [" + jobName + "] to run!");
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
