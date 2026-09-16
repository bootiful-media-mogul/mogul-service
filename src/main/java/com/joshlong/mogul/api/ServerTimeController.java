package com.joshlong.mogul.api;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * a diagnostic. every {@code created} column in this schema is a
 * {@code timestamp without time zone}, so the wall clock it holds only means something if
 * you know which zone wrote it -- and two different things do the writing. rows the
 * application inserts through JDBC are converted using the <em>JVM's</em> default zone;
 * rows that fall back to a column's {@code default now()} are written in
 * <em>Postgres'</em> session zone. this reports both so the two can be compared before
 * anything is migrated to {@code timestamptz}, which requires naming the zone the
 * existing values were written in.
 */
@Controller
class ServerTimeController {

	private final JdbcClient db;

	ServerTimeController(JdbcClient db) {
		this.db = db;
	}

	@QueryMapping
	ServerTime serverTime() {
		var zone = ZoneId.systemDefault();
		var now = Instant.now();
		var databaseTimeZone = this.db.sql("show timezone").query(String.class).single();
		var databaseLocalTime = this.db.sql("select localtimestamp").query(LocalDateTime.class).single();
		return new ServerTime(//
				now.toString(), //
				zone.getId(), //
				zone.getRules().getOffset(now).getId(), //
				LocalDateTime.ofInstant(now, zone).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), //
				databaseTimeZone, //
				databaseLocalTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
	}

	/**
	 * @param instant the actual moment, which nobody disagrees about
	 * @param javaTimeZone the JVM's default zone
	 * @param javaOffset that zone's offset right now, daylight saving included
	 * @param javaLocalTime the wall clock the JVM sees -- what a timestamp written by the
	 * application lands as
	 * @param databaseTimeZone Postgres' session time zone
	 * @param databaseLocalTime the wall clock Postgres sees -- what a {@code default
	 * now()} column lands as
	 */
	record ServerTime(String instant, String javaTimeZone, String javaOffset, String javaLocalTime,
			String databaseTimeZone, String databaseLocalTime) {
	}

}
