package com.joshlong.mogul.api.utils;

import com.joshlong.mogul.api.utils.jdbc.RewindableResultSets;
import org.springframework.jdbc.support.KeyHolder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.ResultSet;
import java.util.Objects;

public abstract class JdbcUtils {

	public static Number getIdFromKeyHolder(KeyHolder kh) {
		return (Number) Objects.requireNonNull(kh.getKeys()).get("id");
	}

	public static URL url(ResultSet rs, String columnName) {
		try {
			var uri = uri(rs, columnName);
			if (null == uri)
				return null;
			return uri.toURL();
		} //
		catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * URIs are not supported by the PostgreSQL JDBC driver, so we fake it 'till we make
	 * it. (sure would be nice to have Java language extension functions à la Kotlin,
	 * though)
	 */
	public static URI uri(ResultSet resultSet, String columnName) {
		try {
			var string = resultSet.getString(columnName);
			return UriUtils.uri(string);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	/**
	 * wraps the {@link ResultSet} in a {@link RewindableResultSet} that can be iterated
	 * over multiple times. this is useful for scenarios where where you'd like to load
	 * all the records, first, then load the details for particular objects in bulk using
	 * IDs collected during the first sweep.
	 */
	public static RewindableResultSet rewindableResultSet(ResultSet resultSet) {
		return RewindableResultSets.build(resultSet);
	}

}
