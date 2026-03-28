package com.joshlong.mogul.api.jobs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.ApplicationEvent;

public class JobStoppedEvent extends ApplicationEvent {

	@JsonCreator
	public JobStoppedEvent(@JsonProperty("jobExecutionId") Long jobExecutionId) {
		super(jobExecutionId);
	}

	public Long jobExecutionId() {
		return (Long) getSource();
	}

}
