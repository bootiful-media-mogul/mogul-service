package com.joshlong.mogul.api.jobs;

import java.util.function.Supplier;

public interface JobExecutionContext {

	<T> T getContextAttribute(String paramName, Class<T> type);
	// <T> T getContextAttributeAsLong(String paramName);

	<T> T getContextAttributeOrDefault(String paramName, Class<T> type, Supplier<T> defaultValue);

	Long mogulId();

}
