package com.joshlong.mogul.api.notes;

import com.joshlong.mogul.api.Note;
import com.joshlong.mogul.api.search.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Component
@Transactional
class NoteIndexingListener {

	private final SearchService searchService;

	private final Logger log = LoggerFactory.getLogger(getClass());

	NoteIndexingListener(SearchService searchService) {
		this.searchService = searchService;
	}

	@ApplicationModuleListener
	void noteCreatedListener(NoteCreatedEvent event) {
		this.indexNote("created", event.note());
	}

	@ApplicationModuleListener
	void noteUpdatedEvent(NoteUpdatedEvent event) {
		this.indexNote("updated", event.note());
	}

	private void indexNote(String message, Note note) {
		Assert.notNull(note, "the note cannot be null");
		this.log.info("{} a note {}", message, note);
		this.searchService.index(note);
	}

}
