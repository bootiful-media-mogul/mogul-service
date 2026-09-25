package com.joshlong.mogul.api.media;

import java.util.Date;
import java.util.Map;

/**
 * one outstanding -- or finished -- request to the {@code processors} module. this is the
 * api's half of the correlation id: the reply carries nothing but that id, and this is
 * what it buys you.
 */
record MediaNormalization(Long id, String correlationId, Long inputManagedFileId, Long outputManagedFileId,
		Map<String, Object> context, Date created, Date completed, Boolean successful, String error) {
}
