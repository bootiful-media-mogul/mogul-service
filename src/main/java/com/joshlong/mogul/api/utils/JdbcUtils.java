package com.joshlong.mogul.api.utils;

import org.springframework.jdbc.support.KeyHolder;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.ResultSet;
import java.util.Objects;

@SuppressWarnings("unused")
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
	 * it. (sure would be nice to have Java language extension functions a la Kotlin,
	 * though)
	 */
	public static URI uri(ResultSet resultSet, String columnName) {
		try {
			var string = resultSet.getString(columnName);
			if (string == null)
				return null;
			return new URI(string);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

}
