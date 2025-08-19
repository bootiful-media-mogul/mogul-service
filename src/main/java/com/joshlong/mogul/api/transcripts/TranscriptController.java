package com.joshlong.mogul.api.transcripts;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
class TranscriptController {

	private final TranscriptService transcriptService;

	TranscriptController(TranscriptService transcriptService) {
		this.transcriptService = transcriptService;
	}

	@MutationMapping
	boolean refreshTranscript(@Argument Long transcriptId) {
		var transcript = this.transcriptService.transcriptById(transcriptId);
		this.transcriptService.transcribe(transcript.mogulId(), transcript.id());
		return true;
	}

	@MutationMapping
	boolean writeTranscript(@Argument Long transcriptId, @Argument String transcript) {
		this.transcriptService.writeTranscript(transcriptId, transcript);
		return true;
	}

}
