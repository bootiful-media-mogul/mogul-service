package com.joshlong.mogul.api.processors;

import java.time.Instant;
import java.util.Map;

public record ProcessorCompletedEvent(String processorId, String correlationId, Map<String, Object> context,
		Instant when, boolean success) {
}
