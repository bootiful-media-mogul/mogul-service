package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.mogul.MogulService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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

	private final MogulService mogulService;

	private final AtomicReference<String> helloWorldJob = new AtomicReference<>();

	DefaultJobsTest(@Autowired Map<String, Job> jobMap, @Autowired Jobs jobs, @Autowired JdbcClient db,
			@Autowired MogulService mogulService) {
		this.jobs = jobs;
		this.jobMap = jobMap;
		this.db = db;
		this.mogulService = mogulService;
		for (var v : this.jobMap.entrySet()) {
			if (v.getValue() instanceof HelloWorldJob hw) {
				this.helloWorldJob.set(v.getKey());
			}
		}
		Assertions.assertNotNull(this.helloWorldJob.get(), "the hello world job should not be null");
	}

	@Test
	void jobExecutions() throws Exception {
		var mogul = this.mogulService.login("jlong", "clientId", "email", "josh", "long");
		Assertions.assertNotNull(mogul, "the mogul should not be null");
		IO.println("the mogul is " + mogul.id() + ".");
		var jobName = this.helloWorldJob.get();
		Assertions.assertNotNull(jobName, "the job name should not be null");
		var context = Map.<String, Supplier<Object>>of("name", () -> "bob");
		var jobExecution = this.jobs.prepareJobExecution(mogul.id(), jobName, context);
		var firstId = jobExecution.id();
		var secondJobExecution = this.jobs.prepareJobExecution(mogul.id(), jobName, context);
		Assertions.assertEquals(firstId, secondJobExecution.id(), "the ids should be the same");
		Assertions.assertNotNull(jobExecution, "the name should not be null");
		Assertions.assertEquals(jobName, jobExecution.jobName(), "the job names should be the same");
		Assertions.assertEquals("bob", jobExecution.getContextAttribute("name", String.class));

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
			var jobIdForJobName = db //
				.sql("select id from job where job_name = ?") //
				.params(jobName) //
				.query((rs, _) -> rs.getLong("id")) //
				.single();
			var dbCount = (int) this.db //
				.sql(" select count(jp.id) as total from job_param jp where jp.job_id = ? ")
				.params(jobIdForJobName)
				.query((rs, _) -> rs.getInt("total"))
				.single();
			var size = requiredContextAttributes.size();
			assertEquals(size, dbCount,
					"the params [" + String.join(",", requiredContextAttributes) + "] is not the same as ["
							+ String.join(",", job.requiredContextAttributes()) + "] for job ID " + jobIdForJobName
							+ '/' + jobName + " in the DB");

		}

	}

}