package com.joshlong.mogul.api.jobs;

import org.springframework.context.ApplicationEvent;

public class JobStoppedEvent extends ApplicationEvent {

	public JobStoppedEvent(Long jobExecutionId) {
		super(jobExecutionId);
	}

	public Long jobExecutionId() {
		return (Long) getSource();
	}

}
