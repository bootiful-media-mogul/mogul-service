package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.utils.CollectionUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

@Transactional
class DefaultJobs implements InitializingBean, Jobs {

	private final ApplicationEventPublisher publisher;

	private final JdbcClient db;

	private final JobsResultSetExtractor jobsResultSetExtractor;

	private final JobParamRowMapper jobParamRowMapper;

	private final JobExecutionResultSetExtractor jobExecutionResultSetExtractor;

	private final JobExecutionParamRowMapper jobExecutionParamRowMapper;

	private final Map<String, com.joshlong.mogul.api.jobs.Job> jobs;

	private final Collection<JobExecutionParamProvider> jobParamPreparers;

	private final Logger log = LoggerFactory.getLogger(getClass());

	DefaultJobs(Map<String, com.joshlong.mogul.api.jobs.Job> jobs, JdbcClient db, ApplicationEventPublisher publisher,
			Collection<JobExecutionParamProvider> jobParamPreparers) {
		this.db = db;
		this.jobs = jobs;
		this.publisher = publisher;
		this.jobParamPreparers = jobParamPreparers;
		this.jobParamRowMapper = new JobParamRowMapper();
		this.jobExecutionParamRowMapper = new JobExecutionParamRowMapper();
		this.jobsResultSetExtractor = new JobsResultSetExtractor(this::getJobParamsByJobIds);
		this.jobExecutionResultSetExtractor = new JobExecutionResultSetExtractor(this::getJobExecutionParamsByIds);
	}

