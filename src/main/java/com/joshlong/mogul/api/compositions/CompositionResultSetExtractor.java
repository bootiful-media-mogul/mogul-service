package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

class CompositionResultSetExtractor implements ResultSetExtractor<Collection<Composition>> {

	private final RowMapper<Attachment> attachmentRowMapper;

	private final JdbcClient db;

	CompositionResultSetExtractor(AttachmentRowMapper arm, JdbcClient db) {
		this.attachmentRowMapper = arm;
		this.db = db;
		Assert.notNull(this.db, "the db is null");
		Assert.notNull(this.attachmentRowMapper, "the attachmentRowMapper is null");
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
			var attachments = db.sql("select * from composition_attachment where composition_id = any(?)")
				.params(new SqlArrayValue("bigint", compositions.keySet().toArray()))
				.query((rs1, rowNum) -> Map.of(rs1.getLong("composition_id"),
						Objects.requireNonNull(this.attachmentRowMapper.mapRow(rs1, rowNum))))
				.list();
			for (var a : attachments) {
				var compositionId = a.keySet().iterator().next();
				var attachment = a.get(compositionId);
				compositions.get(compositionId).attachments().add(attachment);
			}
		}
		return compositions.values();
	}

	private Composition mapRow(ResultSet rs, int rowNum) throws SQLException {
		var id = rs.getLong("id");
		return new Composition(id, rs.getString("payload"), classFor(rs.getString("payload_class")),
				rs.getString("field"), new ArrayList<>());
	}

	private Class<?> classFor(String name) {
		return ReflectionUtils.classForName(name);
	}

}
