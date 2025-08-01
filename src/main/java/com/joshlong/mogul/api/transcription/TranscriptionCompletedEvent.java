package com.joshlong.mogul.api.transcription;

public record TranscriptionCompletedEvent(Long mogulId, Long transcribableId, Long transcriptionId, Class<?> type,
		String text) {
}
