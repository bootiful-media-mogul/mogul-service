package com.joshlong.mogul.api.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
class JobsController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final Jobs jobs;

	JobsController(Jobs jobs) {
		this.jobs = jobs;
	}

	@MutationMapping
	boolean launch(String jobName, Map<String, Object> context) {
		try {
			this.jobs.launch(jobName, context);
			this.log.info("launched {} with {}", jobName, context);
		} //
		catch (JobLaunchException jobLaunchException) {
			log.warn("could not launch the job {} with context {}", jobName, context);
			return false;
		}
		return true;
	}

	@QueryMapping
	Map<String, JobView> jobs() {
		var jobs = this.jobs.jobs();
		var response = new HashMap<String, JobView>();
		for (var j : jobs.entrySet()) {
			var value = j.getValue();
			response.put(j.getKey(), new JobView(j.getKey(), value.requiredContextAttributes().toArray(new String[0])));
		}
		return response;
	}

	record JobView(String name, String[] requiredContextAttributes) {
	}

}
