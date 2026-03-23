package com.joshlong.mogul.api.jobs;

import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
class JobsController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Jobs jobs;

	private final MogulService mogulService;

	private final ManagedFileService managedFileService;

	JobsController(Jobs jobs, MogulService mogulService, ManagedFileService managedFileService) {
		this.jobs = jobs;
		this.mogulService = mogulService;
		this.managedFileService = managedFileService;
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
			this.log.info("launched {} with {}", jobName, context);
		} //
		catch (Throwable jobLaunchException) {
			this.log.warn("could not launch the job {} with context {}", jobName, context);
			return false;
		}
		return true;
	}

	@QueryMapping
	Collection<JobView> jobs() {
		var mogul = this.mogulService.getCurrentMogul().id();
		return this.jobs //
			.jobs()
			.entrySet()//
			.stream() //
			.map(x -> {
				try {
					return this.buildJobView(mogul, x);
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			})
			.toList();
	}

	// todo have some strategy interface or something
	// that inspects the jobs and provides the defaults
	private JobView buildJobView(Long mogul, Map.Entry<String, Job> jobEntry) throws Exception {

		var preparedJob = this.jobs.prepare(mogul, jobEntry.getKey(), Map.of());
		// .filter(s -> !Objects.equals(s, Job.MOGUL_ID_KEY))
		return new JobView(jobEntry.getKey(), preparedJob.context(),
				jobEntry.getValue().requiredContextAttributes().toArray(String[]::new));
	}

	record JobView(String name, Map<String, JobExecutionParam> contextAttributes, String[] requiredContextAttributes) {
	}

}
