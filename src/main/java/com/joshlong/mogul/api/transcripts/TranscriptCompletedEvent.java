package com.joshlong.mogul.api.transcripts;

public record TranscriptCompletedEvent(Long mogulId, Long transcribableId, Long transcriptId, Class<?> type,
		String text) {
}
