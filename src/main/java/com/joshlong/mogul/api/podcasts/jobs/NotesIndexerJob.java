package com.joshlong.mogul.api.podcasts.jobs;

import com.joshlong.mogul.api.jobs.Job;
import com.joshlong.mogul.api.jobs.JobExecutionContext;
import com.joshlong.mogul.api.jobs.JobExecutionResult;
import com.joshlong.mogul.api.notes.NoteService;
import com.joshlong.mogul.api.search.SearchService;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class NotesIndexerJob implements Job {

	private final NoteService noteService;

	private final SearchService searchService;

	private final Logger log = LoggerFactory.getLogger(getClass());

	NotesIndexerJob(NoteService noteService, SearchService searchService) {
		this.noteService = noteService;
		this.searchService = searchService;
	}

	@Override
	public JobExecutionResult run(JobExecutionContext context) throws Exception {
		try {
			this.indexNotesFor(context.mogulId());
		} //
		catch (Throwable throwable) {
			this.log.info(throwable.getMessage(), throwable);
			throw new IllegalStateException(throwable);
		}
		return JobExecutionResult.ok();
	}

	private void indexNotesFor(Long mogulId) {
		this.log.info("indexing notes for mogul # {}", mogulId);
		// one read for the whole mogul. selecting the ids and then loading each note
		// behind them cost a query per note to fetch rows the first query had already
		// found.
		for (var note : this.noteService.getNotesByMogul(mogulId))
			this.searchService.index(note);
	}

}
