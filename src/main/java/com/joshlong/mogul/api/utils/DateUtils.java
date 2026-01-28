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

	public static OffsetDateTime forDate(Date date) {
		if (date == null) {
			return null;
		}
		return ensureJavaUtilDate(date).toInstant().atOffset(ZoneOffset.UTC);
	}

}
