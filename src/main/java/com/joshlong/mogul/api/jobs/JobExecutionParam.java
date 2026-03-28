package com.joshlong.mogul.api.jobs;

public record JobExecutionParam(Long id, String name, Object object, String jsonValue, Class<?> type) {

	@SuppressWarnings("unchecked")
	public <T> T value() {
		return (T) this.object;
	}
}
