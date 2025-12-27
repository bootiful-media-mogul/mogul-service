package com.joshlong.mogul.api.notes;

import com.joshlong.mogul.api.Note;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Collection;
import java.util.List;

/**
 * like post-it notes for various objects in the system like
 * {@link com.joshlong.mogul.api.mogul.Mogul moguls},
 * {@link com.joshlong.mogul.api.podcasts.Podcast podcasts},
 * {@link com.joshlong.mogul.api.podcasts.Episode episodes},
 * {@link com.joshlong.mogul.api.blogs.Blog blogs},
 * {@link com.joshlong.mogul.api.blogs.Post posts}, etc.
 */
public interface NoteService {

	Note note(Long mogulId, String note, URL url);

	Note byId(Long noteId);

	Collection<Note> notesByNotableId(Long notableId);

	/**
	 * Note sure what this is meant to do. Do we simply use PostgreSQL's full-text
	 * queries? Do we plugin Elastic.co (I think I have a free account with them)? Would
	 * notes be encrypted? How would this work? Do we support vector search?
	 */
	Collection<Note> search(String query);

}

/**
 * this handles persistence of the {@link Note note} entity behind the scenes. it needs to
 * be matched with a repositoru which can produce instances of
 * {@link com.joshlong.mogul.api.Notable notable} given a {@code notableId}.
 */

@Service
class DefaultNoteService implements NoteService {

	private final JdbcClient db;

	DefaultNoteService(JdbcClient db) {
		this.db = db;
	}

	@Override
	public Note note(Long mogulId, String note, URL url) {
		return null;
	}

	@Override
	public Note byId(Long noteId) {
		return null;
	}

	@Override
	public Collection<Note> notesByNotableId(Long notableId) {
		return List.of();
	}

	@Override
	public Collection<Note> search(String query) {
		return List.of();
	}

}