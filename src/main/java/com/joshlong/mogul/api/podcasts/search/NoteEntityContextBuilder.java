package com.joshlong.mogul.api.podcasts.search;

import com.joshlong.mogul.api.EntityContext;
import com.joshlong.mogul.api.EntityContextBuilder;
import com.joshlong.mogul.api.Notable;
import com.joshlong.mogul.api.Note;
import com.joshlong.mogul.api.notes.NoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
class NoteEntityContextBuilder implements EntityContextBuilder<Note> {

	private final NoteService service;

	private final ApplicationContext applicationContext;

	private final Logger log = LoggerFactory.getLogger(getClass());

	NoteEntityContextBuilder(ApplicationContext applicationContext, NoteService service) {
		this.service = service;
		this.applicationContext = applicationContext;
	}

	@Override
	public EntityContext buildContextFor(Long mogulId, Long id) {
		this.log.info("building context for episode with mogul ID {} and ID {}", mogulId, id);
		var note = this.service.getNoteById(id);
		this.log.info("found note {}", note);
		var notable = this.service.resolveNotable(mogulId, Long.parseLong(note.payload()),
				(Class<Notable>) note.payloadClass());
		this.log.info("found notable {}", notable);
		var delegateContextBuilder = EntityContextBuilder.contextBuilderFor(this.applicationContext,
				notable.getClass());
		this.log.info("found delegateContextBuilder {}", delegateContextBuilder);
		var context = delegateContextBuilder.buildContextFor(mogulId, notable.notableKey());
		this.log.info("trying to build context for episode {} with id {}: result: {}", mogulId, id, context);
		return context;
	}

}
