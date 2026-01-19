package com.joshlong.mogul.api.notes;

import com.joshlong.mogul.api.Note;
import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collection;

@Controller
class NotesController {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final MogulService mogulService;

	private final NoteService noteService;

	private final ApplicationEventPublisher publisher;

	NotesController(MogulService mogulService, NoteService noteService, ApplicationEventPublisher publisher) {
		this.mogulService = mogulService;
		this.noteService = noteService;
		this.publisher = publisher;
	}

	@MutationMapping
	boolean deleteNote(@Argument Long id) {
		var currentMogul = this.mogulService.getCurrentMogul();
		return this.noteService.deleteNote(currentMogul.id(), id);
	}

	@MutationMapping
	boolean updateNote(@Argument Long id, @Argument String note) {
		return this.noteService.update(id, null, note) != null;
	}

	@MutationMapping
	boolean createNote(@Argument String type, @Argument Long id, @Argument String note) {
		var mogul = this.mogulService.getCurrentMogul();
		var payload = this.noteService.resolveNotable(mogul.id(), id, type);
		var newNote = this.noteService.create(mogul.id(), payload, null, note);

		return newNote != null;
	}

	@QueryMapping
	Collection<ClientNote> notesForNotable(@Argument String type, @Argument Long id) {
		var currentMogul = this.mogulService.getCurrentMogul();
		return this.noteService.notes(currentMogul.id(), id, type)//
			.stream()//
			.map(note -> this.note(type, note))//
			.toList();
	}

	private ClientNote note(String type, Note note) {
		return new ClientNote(type, note.id(), DateUtils.forDate(note.created()), note.url(), note.note());
	}

}

record ClientNote(String type, Long id, OffsetDateTime created, URI url, String note) {
}

record NoteCreatedEvent(Note note) {
}

record NoteUpdatedEvent(Note note) {
}