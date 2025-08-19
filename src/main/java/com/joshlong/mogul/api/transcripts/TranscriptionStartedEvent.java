package com.joshlong.mogul.api.transcripts;

public record TranscriptionStartedEvent(Long mogulId, Long transcribableId, Long transcriptId, Class<?> type) {
}
