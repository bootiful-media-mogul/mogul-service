package com.joshlong.mogul.api.jobs;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

@Configuration
class JobsConfiguration {

	@Bean
	DefaultJobs jobs(Map<String, Job> jobsMap, JobRequestScheduler jobScheduler) {
		return new DefaultJobs(jobsMap, jobScheduler);
	}

	@Bean
	MogulJobRequestHandler mogulJobRequestHandler(Map<String, Job> jobsMap, ApplicationEventPublisher publisher,
			TransactionTemplate transactionTemplate) {
		return new MogulJobRequestHandler(jobsMap, publisher, transactionTemplate);
	}

}
