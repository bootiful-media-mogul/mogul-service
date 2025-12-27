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
	void notes(@Autowired JdbcClient db, @Autowired MogulService mogulService, @Autowired PodcastService podcastService,
			@Autowired NoteService noteService) {

		db.sql("delete from note").update();
		var id = db.sql("select id from mogul").query((rs, _) -> rs.getLong("id")).list().getFirst();
		var mogul = mogulService.getMogulById(id);
		Assertions.assertNotNull(mogul, "the mogul is not null");
		var podcasts = podcastService.getAllPodcastsByMogul(mogul.id());
		Assertions.assertFalse(podcasts.isEmpty(), "there should be at least one podcast for this mogul");
		var first = podcasts.iterator().next();
		var noteText = "this is a test note";
		var thisIsATestNote = noteService.create(mogul.id(), first, null, noteText);
		Assertions.assertNotNull(thisIsATestNote, "the note is not null");
		Assertions.assertEquals(noteText, thisIsATestNote.note());
		Assertions.assertEquals(1, noteService.notes(mogul.id(), first).size());
		var secondNote = noteService.create(mogul.id(), first, null, "this is another test note");
		Assertions.assertEquals(2, noteService.notes(mogul.id(), first).size());
		var anotherOne = "another one!";
		var updated = noteService.update(secondNote.id(), null, anotherOne);
		Assertions.assertEquals(anotherOne, updated.note(), "the note was updated");
		Assertions.assertEquals(updated, noteService.getNoteById(updated.id()),
				"the search should return the same instance");
	}

}