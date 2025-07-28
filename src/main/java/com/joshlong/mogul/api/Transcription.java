package com.joshlong.mogul.api;

import java.util.Date;

public record Transcription(

		Long id, Date created, Date transcribed, String payload, Class<?> payloadClass, String transcript) {
}
