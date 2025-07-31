package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;

import java.util.Map;

public record TranscriptionInvalidatedEvent(Long mogulId, Long key, Class<? extends Transcribable> type,
		Map<String, Object> context) {
}
