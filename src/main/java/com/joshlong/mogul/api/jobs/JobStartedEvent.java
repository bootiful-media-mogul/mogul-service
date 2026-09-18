package com.joshlong.mogul.api.jobs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.ApplicationEvent;

public class JobStartedEvent extends ApplicationEvent {

	private final Long mogulId;

	@JsonCreator
	public JobStartedEvent(@JsonProperty("jobName") String jobName, @JsonProperty("mogulId") Long mogulId) {
		super(jobName);
		this.mogulId = mogulId;
	}

	@JsonProperty
	public String jobName() {
		return (String) getSource();
	}

	@JsonProperty
	public Long mogulId() {
		return this.mogulId;
	}

}
