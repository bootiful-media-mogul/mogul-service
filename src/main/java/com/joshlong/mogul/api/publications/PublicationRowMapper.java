package com.joshlong.mogul.api.publications;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.utils.JdbcUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class PublicationRowMapper implements RowMapper<Publication> {

	// @formatter:off
	private final ParameterizedTypeReference<Map<String, String>> mapParameterizedTypeReference = new ParameterizedTypeReference<>() {};
	// @formatter:on

	private final Map<Long, List<Publication.Outcome>> publicationToOutcomes = new ConcurrentHashMap<>();

	private final TextEncryptor textEncryptor;

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final JdbcClient db;

	private final RowMapper<PublicationOutcome> outcomeRowMapper = (rs, _) -> {
		var outcome = new Publication.Outcome(rs.getInt("id"), rs.getDate("created"), rs.getBoolean("success"),
				JdbcUtils.url(rs, "uri"), rs.getString("key"), rs.getString("server_error_message"));
		return new PublicationOutcome(rs.getLong("publication_id"), outcome);
	};

	PublicationRowMapper(JdbcClient db, TextEncryptor textEncryptor) {
		this.textEncryptor = textEncryptor;
		this.db = db;
	}

	@Override
	public Publication mapRow(ResultSet rs, int rowNum) throws SQLException {
		var last = rs.isLast();
		var context = this.readContext(rs.getString("context"));
		var payload = rs.getString("payload");
		var state = rs.getString("state");
		var stateEnum = Publication.State.valueOf(state);
		Assert.notNull(state, "state must not be null");
		Assert.notNull(stateEnum, "stateEnum must not be null");
		var publicationId = rs.getLong("id");
		var publication = new Publication( //
				rs.getLong("mogul_id"), //
				publicationId, //
				rs.getString("plugin"), //
				rs.getTimestamp("created"), //
				rs.getTimestamp("published"), //
				context, //
				payload, //
				ReflectionUtils.classForName(rs.getString("payload_class")), //
				stateEnum, this.publicationToOutcomes.computeIfAbsent(publicationId, _ -> new ArrayList<>()));

		if (last) {
			this.materializeOutcomes();
		}
		return publication;
	}

	private Map<String, String> readContext(String context) {
		var decrypted = this.textEncryptor.decrypt(context);
		return JsonUtils.read(decrypted, this.mapParameterizedTypeReference);
	}

	private void materializeOutcomes() {
		var publicationIds = new ArrayList<>(this.publicationToOutcomes.keySet());
		if (publicationIds.isEmpty()) {
			return;
		}
		var collect = publicationIds.toArray(Long[]::new);
		var placeholders = publicationIds.stream().map(_ -> "?").collect(Collectors.joining(","));
		var sql = "select * from publication_outcome where publication_id in (" + placeholders + ")  ";
		var outcomes = this.db.sql(sql).params((Object[]) collect).query(this.outcomeRowMapper).list();
		this.publicationToOutcomes.forEach((publicationId, list) -> {
			outcomes.stream() //
				.filter(o -> o.publicationId().equals(publicationId)) //
				.forEach(o -> list.add(o.outcome()));

			list.sort(Comparator.comparing(Publication.Outcome::created).reversed());
		});

	}

	private record PublicationOutcome(Long publicationId, Publication.Outcome outcome) {
	}

}