	@Override
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
			.query(this.jobsResultSetExtractor);
		for (var jobDefinition : list) {
			var job = this.jobs.get(jobDefinition.jobName());
			if (job == null) {
				this.log.warn("there is a row in `job` named [{}] with no Job of that name here; ignoring it",
						jobDefinition.jobName());
				continue;
			}
			map.put(jobDefinition.jobName(), job);
		}
		return map;
	}

	@Override
	public JobExecution getJobExecution(Long id) {
		return CollectionUtils.firstOrNull(this.db //
			.sql(" select * from job_execution where id = ? ") //
			.params(id) //
			.query(this.jobExecutionResultSetExtractor));
	}

	@Override
	public JobExecution prepare(Long mogulId, String jobName, Map<String, Supplier<Object>> context) {

		if (this.findUnusedJobExecutionForMogul(mogulId, jobName) == null) {
			var gkh = new GeneratedKeyHolder();
			this.db.sql("""
					    insert into job_execution(mogul_id, job_name)
					    values(?,?)
					""") //
				.params(mogulId, jobName)//
				.update(gkh);
		} //

		var jobExecution = this.findUnusedJobExecutionForMogul(mogulId, jobName);
		var jobExecutionId = Objects.requireNonNull(jobExecution).id();
		Assert.notNull(jobExecution, "jobExecution is null");
		this.writeContextAttributesForJobExecution(jobExecutionId, context);
		jobExecution = this.findUnusedJobExecutionForMogul(mogulId, jobName);
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
		return this.getJobExecution(jobExecutionId);
	}

	@Override
	public void launch(Long mogulId, Long jobExecutionId, Map<String, Supplier<Object>> context) {

		this.writeContextAttributesForJobExecution(jobExecutionId, context);

		this.db.sql(" update job_execution set start = NOW() where id = ? ").params(jobExecutionId).update();

		this.publisher.publishEvent(new JobStartedEvent(jobExecutionId));
	}

	void writeContextAttributesForJobExecution(Long jobExecutionId, Map<String, Supplier<Object>> context) {
		if (context != null) {
			for (var entry : context.entrySet()) {
				var paramName = entry.getKey();
				var value = null == entry.getValue() ? null : entry.getValue().get();
				var write = null == value ? null : JsonUtils.write(value);
				this.createJobExecutionParameter(jobExecutionId, paramName, write, value.getClass());
			}
		}
	}

	private JobExecution findUnusedJobExecutionForMogul(Long mogulId, String jobName) {
		var list = this.db//
			.sql("""
					        select * from job_execution where job_name = ? and mogul_id = ?
					            and "start" is null
					            and "stop" is null
					""") //
			.params(jobName, mogulId) //
			.query(this.jobExecutionResultSetExtractor);
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
			this.log.debug("preparing execution parameter {} = {}", paramName, value);
		}
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
		var existingJobParamsInDb = this.getJobParamsByJobIds(List.of(jobDefinition.id()))
			.getOrDefault(jobDefinition.id(), List.of());
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
		return CollectionUtils.firstOrNull(this.db //
			.sql("select * from job where job_name =  ?") //
			.params(jobName) //
			.query(this.jobsResultSetExtractor));
	}

	/**
	 * every job's parameters in one query, grouped by job, however many jobs were asked
	 * for.
	 */
	private Map<Long, Collection<JobParam>> getJobParamsByJobIds(Collection<Long> jobIds) {
		var results = new HashMap<Long, Collection<JobParam>>();
		if (jobIds.isEmpty())
			return results;
		this.db.sql("select * from job_param where job_id = any(?)")
			.params(new SqlArrayValue("bigint", jobIds.toArray()))
			.query((rs, rowNum) -> Map.entry(rs.getLong("job_id"),
					Objects.requireNonNull(this.jobParamRowMapper.mapRow(rs, rowNum))))
			.list()
			.forEach(entry -> results.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>()).add(entry.getValue()));
		return results;
	}

	/**
	 * every execution's parameters in one query, grouped by execution and then by name.
	 */
	private Map<Long, Map<String, JobExecutionParam>> getJobExecutionParamsByIds(Collection<Long> jobExecutionIds) {
		var results = new HashMap<Long, Map<String, JobExecutionParam>>();
		if (jobExecutionIds.isEmpty())
			return results;
		// read job_execution_id off the row rather than off the mapped object: the
		// record's
		// first component is called `id` but the mapper fills it with the execution's id,
		// not the param's own, and that is too easy a thing to group by wrongly.
		this.db.sql("select * from job_execution_param jep where jep.job_execution_id = any(?)")
			.params(new SqlArrayValue("bigint", jobExecutionIds.toArray()))
			.query((rs, rowNum) -> Map.entry(rs.getLong("job_execution_id"),
					Objects.requireNonNull(this.jobExecutionParamRowMapper.mapRow(rs, rowNum))))
			.list()
			.forEach(entry -> results.computeIfAbsent(entry.getKey(), _ -> new HashMap<>())
				.put(entry.getValue().name(), entry.getValue()));
		return results;
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
			return new JobExecutionParam(rs.getLong("job_execution_id"), rs.getString("param_name"), obj, paramValue,
					clazz);
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

	/**
	 * reads the executions first and then their parameters in one call, rather than a
	 * query per row.
	 */
	private record JobExecutionResultSetExtractor(
			Function<Collection<Long>, Map<Long, Map<String, JobExecutionParam>>> params)
			implements
				ResultSetExtractor<List<JobExecution>> {

		@Override
		public List<JobExecution> extractData(ResultSet rs) throws SQLException, DataAccessException {
			var rows = new ArrayList<JobExecution>();
			var ids = new LinkedHashSet<Long>();
			while (rs.next()) {
				var id = rs.getLong("id");
				ids.add(id);
				rows.add(new JobExecution(id, rs.getLong("mogul_id"), rs.getString("job_name"),
						rs.getBoolean("success"), new HashMap<>()));
			}
			var paramsById = this.params.apply(ids);
			return rows.stream()
				.map(row -> new JobExecution(row.id(), row.mogulId(), row.jobName(), row.success(),
						paramsById.getOrDefault(row.id(), Map.of())))
				.toList();
		}

	}

	/**
	 * reads the jobs first and then their parameters in one call, rather than a query per
	 * row.
	 */
	private record JobsResultSetExtractor(Function<Collection<Long>, Map<Long, Collection<JobParam>>> params)
			implements
				ResultSetExtractor<List<Job>> {

		@Override
		public List<Job> extractData(ResultSet rs) throws SQLException, DataAccessException {
			var names = new LinkedHashMap<Long, String>();
			while (rs.next())
				names.put(rs.getLong("id"), rs.getString("job_name"));
			var paramsByJobId = this.params.apply(names.keySet());
			return names.entrySet()
				.stream()
				.map(entry -> new Job(paramsByJobId.getOrDefault(entry.getKey(), List.of()), entry.getValue(),
						entry.getKey()))
				.toList();
		}

	}

}
