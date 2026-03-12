package com.joshlong.mogul.api.jobs;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Configuration
class DefaultJobsConfiguration {

	@Bean
	DefaultJobs defaultJobs(Map<String, Job> jobMap, JdbcClient db) {
		return new DefaultJobs(jobMap, db);
	}

}

@SpringBootTest
@Import(DefaultJobsConfiguration.class)
class DefaultJobsTest {

	private final Jobs jobs;

	private final Map<String, Job> jobMap;

	private final JdbcClient db;

	DefaultJobsTest(@Autowired Map<String, Job> jobMap, @Autowired Jobs jobs, @Autowired JdbcClient db) {
		this.jobs = jobs;
		this.jobMap = jobMap;
		this.db = db;
	}

	@Test
	void jobs() {
		var dbJobsMap = this.jobs.jobs();
		assertEquals(this.jobMap.size(), dbJobsMap.size(),
				"should not be empty if there are " + Job.class.getName() + " instances in the BeanFactory.");
		for (var e : dbJobsMap.entrySet()) {
			var job = e.getValue();
			var jobName = e.getKey();
			var requiredContextAttributes = job.requiredContextAttributes();
			var jobIdForJobName = db ///
				.sql("select id from job where job_name = ?") ///
				.params(jobName) //
				.query((rs, _) -> rs.getLong("id")) //
				.single();
			var dbCount = this.db //
				.sql(" select count(jp.id) as total from job_param jp where jp.job_id = ? ")
				.params(jobIdForJobName)
				.query((rs, _) -> rs.getInt("total"))
				.single()
				.intValue();
			var size = requiredContextAttributes.size();
			assertEquals(size, dbCount,
					"the params [" + String.join(",", requiredContextAttributes) + "] is not the same as ["
							+ String.join(",", job.requiredContextAttributes()) + "] for job ID " + jobIdForJobName
							+ '/' + jobName + " in the DB");

		}

	}

}