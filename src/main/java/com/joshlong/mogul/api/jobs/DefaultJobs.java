package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * maybe if i designed the tables i could have a way to prepopulate a table for each of
 * the required parameters? eg, for every user + job name, there'd be rows in a
 * job_parameters table with, e.g.: name == 'managedFileId' and value = null | number.
 * this way, the ManagedFile machinery could point to this int and everything would work.
 * the second somebody launches the job, those pre-populated instances get created anew.
 * after all, each user only gets one job run at a time.
 * <p>
 * so: anytime a user does anything with any job, wed realuate a loop that would iterate
 * through all the jobs, and ensure that there's a pristine job in the jobs table, and
 * pristine rows for each of the job_params. there'd be custom logic for how to initialie
 * those job_params. for the case of managedFileId, we'd create one and store the value
 * there.
 */
// todo we have the metadata to articulate a given jobs makeup
// no we need to build the runtime infrastructure to launch a job and preservee all of its
// state in the db. and of course
// to do this well need to have a pre-allocated instance of each ojb for each user. as we
// pre-allocate, we'll involve some
// customization logic by allowing some registered spring beans to handle the defaults for
// the parameters. namely, the ManagedFiles.
// well listen for the user authenticated signal to run this pre-allocation logic the
// first time. then we'll re-run the logic every singlew time a
// user launches a job of any type (this way well ensure that the job is again ready to go
// for a new job run)
// todo make this a spring components

// todo refactor this to use the ResultSetExtractor for things like the params
// or anything that produces more than one result
@Transactional
class DefaultJobs implements InitializingBean, Jobs {

	private final ApplicationEventPublisher publisher;

	private final JdbcClient db;

	private final JobsRowMapper jobsRowMapper;

	private final JobParamRowMapper jobParamRowMapper;

	private final JobExecutionRowMapper jobExecutionRowMapper;

	private final JobExecutionParamRowMapper jobExecutionParamRowMapper;

	private final Map<String, com.joshlong.mogul.api.jobs.Job> jobs;

	private final Collection<DefaultJobExecutionParamProvider> jobParamPreparers;

	private final Logger log = LoggerFactory.getLogger(getClass());

	DefaultJobs(Map<String, com.joshlong.mogul.api.jobs.Job> jobs, JdbcClient db, ApplicationEventPublisher publisher,
			Collection<DefaultJobExecutionParamProvider> jobParamPreparers) {
		this.db = db;
		this.jobs = jobs;
		this.publisher = publisher;
		this.jobParamPreparers = jobParamPreparers;
		this.jobParamRowMapper = new JobParamRowMapper();
		this.jobsRowMapper = new JobsRowMapper(this::getJobParamCollection);
		this.jobExecutionRowMapper = new JobExecutionRowMapper(this::getJobExecutionParams);
		this.jobExecutionParamRowMapper = new JobExecutionParamRowMapper();
	}

	@Override
	@Transactional
	public void afterPropertiesSet() {
		for (var jobName : this.jobs.keySet()) {
			this.createJob(jobName);
		}
	}

	@Override
	public Map<String, com.joshlong.mogul.api.jobs.Job> jobs() {
		var map = new HashMap<String, com.joshlong.mogul.api.jobs.Job>();
		var list = this.db //
			.sql(" select * from job ") //
			.query(this.jobsRowMapper) //
			.list();
		for (var jobDefinitions : list) {
			map.put(jobDefinitions.jobName(), this.jobs.get(jobDefinitions.jobName()));
		}
		return map;
	}

	@Override
	public JobExecution getJobExecution(Long id) {
		return this.db //
			.sql(" select * from job_execution where id = ? ") //
			.params(id) //
			.query(this.jobExecutionRowMapper) //
			.single();
	}

	@Override
	@Transactional
	public void launchJobExecution(Long mogulId, Long jobExecutionId, Map<String, Supplier<Object>> context) {
		this.writeContextAttributesForJobExecution(jobExecutionId, context);
		this.db.sql(" update job_execution set start = NOW() where id = ? ").params(jobExecutionId).update();
		this.publisher.publishEvent(new JobLaunchedEvent(jobExecutionId));

	}

