package com.joshlong.mogul.api.transcription;

import com.joshlong.mogul.api.Transcribable;

import java.util.Map;

public record TranscriptionRequest(Long mogulId, Transcribable payload, Map<String, Object> context) {
}
