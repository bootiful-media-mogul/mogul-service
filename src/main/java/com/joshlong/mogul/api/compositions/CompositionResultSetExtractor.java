package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.Publication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

class CompositionResultSetExtractor implements ResultSetExtractor<Collection<Composition>> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final RowMapper<Attachment> attachmentRowMapper;

	private final JdbcClient db;

	CompositionResultSetExtractor(AttachmentRowMapper arm, JdbcClient db) {
		attachmentRowMapper = arm;
		this.db = db;
	}

	@Override
	public Collection<Composition> extractData(ResultSet rs) throws SQLException, DataAccessException {
		var compositions = new HashMap<Long, Composition>();
		var indx = 0;
		while (rs.next()) {
			var composition = mapRow(rs, indx);
			compositions.put(composition.id(), composition);
			indx += 1;
		}
		if (!compositions.isEmpty()) {
			var compositionIds = compositions.keySet().stream().map(Object::toString).collect(Collectors.joining(","));
			var attachments = db
				.sql("select * from composition_attachment where composition_id in (" + compositionIds + ")")
				.query((rs1, rowNum) -> {
					Assert.notNull(attachmentRowMapper, "the attachmentRowMapper is not null");
					return Map.of(rs1.getLong("composition_id"),
							Objects.requireNonNull(attachmentRowMapper.mapRow(rs1, rowNum)));
				})
				.list();
			for (var a : attachments) {
				var compositionId = a.keySet().iterator().next();
				var attachment = a.get(compositionId);
				compositions.get(compositionId).attachments().add(attachment);
			}
		}
		return compositions.values();
	}

	private Class<?> classFor(String name) {
		try {
			Assert.hasText(name, "you must provide a non-empty class name");
			return Class.forName(name);
		}
		catch (ClassNotFoundException e) {
			log.warn("classNotFoundException when trying to do Class.forName({}) to resolve the class for a {} ", name,
					Publication.class.getName(), e);
		}
		return null;
	}

	private Composition mapRow(ResultSet rs, int rowNum) throws SQLException {
		var id = rs.getLong("id");
		return new Composition(id, rs.getString("payload"), classFor(rs.getString("payload_class")),
				rs.getString("field"), new ArrayList<>());
	}

}
