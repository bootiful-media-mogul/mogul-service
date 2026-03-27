package com.joshlong.mogul.api.jobs;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.util.Collection;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Configuration
class JobsConfiguration {

	@Bean
	DefaultJobs jobs(JdbcClient db, Map<String, Job> jobsMap,
			@Autowired(required = false) Collection<JobExecutionParamProvider> jobParamPreparers,
			ApplicationEventPublisher publisher) {
		return new DefaultJobs(jobsMap, db, publisher, jobParamPreparers);
	}

	@Bean
	JobExecutor jobExecutor(DefaultJobs jobs, ApplicationEventPublisher publisher, JdbcClient jdbcClient,
			IncompleteEventPublications eventPublications) {
		return new JobExecutor(jobs, eventPublications, jdbcClient, publisher,
				(jobExecutionId, outputContextAttributes) -> {
					var outputAttributesButAsSuppliers = new HashMap<String, Supplier<Object>>();
					for (var key : outputContextAttributes.keySet()) {
						outputAttributesButAsSuppliers.put(key, () -> outputContextAttributes.get(key));
					}
					jobs.writeContextAttributesForJobExecution(jobExecutionId, outputAttributesButAsSuppliers);
					return null;
				});
	}

}
