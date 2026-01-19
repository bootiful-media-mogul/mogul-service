package com.joshlong.mogul.api.notes;

import com.joshlong.mogul.api.Notable;
import com.joshlong.mogul.api.Note;

import java.net.URI;
import java.util.Collection;
import java.util.Map;

public interface NoteService {

	<T extends Notable> T resolveNotable(Long mogulId, Long id, String clazz);

	boolean deleteNote(Long mogulId, Long noteId);

	<T extends Notable> T resolveNotable(Long mogulId, Long id, Class<T> clazz);

	Note getNoteById(Long id);

	Map<Long, Note> getNotesById(Collection<Long> ids);

	<T extends Notable> Collection<Note> notes(Long mogulId, Long id, String clazz);

	<T extends Notable> Collection<Note> notes(Long mogulId, T payload);

	<T extends Notable> Note create(Long mogulId, T payload, URI url, String note);

	<T extends Notable> Note update(Long noteId, URI url, String note);

}
