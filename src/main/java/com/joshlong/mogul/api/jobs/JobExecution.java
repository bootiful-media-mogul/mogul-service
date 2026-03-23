package com.joshlong.mogul.api.jobs;

import org.springframework.util.Assert;

import java.util.Map;
import java.util.Objects;

public class JobExecution {

	private final Long id;

	private final Long mogulId;

	private final String jobName;

	private final Map<String, JobExecutionParam> context;

	JobExecution(Long id, Long mogulId, String jobName, Map<String, JobExecutionParam> context) {
		this.id = id;
		this.mogulId = mogulId;
		this.jobName = jobName;
		this.context = context;
	}

	public Long id() {
		return id;
	}

	public Long mogulId() {
		return mogulId;
	}

	public String jobName() {
		return jobName;
	}

	public Map<String, JobExecutionParam> context() {
		return context;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass())
			return false;
		var that = (JobExecution) o;
		return Objects.equals(this.id, that.id) && Objects.equals(this.mogulId, that.mogulId)
				&& Objects.equals(this.jobName, that.jobName) && Objects.equals(this.context, that.context);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.id, this.mogulId, this.jobName, this.context);
	}

	public <T> T getContextAttribute(String paramName, Class<T> type) {
		Assert.notNull(paramName, "paramName must not be null");
		Assert.notNull(type, "type must not be null");
		if (!this.context.containsKey(paramName) || this.context.get(paramName) == null)
			return null;

		var je = this.context.get(paramName);
		return (T) je.value();
	}

}