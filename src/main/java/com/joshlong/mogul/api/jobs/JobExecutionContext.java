package com.joshlong.mogul.api.jobs;

import java.util.function.Supplier;

public interface JobExecutionContext {

	<T> T getContextAttribute(String paramName, Class<T> type);

	long getContextAttributeAsLong(String paramName);

	int getContextAttributeAsInteger(String paramName);

	boolean getContextAttributeAsBoolean(String paramName);

	float getContextAttributeAsFloat(String paramName);

	double getContextAttributeAsDouble(String paramName);

	String getContextAttributeAsString(String paramName);

	<T> T getContextAttributeOrDefault(String paramName, Class<T> type, Supplier<T> defaultValue);

	Long mogulId();

}
