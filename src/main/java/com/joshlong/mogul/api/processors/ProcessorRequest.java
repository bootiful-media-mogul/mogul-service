package com.joshlong.mogul.api.processors;

import java.util.Map;

/**
 * the envelope we put on the wire. it is also carried in the message headers, so that a
 * consumer can route on it without deserializing the body.
 */
record ProcessorRequest(String processorId, String correlationId, Map<String, Object> context) {
}
