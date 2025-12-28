package com.joshlong.mogul.api.notes;

import com.joshlong.mogul.api.AbstractDomainService;
import com.joshlong.mogul.api.Notable;
import com.joshlong.mogul.api.NotableResolver;
import com.joshlong.mogul.api.Note;
import com.joshlong.mogul.api.utils.JsonUtils;
import com.joshlong.mogul.api.utils.ReflectionUtils;
import com.joshlong.mogul.api.utils.UriUtils;
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

	Note getNoteById(Long id);

	<T extends Notable> Collection<Note> notes(Long mogulId, T payload);

	<T extends Notable> Note create(Long mogulId, T payload, URI url, String note);

	<T extends Notable> Note update(Long noteId, URI url, String note);

}

@Service
@Transactional
class DefaultNoteService extends AbstractDomainService<Notable, NotableResolver<?>> implements NoteService {

	private final RowMapper<Note> noteRowMapper = (rs, _) -> new Note(rs.getLong("mogul_id"), rs.getLong("id"),
			rs.getString("payload"), ReflectionUtils.classForName(rs.getString("payload_class")), rs.getDate("created"),
			UriUtils.uri(rs.getString("url")), rs.getString("note"));

	private final JdbcClient db;

	DefaultNoteService(Collection<NotableResolver<?>> repositories, JdbcClient db) {
		super(repositories);
		this.db = db;
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
	public <T extends Notable> Collection<Note> notes(Long mogulId, T payload) {
		return this.db //
			.sql("select * from note where payload = ? and payload_class = ? order by created ")//
			.params(JsonUtils.write(payload.notableKey()), payload.getClass().getName()) //
			.query(this.noteRowMapper)//
			.list();
	}

	@Override
	public <T extends Notable> Note update(Long noteId, URI url, String note) {
		this.db.sql("update note set  url = ?, note = ? where id = ?") //
			.params(url == null ? null : url.toString(), note, noteId) //
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
