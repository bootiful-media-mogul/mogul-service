package com.joshlong.mogul.api.transcription;

public record TranscriptionCompletedEvent(Long mogulId, Long key, Class<?> type, String text) {
}
