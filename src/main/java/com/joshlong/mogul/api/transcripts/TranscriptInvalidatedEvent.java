package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.Transcribable;

import java.util.Map;

public record TranscriptInvalidatedEvent(Long mogulId, Long key, Class<? extends Transcribable> type,
		Map<String, Object> context) {
}
