package com.joshlong.mogul.api.transcription;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

@Controller
class TranscriptionController {

	private final TranscriptionService transcriptionService;

	TranscriptionController(TranscriptionService transcriptionService) {
		this.transcriptionService = transcriptionService;
	}

	@MutationMapping
	boolean refreshTranscription(@Argument Long transcriptionId) {
		var transcription = this.transcriptionService.transcriptionById(transcriptionId);
		this.transcriptionService.transcribe(transcription.mogulId(), transcription.id());
		return true;
	}

	@MutationMapping
	boolean writeTranscript(@Argument Long transcriptionId, @Argument String transcript) {
		this.transcriptionService.writeTranscript(transcriptionId, transcript);
		return true;
	}

}
