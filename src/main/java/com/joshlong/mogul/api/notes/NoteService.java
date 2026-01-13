package com.joshlong.mogul.api.notes;

import com.joshlong.mogul.api.AbstractDomainService;
import com.joshlong.mogul.api.Notable;
import com.joshlong.mogul.api.NotableResolver;
import com.joshlong.mogul.api.Note;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import com.joshlong.mogul.api.utils.UriUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;

public interface NoteService {

	<T extends Notable> String typeFor(T entity);

	<T extends Notable> T resolveNotable(Long mogulId, Long id, String clazz);

	boolean deleteNote(Long mogulId, Long noteId);

	<T extends Notable> T resolveNotable(Long mogulId, Long id, Class<T> clazz);

	Note getNoteById(Long id);

	<T extends Notable> Collection<Note> notes(Long mogulId, Long id, String clazz);

	<T extends Notable> Collection<Note> notes(Long mogulId, T payload);

	<T extends Notable> Note create(Long mogulId, T payload, URI url, String note);

	<T extends Notable> Note update(Long noteId, URI url, String note);

}

@SuppressWarnings("unchecked")
@Service
@Transactional
class DefaultNoteService extends AbstractDomainService<Notable, NotableResolver<?>> implements NoteService {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final RowMapper<Note> noteRowMapper = (rs, _) -> new Note(rs.getLong("mogul_id"), rs.getLong("id"),
			rs.getString("payload"), ReflectionUtils.classForName(rs.getString("payload_class")),
			new java.util.Date(rs.getTimestamp("created").getTime()), UriUtils.uri(rs.getString("url")),
			rs.getString("note"));

	private final JdbcClient db;

	private final MogulService mogulService;

	DefaultNoteService(Collection<NotableResolver<?>> resolvers, JdbcClient db, MogulService mogulService) {
		super(resolvers);
		this.db = db;
		this.mogulService = mogulService;
	}

	@Override
	public <T extends Notable> T resolveNotable(Long mogulId, Long id, String clazz) {
		var type = (Class<? extends T>) this.classForType(clazz);

		this.log.debug("Resolving notable entity of type {} for mogulId {} and id {}", type.getSimpleName(), mogulId,
				id);

		return this.resolveNotable(mogulId, id, type);
	}

	@Override
	public boolean deleteNote(Long mogulId, Long noteId) {
		return this.db.sql("delete from note where id = ? and mogul_id = ?").params(noteId, mogulId).update() > 0;
	}

	@Override
	public <T extends Notable> T resolveNotable(Long mogulId, Long id, Class<T> clazz) {
		this.mogulService.assertAuthorizedMogul(mogulId);
		return this.findEntity(clazz, id);
	}

	@Override
	public Note getNoteById(Long id) {
		return this.db //
			.sql("select * from note where id = ? ") //
			.params(id)//
			.query(this.noteRowMapper) //
			.single();
	}

	@Override
	public <T extends Notable> Collection<Note> notes(Long mogulId, Long id, String clazz) {
		var payload = this.resolveNotable(mogulId, id, clazz);
		return this.notes(mogulId, payload);
	}

	@Override
	public <T extends Notable> Collection<Note> notes(Long mogulId, T payload) {
		var list = this.db //
			.sql("select * from note where payload = ? and payload_class = ? order by created ")//
			.params(JsonUtils.write(payload.notableKey()), payload.getClass().getName()) //
			.query(this.noteRowMapper)//
			.list();
		log.info("found {} notes for {} of type {}", list.size(), payload.notableKey(), payload.getClass().getName());
		return list;
	}

	@Override
	public <T extends Notable> Note update(Long noteId, URI url, String note) {
		this.db.sql("update note set  url = ?, note = ? where id = ?") //
			.param(url == null ? null : url.toString())
			.param(note)
			.param(noteId) //
			.update();
		return this.getNoteById(noteId);
	}

	@Override
	public <T extends Notable> Note create(Long mogulId, T payload, URI url, String note) {
		var payloadKeyAsJson = JsonUtils.write(payload.notableKey());
		var newNote = new Note(mogulId, null, payloadKeyAsJson, payload.getClass(), new Date(), url, note);
		var kg = new GeneratedKeyHolder();
		db.sql("""
				 insert into note (mogul_id, created, payload, payload_class, url, note)
				 values (?, ?, ?, ?, ?, ?)
				""")
			.params(mogulId, newNote.created(), newNote.payload(), newNote.payloadClass().getName(),
					url == null ? null : url.toString(), note)
			.update(kg);

		var insertedId = ((Integer) Objects.requireNonNull(kg.getKeys()).get("id")).longValue();
		return getNoteById(insertedId);

	}

}
