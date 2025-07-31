package com.joshlong.mogul.api.transcription;

public record TranscriptionStartedEvent(Long mogulId, Long key, Class<?> type) {
}
