package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.utils.JsonUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Controller;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Controller
class JobsController {

	private final Jobs jobs;

	private final MogulService mogulService;

	private final ApplicationEventPublisher applicationEventPublisher;

	private final ManagedFileService managedFileService;

	JobsController(Jobs jobs, MogulService mogulService, ManagedFileService managedFileService,
			ApplicationEventPublisher applicationEventPublisher) {
		this.jobs = jobs;
		this.mogulService = mogulService;
		this.managedFileService = managedFileService;
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@MutationMapping
	boolean launchJob(@Argument String jobName, @Argument String contextAsJson) throws JobException {
		// @formatter:off
		var typeReference = new ParameterizedTypeReference<Map<String,Object>>() {};
		// @formatter:on
		if (JsonUtils.read(contextAsJson, typeReference) instanceof Map<String, Object> context) {
			var mogulId = this.mogulService.getCurrentMogul().id();
			this.jobs.launch(mogulId, jobName, context);
		}
		return true;
	}

	private void emit(Object event, Long mogulId, String jobName, boolean success) {
		this.applicationEventPublisher.publishEvent(NotificationEvent.visibleNotificationEventFor(mogulId, event,
				jobName, JsonUtils.write(Map.of("success", success))));
	}

	@ApplicationModuleListener
	void on(JobStartedEvent startedEvent) {
		// a job that has only started has not succeeded yet, and saying so would be a
		// lie the client renders.
		this.emit(startedEvent, startedEvent.mogulId(), startedEvent.jobName(), false);
	}

	@ApplicationModuleListener
	void on(JobStoppedEvent stoppedEvent) {
		this.emit(stoppedEvent, stoppedEvent.mogulId(), stoppedEvent.jobName(), stoppedEvent.success());
	}

	/**
	 * jobs that take a {@link Job#MANAGED_FILE_ID_KEY} need somewhere to put the file
	 * <em>before</em> the job runs, and the client needs its id to upload into. the draft
	 * job_execution used to manufacture that as a side effect of being read; now the
	 * client asks for it outright, which is the same thing said plainly.
	 */
	@MutationMapping
	ManagedFile createJobManagedFile(@Argument String jobName) throws JobException {
		var mogulId = this.mogulService.getCurrentMogul().id();
		if (!this.jobs.jobs().containsKey(jobName))
			throw new JobException("there is no job named [" + jobName + "]");
		return this.managedFileService.createManagedFile(mogulId, jobName + "/" + UUID.randomUUID(), "archive.zip", 0,
				CommonMediaTypes.BINARY, false);
	}

	@QueryMapping
	Collection<JobView> jobs() {
		return this.jobs //
			.jobs()
			.entrySet()//
			.stream() //
			.map(entry -> new JobView(entry.getKey(), this.requiredContextAttributesFrom(entry.getValue())))
			.toList();
	}

	private String[] requiredContextAttributesFrom(Job job) {
		if (job == null || job.requiredContextAttributes() == null)
			return new String[0];
		return job.requiredContextAttributes()
			.stream()
			// the mogul is supplied from the authenticated principal, never asked for
			.filter(attributeName -> !attributeName.equals(Job.MOGUL_ID_KEY))
			.toArray(String[]::new);
	}

	record JobView(String name, String[] requiredContextAttributes) {
	}

}
