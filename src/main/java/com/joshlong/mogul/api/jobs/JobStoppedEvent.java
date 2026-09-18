package com.joshlong.mogul.api.jobs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.ApplicationEvent;

public class JobStoppedEvent extends ApplicationEvent {

	private final Long mogulId;

	private final boolean success;

	@JsonCreator
	public JobStoppedEvent(@JsonProperty("jobName") String jobName, @JsonProperty("mogulId") Long mogulId,
			@JsonProperty("success") boolean success) {
		super(jobName);
		this.mogulId = mogulId;
		this.success = success;
	}

	@JsonProperty
	public String jobName() {
		return (String) getSource();
	}

	@JsonProperty
	public Long mogulId() {
		return this.mogulId;
	}

	@JsonProperty
	public boolean success() {
		return this.success;
	}

}
