package com.joshlong.mogul.api.notes;

import com.joshlong.mogul.api.Note;

import java.net.URL;
import java.util.Collection;

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
