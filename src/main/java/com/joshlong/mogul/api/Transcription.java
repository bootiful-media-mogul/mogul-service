package com.joshlong.mogul.api;

import java.util.Date;

public record Transcription(Long mogulId, Long id, Date created, Date transcribed, String payload,
		Class<? extends Transcribable> payloadClass, String transcript) {
}
