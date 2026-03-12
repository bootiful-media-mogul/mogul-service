package com.joshlong.mogul.api.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

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

// todo make this a spring component
@Transactional
class DefaultJobs implements InitializingBean, Jobs {

	private final JdbcClient db;

	private final JobsRowMapper rowMapper;

	private final JobParamRowMapper paramRowMapper;

	private final Map<String, Job> jobs;

	private final Logger log = LoggerFactory.getLogger(getClass());

	DefaultJobs(Map<String, Job> jobs, JdbcClient db) {
		this.db = db;
		this.jobs = jobs;

		this.paramRowMapper = new JobParamRowMapper();
		this.rowMapper = new JobsRowMapper(this::jobParamDefinitionCollection);

	}

	@Override
	public Map<String, Job> jobs() {
		var map = new HashMap<String, Job>();
		for (var jobDefinitions : this.db //
			.sql("select * from job ") //
			.query(this.rowMapper) //
			.list())
			map.put(jobDefinitions.jobName(), this.jobs.get(jobDefinitions.jobName()));
		return map;
	}

	@Override
	public CompletableFuture<Job.Result> launch(String jobName, Map<String, Object> context) throws JobLaunchException {
		// todo
		return null;
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
		var jobDefinition = this.findJobDefinition(jobName);
		Assert.notNull(jobDefinition, "the job named [" + jobName + "] does not exist!");
		this.db.sql("delete from job_param where job_id = ?").params(jobDefinition.id()).update();
		var existingJobParamsInDb = jobParamDefinitionCollection(jobDefinition.id());
		this.log.debug("got {} job params from the DB.", existingJobParamsInDb.size());
		for (var existingJobParamInDb : existingJobParamsInDb) {
			if (!requiredContextAttributes.contains(existingJobParamInDb.paramName())) {
				this.log.debug("deleting job param {} from the DB", existingJobParamInDb.paramName());
				this.db.sql("delete from job_param where id = ?").params(existingJobParamInDb.id()).update();
			}
		}
		this.log.debug("there are {} required context attributes for job {}.", requiredContextAttributes.size(),
				jobName);
		for (var parameterName : requiredContextAttributes)
			this.createJobParameter(jobDefinition.id(), parameterName);
	}

	private void createJobParameter(long jobId, String parameterName) {
		this.db.sql("""
				INSERT INTO JOB_PARAM (JOB_ID, PARAM_NAME) VALUES (?, ?) ON CONFLICT (job_id,param_name) DO NOTHING
				""").params(jobId, parameterName).update();
		this.log.info("created job param {} for job {}", parameterName, jobId);
	}

	private JobDefinition findJobDefinition(String jobName) {
		return this.db.sql("select * from job where job_name =  ?").params(jobName).query(this.rowMapper).single();
	}

	@Override
	@Transactional
	public void afterPropertiesSet() throws Exception {
		for (var jobName : jobs.keySet())
			this.createJob(jobName);

	}

	private Collection<JobParamDefinition> jobParamDefinitionCollection(long jobId) {
		var params = this.db.sql("select * from job_param where job_id = ?")
			.params(jobId)
			.query(this.paramRowMapper)
			.list();

		return params;
	}

	private static class JobParamRowMapper implements RowMapper<JobParamDefinition> {

		@Override
		public JobParamDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new JobParamDefinition(rs.getLong("id"), rs.getString("param_name"));
		}

	}

	private static class JobsRowMapper implements RowMapper<JobDefinition> {

		private final Function<Long, Collection<JobParamDefinition>> function;

		private JobsRowMapper(Function<Long, Collection<JobParamDefinition>> function) {
			this.function = function;
		}

		@Override
		public JobDefinition mapRow(ResultSet rs, int rowNum) throws SQLException {
			var jobId = rs.getLong("id");
			return new JobDefinition(this.function.apply(jobId), rs.getString("job_name"), jobId);
		}

	}

	record JobDefinition(Collection<JobParamDefinition> parameters, String jobName, long id) {
	}

	record JobParamDefinition(Long id, String paramName) {
	}

}
