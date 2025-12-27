package com.joshlong.mogul.api.notes;

import com.joshlong.mogul.api.mogul.MogulService;
import com.joshlong.mogul.api.podcasts.PodcastService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class NoteServiceTest {

	@Test
	void create(@Autowired JdbcClient db, @Autowired MogulService mogulService,
			@Autowired PodcastService podcastService, @Autowired NoteService noteService) {

		db.sql("delete from note").update();

		var id = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		var mogul = mogulService.getMogulById(id);
		Assertions.assertNotNull(mogul, "the mogul is not null");

		var podcasts = podcastService.getAllPodcastsByMogul(mogul.id());
		Assertions.assertFalse(podcasts.isEmpty(), "there should be at least one podcast for this mogul");

		var first = podcasts.iterator().next();
		var noteText = "this is a test note";
		var thisIsATestNote = noteService.note(mogul.id(), first, null, noteText);
		Assertions.assertNotNull(thisIsATestNote, "the note is not null");
		Assertions.assertEquals(noteText, thisIsATestNote.note());

		Assertions.assertEquals(noteService.notes(mogul.id(), first).size(), 1);

		noteService.note(mogul.id(), first, null, "this is another test note");

		Assertions.assertEquals(noteService.notes(mogul.id(), first).size(), 2);
	}

}