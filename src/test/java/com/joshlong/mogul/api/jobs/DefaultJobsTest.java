package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.mogul.MogulService;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestHelloWorldJob implements Job {

	@Override
	public @NonNull Set<String> requiredContextAttributes() {
		return Set.of("managedFileId", "name", "blogId");
	}

	@Override
	public JobExecutionResult run(JobExecutionContext context) throws Exception {
		Assertions.assertNotNull(context, "context cannot be null");
		Assertions.assertTrue(context.mogulId() > 0, "context must contain mogul id");
		return JobExecutionResult.ok(Map.of("uploaded", new Date()));
	}

}

class TestHelloWorldJobJobExecutionParamProvider implements JobExecutionParamProvider {

	@Override
	public boolean supports(Job job) {
		return job instanceof TestHelloWorldJob;
	}

	@Override
	public Map<String, Supplier<Object>> prepare(JobExecution jobExecution) {
		return Map.of("managedFileId", () -> 1L);
	}

}

@Configuration
class DefaultJobsConfiguration {

	@Bean
	TestHelloWorldJobJobExecutionParamProvider testHelloWorldJobDefaultJobExecutionParamProvider() {
		return new TestHelloWorldJobJobExecutionParamProvider();
	}

	@Bean
	TestHelloWorldJob testHelloWorldJob() {
		return new TestHelloWorldJob();
	}

	@Bean
	Listener listener() {
		return new Listener();
	}

}

class Listener {

	private final AtomicInteger events = new AtomicInteger(0);

	@EventListener
	void on(JobStartedEvent jobStartedEvent) {
		events.incrementAndGet();
	}

	@EventListener
	void on(JobStoppedEvent jobStoppedEvent) {
		events.incrementAndGet();
	}

	int count() {
		return events.get();
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

	private final Listener listener;

	DefaultJobsTest(@Autowired Listener listener, @Autowired Map<String, Job> jobMap, @Autowired Jobs jobs,
			@Autowired JdbcClient db, @Autowired MogulService mogulService) {
		this.jobs = jobs;
		this.jobMap = jobMap;
		this.db = db;
		this.mogulService = mogulService;
		for (var v : this.jobMap.entrySet()) {
			if (v.getValue() instanceof TestHelloWorldJob hw) {
				this.helloWorldJob.set(v.getKey());
			}
		}
		Assertions.assertNotNull(this.helloWorldJob.get(), "the test hello world job should not be null");
		this.listener = listener;
	}

	@Test
	void jobExecutions() throws Exception {
		var mogul = this.mogulService.login("jlong", "clientId", "email", "josh", "long");
		Assertions.assertNotNull(mogul, "the mogul should not be null");
		IO.println("the mogul is " + mogul.id() + ".");
		var jobName = this.helloWorldJob.get();
		Assertions.assertNotNull(jobName, "the job name should not be null");
		var context = Map.<String, Supplier<Object>>of("managedFileId", () -> 1L, "name", () -> "bob");
		var jobExecution = this.jobs.prepare(mogul.id(), jobName, context);
		var firstId = jobExecution.id();
		var secondJobExecution = this.jobs.prepare(mogul.id(), jobName, context);
		Assertions.assertEquals(firstId, secondJobExecution.id(), "the ids should be the same");
		Assertions.assertNotNull(jobExecution, "the name should not be null");
		Assertions.assertEquals(jobName, jobExecution.jobName(), "the job names should be the same");
		Assertions.assertEquals("bob", jobExecution.getContextAttribute("name", String.class));
		Assertions.assertEquals(1L, jobExecution.getContextAttribute("managedFileId", Long.class));
		var msg = new StringBuilder().append(System.lineSeparator());
		jobExecution.context().forEach((key, value) -> {
			msg.append('\t').append(key).append(": ").append(value).append(System.lineSeparator());
			if (value.type().isAssignableFrom(Long.class)) {
				var id = (Long) value.value();
				Assertions.assertNotNull(id, "the id should not be null");
			}
		});
		IO.println("the job is " + jobExecution.id() + "." + msg);

		this.jobs.launch(mogul.id(), jobExecution.id(), Map.of("message", () -> "hello world!"));

		Thread.sleep(Duration.ofSeconds(10));

		Assertions.assertEquals(2, this.listener.count(), "there should be exactly 2 events (start and stop)");
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