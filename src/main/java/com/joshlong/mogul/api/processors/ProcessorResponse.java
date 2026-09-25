package com.joshlong.mogul.api.processors;

import java.util.Map;

/**
 * what comes back off the reply queue. the {@code context} is whatever the processor
 * produced -- not an echo of what we sent it. we know what we sent it; that is what
 * {@code correlationId} is for.
 */
record ProcessorResponse(String processorId, String correlationId, boolean success, Map<String, Object> context) {
}
