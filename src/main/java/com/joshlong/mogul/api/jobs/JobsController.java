package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.notifications.NotificationEvent;
import com.joshlong.mogul.api.notifications.NotificationEvents;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Controller
class JobsController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Jobs jobs;

	private final MogulService mogulService;

	JobsController(Jobs jobs, MogulService mogulService) {
		this.jobs = jobs;
		this.mogulService = mogulService;
	}

	@MutationMapping
	boolean launchJob(@Argument String jobName, @Argument String contextAsJson) {
		// @formatter:off
        var typeReference = new ParameterizedTypeReference<Map<String,Object>>() {};
        // @formatter:on
		var context = JsonUtils.read(contextAsJson, typeReference);
		try {
			context = context == null ? new HashMap<>() : new HashMap<>(context);
			var mogulId = this.mogulService.getCurrentMogul().id();
			context.putIfAbsent(Job.MOGUL_ID_KEY, mogulId);
			this.log.info("launching job with mogul # {}, context # {}", mogulId, context);
			this.jobs.launch(jobName, context) //
				.thenAccept(result -> {
					var jobCompletionEvent = new JobCompletedEvent(mogulId, result.success(), jobName);
					this.emit(jobName, true, mogulId, jobCompletionEvent);
				}) //
				.exceptionally(throwable -> {
					var jce = new JobCompletedEvent(mogulId, false, jobName);
					this.emit(jobName, true, mogulId, jce);
					return null;
				});
			this.log.info("launched {} with {}", jobName, context);
		} //
		catch (Throwable jobLaunchException) {
			this.log.warn("could not launch the job {} with context {}", jobName, context);
			return false;
		}
		return true;
	}

	private void emit(String jobName, boolean success, Long mogulId, JobCompletedEvent event) {
		var json = JsonUtils.write(Map.of("jobName", jobName, "success", success));
		var notificationEvent = NotificationEvent //
			.systemNotificationEventFor(mogulId, event, jobName, json);
		NotificationEvents.notifyAsync(notificationEvent);
	}

	@QueryMapping
	Collection<JobView> jobs() {
		var jobs = this.jobs //
			.jobs()
			.entrySet()//
			.stream() //
			.map(e -> new JobView(e.getKey(),
					e.getValue()
						.requiredContextAttributes()
						.stream()
						.filter(s -> !Objects.equals(s, Job.MOGUL_ID_KEY))
						.toArray(String[]::new)))
			.toList();
		log.info("jobs:{}", jobs);
		return jobs;
	}

	record JobView(String name, String[] requiredContextAttributes) {
	}

}

record JobCompletedEvent(Long mogulId, boolean succeeded, String jobName) {
}
