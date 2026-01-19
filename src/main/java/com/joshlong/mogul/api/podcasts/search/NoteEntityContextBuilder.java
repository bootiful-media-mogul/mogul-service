package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.EntityContext;
import com.joshlong.mogul.api.EntityContextBuilder;
import com.joshlong.mogul.api.Notable;
import com.joshlong.mogul.api.Note;
import com.joshlong.mogul.api.notes.NoteService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
class NoteEntityContextBuilder implements EntityContextBuilder<Note> {

	private final NoteService service;

	private final ApplicationContext applicationContext;

	NoteEntityContextBuilder(ApplicationContext applicationContext, NoteService service) {
		this.service = service;
		this.applicationContext = applicationContext;
	}

	@Override
	public EntityContext buildContextFor(Long mogulId, Long id) {
		var note = this.service.getNoteById(id);
		var notable = this.service.resolveNotable(mogulId, Long.parseLong(note.payload()),
				(Class<Notable>) note.payloadClass());
		var delegateContextBuilder = EntityContextBuilder.contextBuilderFor(this.applicationContext,
				notable.getClass());
		return delegateContextBuilder.buildContextFor(mogulId, notable.notableKey());
	}

}
