package com.joshlong.mogul.api.utils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public abstract class DateUtils {

	public static Date ensureJavaUtilDate(Date date) {
		if (date == null) {
			throw new IllegalArgumentException("date cannot be null");
		}
		if (date instanceof java.sql.Date sqlData) {
			return new Date(sqlData.getTime());
		}
		return date;
	}

	/**
	 * the inverse of {@link #forDate(Date)}, for dates arriving from the client through
	 * the {@code DateTime} scalar.
	 */
	public static Date toDate(OffsetDateTime offsetDateTime) {
		if (offsetDateTime == null) {
			return null;
		}
		return Date.from(offsetDateTime.toInstant());
	}

	public static OffsetDateTime forDate(Date date) {
		if (date == null) {
			return null;
		}
		return ensureJavaUtilDate(date).toInstant().atOffset(ZoneOffset.UTC);
	}

}
