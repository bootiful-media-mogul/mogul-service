package com.joshlong.mogul.api.jobs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.ApplicationEvent;

public class JobStartedEvent extends ApplicationEvent {

	@JsonCreator
	public JobStartedEvent(@JsonProperty("jobExecutionId") Long jobExecutionId) {
		super(jobExecutionId);
	}

	@JsonProperty
	public Long jobExecutionId() {
		return (Long) getSource();
	}

}