	/**
	 * there should never be more than one instance of a prepared job type for a given
	 * mogul. so we'll always find the existing instance or create a new one
	 */
	@Override
	@Transactional
	public JobExecution prepareJobExecution(Long mogulId, String jobName, Map<String, Supplier<Object>> context)
			throws Exception {

		if (this.findJobExecution(mogulId, jobName) == null) {
			var gkh = new GeneratedKeyHolder();
			this.db.sql("""
					            insert into job_execution (mogul_id, job_name)
					            values (?,?)
					            on conflict (mogul_id, job_name ) do nothing
					            returning  id
					""") //
				.params(mogulId, jobName)//
				.update(gkh);
		} //

		var jobExecution = this.findJobExecution(mogulId, jobName);
		Assert.notNull(jobExecution, "jobExecution is null");
		this.writeContextAttributesForJobExecution(jobExecution.id(), context);
		jobExecution = this.findJobExecution(mogulId, jobName);
		Assert.notNull(jobExecution, "jobExecution is null");
		var aggregate = new HashMap<String, Supplier<Object>>();
		for (var prep : this.jobParamPreparers) {
			var job = this.jobs.get(jobExecution.jobName());
			if (prep.supports(job)) {
				var mapOfAttrsToContribute = prep.prepare(jobExecution);
				for (var k : mapOfAttrsToContribute.keySet()) {
					if (!jobExecution.context().containsKey(k)) {
						aggregate.put(k, mapOfAttrsToContribute.get(k));
					}
				}
			}
		} //
		this.writeContextAttributesForJobExecution(jobExecution.id(), aggregate);
		return this.findJobExecution(mogulId, jobExecution.jobName());
	}

	private void writeContextAttributesForJobExecution(Long jobExecutionId, Map<String, Supplier<Object>> context) {
		if (context != null) {
			for (var entry : context.entrySet()) {
				var paramName = entry.getKey();
				var value = null == entry.getValue() ? null : entry.getValue().get();
				var write = null == value ? null : JsonUtils.write(value);
				this.createJobExecutionParameter(jobExecutionId, paramName, write, value.getClass());
			}
		}
	}

	private JobExecution findJobExecution(Long mogulId, String jobName) {
		var list = this.db//
			.sql("select * from job_execution where job_name = ? and mogul_id = ?") //
			.params(jobName, mogulId) //
			.query(this.jobExecutionRowMapper) //
			.list();
		if (!list.isEmpty())
			return list.getFirst();
		return null;
	}

	private Map<String, JobExecutionParam> getJobExecutionParams(Long jobExecutionId) {
		var params = this.db //
			.sql("select  * from job_execution_param jep where jep.job_execution_id =  ? ") //
			.params(jobExecutionId) //
			.query(this.jobExecutionParamRowMapper) //
			.list();
		var map = new HashMap<String, JobExecutionParam>();
		for (var p : params) {
			map.put(p.name(), p);
		}
		return map;
	}

	private void createJobExecutionParameter(Long jobExecutionId, String paramName, String value, Class<?> clzz) {
		if (value == null) {
			this.db.sql("""
					insert into job_execution_param (job_execution_id, param_name , param_class ) values (?,?,?)
					on conflict (job_execution_id, param_name) do update set param_value = null, param_class = null
					""") //
				.params(jobExecutionId, paramName) //
				.update();
		} //
		else {
			this.db //
				.sql("""
						    insert into job_execution_param (job_execution_id, param_name, param_value, param_class ) values (?,?,?, ?)
						    on conflict (job_execution_id, param_name) do update  set param_value = excluded.param_value , param_class = excluded.param_class
						""") //
				.params(jobExecutionId, paramName, value, clzz.getName()) //
				.update();
		}
		this.log.debug("preparing execution parameter {} = {}", paramName, "" + value);
	}

	private void createJob(String jobName) {
		var job = this.jobs.get(jobName);
		Assert.notNull(job, "the job named [" + jobName + "] does not exist!");
		var sql = """
				INSERT INTO job (job_name)
				VALUES (?)
				ON CONFLICT (job_name)
				DO NOTHING
				""";
		this.db.sql(sql).params(jobName).update();
		var requiredContextAttributes = job.requiredContextAttributes();
		var jobDefinition = this.findJob(jobName);
		Assert.notNull(jobDefinition, "the job named [" + jobName + "] does not exist!");
		this.db.sql("delete from job_param where job_id = ?").params(jobDefinition.id()).update();
		var existingJobParamsInDb = this.getJobParamCollection(jobDefinition.id());
		this.log.debug("got {} job params from the DB.", existingJobParamsInDb.size());
		for (var existingJobParamInDb : existingJobParamsInDb) {
			if (!requiredContextAttributes.contains(existingJobParamInDb.paramName())) {
				this.log.debug("deleting job param {} from the DB", existingJobParamInDb.paramName());
				this.db.sql("delete from job_param where id = ?").params(existingJobParamInDb.id()).update();
			}
		}
		this.log.debug("there are {} required context attributes for job {}.", requiredContextAttributes.size(),
				jobName);
		for (var parameterName : requiredContextAttributes) {
			this.createJobParameter(jobDefinition.id(), parameterName);
		}
	}

	private void createJobParameter(long jobId, String parameterName) {
		this.db.sql("""
				INSERT INTO JOB_PARAM (JOB_ID, PARAM_NAME) VALUES (?, ?)
				 ON CONFLICT (job_id,param_name) DO NOTHING
				""")//
			.params(jobId, parameterName)//
			.update();
		this.log.info("created job param {} for job {}", parameterName, jobId);
	}

