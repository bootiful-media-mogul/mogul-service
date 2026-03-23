package com.joshlong.mogul.api.jobs;

import org.springframework.context.ApplicationEvent;

public class JobCompletedEvent extends ApplicationEvent {

	public JobCompletedEvent(Long jobExecutionId) {
		super(jobExecutionId);
	}

	public Long jobExecutionId() {
		return (Long) getSource();
	}

}
