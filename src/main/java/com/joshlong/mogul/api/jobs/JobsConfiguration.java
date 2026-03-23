package com.joshlong.mogul.api.jobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Collection;
import java.util.Map;

@Configuration
class JobsConfiguration {

	@Bean
	DefaultJobs jobs(JdbcClient db, Map<String, Job> jobsMap,
			@Autowired(required = false) Collection<DefaultJobExecutionParamProvider> jobParamPreparers,
			ApplicationEventPublisher publisher) {
		return new DefaultJobs(jobsMap, db, publisher, jobParamPreparers);
	}

}
