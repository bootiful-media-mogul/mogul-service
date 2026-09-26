package com.joshlong.mogul.api.transcripts;

public record TranscriptionFailedEvent(Long mogulId, Long transcribableId, Long transcriptId, Class<?> type,
		String error) {
}
