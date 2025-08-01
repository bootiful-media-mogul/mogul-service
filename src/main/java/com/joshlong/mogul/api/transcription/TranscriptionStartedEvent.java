package com.joshlong.mogul.api.transcription;

public record TranscriptionStartedEvent(Long mogulId, Long transcribableId, Long transcriptionId, Class<?> type) {
}
