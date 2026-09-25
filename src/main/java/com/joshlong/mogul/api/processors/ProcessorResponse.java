package com.joshlong.mogul.api.processors;

import java.util.Map;

/**
 * what comes back off the reply queue: the context we sent, plus whatever the processor
 * produced, plus whether it worked. we kept nothing, so this message is the whole story.
 */
record ProcessorResponse(String processorId, String correlationId, boolean success, String error,
		Map<String, Object> context) {
}
