package com.joshlong.mogul.api.jobs;

import org.springframework.context.ApplicationEvent;

public class JobLaunchedEvent extends ApplicationEvent {

	public JobLaunchedEvent(Long jobExecutionId) {
		super(jobExecutionId);
	}

	public Long jobExecutionId() {
		return (Long) getSource();
	}

}
