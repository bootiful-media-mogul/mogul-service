package com.joshlong.mogul.api.compositions;

import com.joshlong.mogul.api.Publication;
import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.utils.JdbcUtils;
import com.joshlong.mogul.api.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * helps to manage the lifecycle and entities associated with a given composition, which
 * are blocks of text with attachments. for now, we'll assume attachments are images.
 * maybe one day we'll be able to embed, somehow, audio and videos that have been dragged
 * into a text block. it wouldn't be so hard. some sort of strategy that - given a
 * particular {@link ManagedFile}, consults the {@link ManagedFile#contentType()} and
 * helps to render Markdown that embeds a {@code <video>} player or a {@code <audio>}
 * player or an {@code <img >} tag, as appropriate. for now, though. images.
 */
@Service
@Transactional
class DefaultCompositionService implements CompositionService {

	private final JdbcClient db;

	private final ManagedFileService managedFileService;

	private final RowMapper<Attachment> attachmentRowMapper;

	private final ResultSetExtractor<Collection<Composition>> compositionResultSetExtractor;

	DefaultCompositionService(AttachmentRowMapper attachmentRowMapper, JdbcClient db,
			ManagedFileService managedFileService) {
		this.db = db;
		this.managedFileService = managedFileService;
		this.attachmentRowMapper = attachmentRowMapper;
		this.compositionResultSetExtractor = new CompositionResultSetExtractor(attachmentRowMapper, this.db);
	}

	@Override
	public Composition getCompositionById(Long id) {
		return this.one(this.db //
			.sql("select * from composition where id = ? ") //
			.params(id)//
			.query(this.compositionResultSetExtractor)); //
	}

	private Composition one(Collection<Composition> all) {
		Assert.state(!all.isEmpty(), "there should be at least one composition for the given payload and field");
		return all.iterator().next();
	}

	@Override
	public Map<Long, Composition> getCompositionsByIds(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return new HashMap<>();
		}
		var map = new HashMap<Long, Composition>();
		var collect = ids.stream().map(l -> Long.toString(l)).collect(Collectors.joining(","));
		var listOfCompos = this.db //
			.sql("select * from composition where id in (" + collect + ")") //
			.query(this.compositionResultSetExtractor);
		for (var c : listOfCompos) {
			map.put(c.id(), c);
		}
		return map;
	}

	@Override
	public void deleteCompositionAttachment(Long id) {
		var attachmentById = this.getAttachmentById(id);
		var mf = attachmentById.managedFile();
		this.db.sql("delete from composition_attachment where id = ?").params(id).update();
		this.managedFileService.deleteManagedFile(mf.id());
	}

	@Override
	public <T extends Composable> Composition compose(T payload, String field) {
		var generatedKeyHolder = new GeneratedKeyHolder();
		var key = JsonUtils.write(payload.compositionKey());
		var clazz = payload.getClass().getName();
		this.db //
			.sql("""
					insert into composition(payload, payload_class, field) values (?,?,?)
					on conflict on constraint composition_payload_class_payload_field_key
					do nothing
					""")//
			.params(key, clazz, field)//
			.update(generatedKeyHolder);

		return this.one(this.db //
			.sql("select * from composition where payload_class = ? and payload = ?  and field = ? ")//
			.params(clazz, key, field)//
			.query(this.compositionResultSetExtractor));

	}

	@Override
	public Attachment createCompositionAttachment(Long mogul, Long compositionId, String key) {
		var managedFile = this.managedFileService.createManagedFile(mogul, "compositions", "", 0,
				CommonMediaTypes.BINARY, true);
		var gkh = new GeneratedKeyHolder();
		this.db.sql("""
				insert into composition_attachment( caption, composition_id, managed_file_id )
				values (?,?,?)
				""")//
			.params(key, compositionId, managedFile.id())//
			.update(gkh);
		var ai = JdbcUtils.getIdFromKeyHolder(gkh).longValue();
		return this.getAttachmentById(ai);
	}

	private Attachment getAttachmentById(Long id) {
		return this.db.sql("select * from composition_attachment where id = ?")
			.params(id)
			.query(this.attachmentRowMapper)
			.single();
	}

}

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