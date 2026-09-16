package com.joshlong.mogul.api;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * a diagnostic. every {@code created} column in this schema is a
 * {@code timestamp without time zone}, so the wall clock it holds only means something if
 * you know which zone wrote it -- and migrating those columns to {@code timestamptz}
 * means naming that zone, irreversibly.
 * <p>
 * the answer is the JVM's zone. the PostgreSQL JDBC driver issues a {@code SET TimeZone}
 * to the JVM's default on every connection, so the session zone follows the JVM rather
 * than the server's own configuration. that means both writers agree: rows the
 * application inserts and rows that fall back to a column's {@code default now()} land in
 * the same wall clock. the database values are reported here to confirm that, not because
 * they're independent.
 */
@Controller
class ServerTimeController {

	private final JdbcClient db;

	ServerTimeController(JdbcClient db) {
		this.db = db;
	}

	/**
	 * returns a map rather than a record on purpose: this is deployed as a GraalVM native
	 * image, and a record read reflectively by the GraphQL layer would need registering
	 * for reflection to survive it. a map needs nothing, which is the same reason
	 * {@code MogulController.me()} returns one.
	 */
	@QueryMapping
	Map<String, String> serverTime() {
		var zone = ZoneId.systemDefault();
		var now = Instant.now();
		var databaseTimeZone = this.db.sql("show timezone").query(String.class).single();
		var databaseLocalTime = this.db.sql("select localtimestamp").query(LocalDateTime.class).single();
		var map = new LinkedHashMap<String, String>();
		map.put("instant", now.toString());
		map.put("javaTimeZone", zone.getId());
		map.put("javaOffset", zone.getRules().getOffset(now).getId());
		map.put("javaLocalTime", LocalDateTime.ofInstant(now, zone).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		map.put("databaseTimeZone", databaseTimeZone);
		map.put("databaseLocalTime", databaseLocalTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		return map;
	}

}
