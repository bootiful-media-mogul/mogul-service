package com.joshlong.mogul.api.processors;

import java.time.Instant;
import java.util.Map;

public record ProcessorLaunchedEvent(String id, Map<String, Object> context, Instant when) {
}
