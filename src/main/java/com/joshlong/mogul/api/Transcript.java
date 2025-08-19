package com.joshlong.mogul.api;

import java.util.Date;

public record Transcript(Long mogulId, Long id, Date created, Date transcribed, String payload,
		Class<? extends Transcribable> payloadClass, String transcript) {
}
