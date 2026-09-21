package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.mogul.MogulService;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

/**
 * jobs are persisted, handed to a single node and retried by JobRunr now, so what is left
 * to cover here is the seam: that launching enqueues something JobRunr can round-trip
 * through JSON and hand back to the right {@link Job}, and that the start/stop events the
 * client's notifications ride on still come out.
 */
@SpringBootTest(properties = { "jobrunr.background-job-server.enabled=true",
		// the default poll is 15s, which makes a 30s wait a coin toss once the shared
		// development database has a few jobs in it from earlier runs.
		"jobrunr.background-job-server.poll-interval-in-seconds=5" })
@Import(JobsTest.JobsTestConfiguration.class)
class JobsTest {

	@Autowired
	private Jobs jobs;

	@Autowired
	private MogulService mogulService;

	@Autowired
	private RecordingJob recordingJob;

	@Autowired
	private JobEventListener listener;

	@Test
	void launchingRunsTheJobOnABackgroundWorkerWithItsContext() throws Exception {
		var mogul = this.mogulService.login("jlong", "clientId", "email", "josh", "long");
		this.recordingJob.seen().clear();

		this.jobs.launch(mogul.id(), "recordingJob", Map.of("name", "bob", "count", 42L));

		// enqueue returns immediately; JobRunr runs it on a worker thread, so wait.
		await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(this.recordingJob.seen()).isNotEmpty());

		var context = this.recordingJob.seen().getFirst();
		assertThat(context.get("name")).as("a string survives the json round trip").isEqualTo("bob");
		// json gives back a Number, and the context is expected to convert it
		assertThat(context.get("count")).as("a number is converted to what the job asked for").isEqualTo(42L);
		assertThat(context.get("mogulId")).as("the mogul is always supplied").isEqualTo(mogul.id());
	}

	@Test
	void startingAndStoppingAreBothAnnounced() throws Exception {
		var mogul = this.mogulService.login("jlong", "clientId", "email", "josh", "long");

		// its own job, and the events are tagged with the job name: every test here
		// shares one context and one JobRunr server, so a job another test enqueued can
		// still be finishing while this one asserts.
		this.jobs.launch(mogul.id(), "announcingJob", Map.of());

		await().atMost(Duration.ofSeconds(60))
			.untilAsserted(
					() -> assertThat(this.listener.eventsFor("announcingJob")).containsExactly("started", "stopped"));
	}

	@Test
	void launchingAJobThatDoesNotExistIsRefusedRatherThanEnqueued() {
		assertThatExceptionOfType(JobException.class).isThrownBy(() -> this.jobs.launch(1L, "no-such-job", Map.of()))
			.withMessageContaining("no job named");
	}

	@Test
	void everyKnownJobIsListedByName() {
		assertThat(this.jobs.jobs()).containsKey("recordingJob");
	}

	static class RecordingJob implements Job {

		private final CopyOnWriteArrayList<Map<String, Object>> seen = new CopyOnWriteArrayList<>();

		CopyOnWriteArrayList<Map<String, Object>> seen() {
			return this.seen;
		}

		@Override
		public @NonNull Set<String> requiredContextAttributes() {
			return Set.of(Job.MOGUL_ID_KEY, "name");
		}

		@Override
		public JobExecutionResult run(JobExecutionContext context) {
			// read through the accessors, so the json-to-java conversion is what is
			// actually under test rather than the raw map
			this.seen.add(Map.of("name", context.getContextAttributeAsString("name"), "count",
					context.getContextAttributeOrDefault("count", Long.class, () -> 0L), "mogulId", context.mogulId()));
			return JobExecutionResult.ok();
		}

	}

	static class JobEventListener {

		private final CopyOnWriteArrayList<String> events = new CopyOnWriteArrayList<>();

		List<String> eventsFor(String jobName) {
			return this.events.stream()
				.filter(e -> e.startsWith(jobName + ":"))
				.map(e -> e.substring(jobName.length() + 1))
				.toList();
		}

		@ApplicationModuleListener
		void on(JobStartedEvent event) {
			this.events.add(event.jobName() + ":started");
		}

		@ApplicationModuleListener
		void on(JobStoppedEvent event) {
			this.events.add(event.jobName() + ":stopped");
		}

	}

	/**
	 * launched only by the events test, so its announcements are unambiguous.
	 */
	static class AnnouncingJob implements Job {

		@Override
		public @NonNull Set<String> requiredContextAttributes() {
			return Set.of(Job.MOGUL_ID_KEY);
		}

		@Override
		public JobExecutionResult run(JobExecutionContext context) {
			return JobExecutionResult.ok();
		}

	}

	@TestConfiguration
	static class JobsTestConfiguration {

		@Bean
		RecordingJob recordingJob() {
			return new RecordingJob();
		}

		@Bean
		AnnouncingJob announcingJob() {
			return new AnnouncingJob();
		}

		@Bean
		JobEventListener jobEventListener() {
			return new JobEventListener();
		}

	}

}
