package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.utils.ReflectionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

class CompositionResultSetExtractor implements ResultSetExtractor<Collection<Composition>> {

	private final AttachmentResultSetExtractor attachments;

	private final JdbcClient db;

	CompositionResultSetExtractor(JdbcClient db, AttachmentResultSetExtractor attachments) {
		this.db = db;
		this.attachments = attachments;
		Assert.notNull(this.db, "the db is null");
		Assert.notNull(this.attachments, "the attachments extractor is null");
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
		if (compositions.isEmpty())
			return compositions.values();
		// every attachment of every composition in one query, and every one of their
		// managed files in one more, however many compositions were asked for.
		var attachmentsByComposition = this.db.sql("select * from composition_attachment where composition_id = any(?)")
			.params(new SqlArrayValue("bigint", compositions.keySet().toArray()))
			.query(this.attachments);
		if (attachmentsByComposition != null)
			attachmentsByComposition.forEach((compositionId, attachments) -> {
				var composition = compositions.get(compositionId);
				if (composition != null)
					composition.attachments().addAll(attachments);
			});
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
