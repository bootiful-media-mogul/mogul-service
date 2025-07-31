package com.joshlong.mogul.api.transcription;

import org.springframework.stereotype.Controller;

@Controller
class TranscriptionController {

	private final TranscriptionService transcriptionService;

	TranscriptionController(TranscriptionService transcriptionService) {
		this.transcriptionService = transcriptionService;
	}

}