	private Job findJob(String jobName) {
		return this.db //
			.sql("select * from job where job_name =  ?") //
			.params(jobName) //
			.query(this.jobsRowMapper) //
			.single();
	}

	private Collection<JobParam> getJobParamCollection(long jobId) {
		return this.db.sql("select * from job_param where job_id = ?")
			.params(jobId)
			.query(this.jobParamRowMapper)
			.list();
	}

	record Job(Collection<JobParam> parameters, String jobName, long id) {
	}

	record JobParam(Long id, String paramName) {
	}

	private static class JobExecutionParamRowMapper implements RowMapper<JobExecutionParam> {

		@Override
		public JobExecutionParam mapRow(ResultSet rs, int rowNum) throws SQLException {
			var paramValue = rs.getString("param_value");
			var paramClass = rs.getString("param_class");
			var hv = hasValue(paramClass, paramValue);
			var clazz = (Class<?>) (hv ? ReflectionUtils.classForName(paramClass) : null);
			var obj = hv ? JsonUtils.read(paramValue, clazz) : null;
			return new JobExecutionParam(rs.getLong("job_execution_id"), rs.getString("param_name"), obj, clazz);
		}

		private boolean hasValue(String type, String value) {
			return StringUtils.hasText(value) && StringUtils.hasText(type);
		}

	}

	private static class JobParamRowMapper implements RowMapper<JobParam> {

		@Override
		public JobParam mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new JobParam(rs.getLong("id"), rs.getString("param_name"));
		}

	}

	private static class JobExecutionRowMapper implements RowMapper<JobExecution> {

		private final Function<Long, Map<String, JobExecutionParam>> function;

		JobExecutionRowMapper(Function<Long, Map<String, JobExecutionParam>> function) {
			this.function = function;
		}

		@Override
		public JobExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
			var paramsMap = this.function.apply(rs.getLong("id"));
			return new JobExecution(rs.getLong("id"), rs.getLong("mogul_id"), rs.getString("job_name"), paramsMap);
		}

	}

	private static class JobsRowMapper implements RowMapper<Job> {

		private final Function<Long, Collection<JobParam>> function;

		JobsRowMapper(Function<Long, Collection<JobParam>> function) {
			this.function = function;
		}

		@Override
		public Job mapRow(ResultSet rs, int rowNum) throws SQLException {
			var jobId = rs.getLong("id");
			return new Job(this.function.apply(jobId), rs.getString("job_name"), jobId);
		}

	}

}

@Component
@Transactional
class JobsProcessor {

	private final Jobs jobs;

	private final IncompleteEventPublications eventPublications;

	private final JdbcClient db;

	private final ApplicationEventPublisher applicationEventPublisher;

	JobsProcessor(Jobs jobs, IncompleteEventPublications eventPublications, JdbcClient jdbcClient,
			ApplicationEventPublisher applicationEventPublisher) {
		this.jobs = jobs;
		this.eventPublications = eventPublications;
		this.db = jdbcClient;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
	void checkForIncompleteEvents() {
		this.eventPublications.resubmitIncompletePublications(e -> e.getApplicationEvent() instanceof JobLaunchedEvent);
	}

	@ApplicationModuleListener
	void onJobLaunchedEvent(JobLaunchedEvent job) {
		var jobExecution = this.jobs.getJobExecution(job.jobExecutionId());
		var context = new JobExecutionWrappingJobExecutionContext(jobExecution);
		try {
			var jobName = jobExecution.jobName();
			Assert.state(this.jobs.jobs().containsKey(jobName), "the job doesn't exist in the jobs map!");
			var jobsInstance = this.jobs.jobs().get(jobName);
			var result = jobsInstance.run(context);
			this.recordJobExecutionResult(job, result);
		} //
		catch (Exception e) {
			var ex = JobExecutionResult.error(e);
			this.recordJobExecutionResult(job, ex);
		}
	}

	private void recordJobExecutionResult(JobLaunchedEvent jobLaunchedEvent, JobExecutionResult executionResult) {
		this.db //
			.sql("update job_execution set stop = ? , success = ? where job_execution_id = ?") //
			.params(new Date(), executionResult.success(), jobLaunchedEvent.jobExecutionId()) //
			.update();
		applicationEventPublisher.publishEvent(new JobCompletedEvent(jobLaunchedEvent.jobExecutionId()));
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
		public <T> T getContextAttributeOrDefault(String paramName, Class<T> type, Supplier<T> defaultValue) {
			var result = this.delegate.getContextAttribute(paramName, type);
			if (null == result) {
				return defaultValue.get();
			}
			return result;
		}

		@Override
		public Long getMogulId() {
			return this.getContextAttribute(Job.MOGUL_ID_KEY, Long.class);
		}

	}

}
