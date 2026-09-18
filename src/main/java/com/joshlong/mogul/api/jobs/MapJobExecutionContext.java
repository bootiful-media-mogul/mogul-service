package com.joshlong.mogul.api.jobs;

import org.springframework.util.Assert;

import java.util.Map;
import java.util.function.Supplier;

record MapJobExecutionContext(Long mogulId, Map<String, Object> context) implements JobExecutionContext {

	MapJobExecutionContext {
		Assert.notNull(context, "the context must not be null");
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getContextAttribute(String paramName, Class<T> type) {
		Assert.notNull(paramName, "paramName must not be null");
		Assert.notNull(type, "type must not be null");
		var value = this.context.get(paramName);
		if (value == null)
			return null;
		if (type.isInstance(value))
			return (T) value;
		if (value instanceof Number number) {
			if (type == Long.class)
				return (T) Long.valueOf(number.longValue());
			if (type == Integer.class)
				return (T) Integer.valueOf(number.intValue());
			if (type == Float.class)
				return (T) Float.valueOf(number.floatValue());
			if (type == Double.class)
				return (T) Double.valueOf(number.doubleValue());
		}
		if (type == String.class)
			return (T) value.toString();
		if (type == Boolean.class)
			return (T) Boolean.valueOf(value.toString());
		throw new IllegalArgumentException(
				"the context attribute [" + paramName + "] is a " + value.getClass().getName() + ", not a " + type);
	}

	@Override
	public <T> T getContextAttributeOrDefault(String paramName, Class<T> type, Supplier<T> defaultValue) {
		var value = this.getContextAttribute(paramName, type);
		return value == null ? defaultValue.get() : value;
	}

	@Override
	public long getContextAttributeAsLong(String paramName) {
		return this.getContextAttribute(paramName, Long.class);
	}

	@Override
	public int getContextAttributeAsInteger(String paramName) {
		return this.getContextAttribute(paramName, Integer.class);
	}

	@Override
	public boolean getContextAttributeAsBoolean(String paramName) {
		return this.getContextAttribute(paramName, Boolean.class);
	}

	@Override
	public float getContextAttributeAsFloat(String paramName) {
		return this.getContextAttribute(paramName, Float.class);
	}

	@Override
	public double getContextAttributeAsDouble(String paramName) {
		return this.getContextAttribute(paramName, Double.class);
	}

	@Override
	public String getContextAttributeAsString(String paramName) {
		return this.getContextAttribute(paramName, String.class);
	}

}
