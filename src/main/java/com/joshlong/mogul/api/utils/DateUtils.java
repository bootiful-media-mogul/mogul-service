package com.joshlong.mogul.api.utils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public abstract class DateUtils {

	public static OffsetDateTime forDate(Date date) {
		if (date == null) {
			return null;
		}
		return date.toInstant().atOffset(ZoneOffset.UTC);
	}

}
