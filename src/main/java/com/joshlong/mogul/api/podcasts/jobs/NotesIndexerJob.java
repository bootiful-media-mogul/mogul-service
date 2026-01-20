package com.joshlong.mogul.api.podcasts.jobs;

import com.joshlong.mogul.api.jobs.Job;
import com.joshlong.mogul.api.notes.NoteService;
import com.joshlong.mogul.api.search.SearchService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class NotesIndexerJob implements Job {

	private final NoteService noteService;

	private final SearchService searchService;

	private final JdbcClient db;

	private final Logger log = LoggerFactory.getLogger(getClass());

	NotesIndexerJob(NoteService noteService, SearchService searchService, JdbcClient db) {
		this.noteService = noteService;
		this.searchService = searchService;
		this.db = db;
	}

	@Override
	public Result run(Map<String, Object> context) throws Exception {
		try {
			this.indexNotesFor(this.from(context, Job.MOGUL_ID_KEY));
		} //
		catch (Throwable throwable) {
			this.log.info(throwable.getMessage(), throwable);
			throw new IllegalStateException(throwable);
		}
		return Result.ok(context);
	}

	private long from(Map<String, Object> ctx, @NonNull String k) {
		var num = ctx.containsKey(k) ? (Number) ctx.get(k) : 0;
		return num.longValue();
	}

	private void indexNotesFor(Long mogulId) {
		this.log.info("indexing notes for mogul # {}", mogulId);
		var list = this.db.sql(" select id from note where mogul_id = ? ") //
			.param(mogulId)//
			.query((rs, rowNum) -> rs.getLong("id")) //
			.list();
		for (var noteId : list) {
			var note = noteService.getNoteById(noteId);
			this.searchService.index(note);

		}
	}

}
