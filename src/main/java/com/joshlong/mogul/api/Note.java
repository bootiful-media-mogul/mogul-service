package com.joshlong.mogul.api;

import java.net.URI;
import java.util.Date;

/**
 * represents a note attached to a {@link Notable notable } thing.
 */
public record Note(Long mogulId, Long id, String payload, Class<?> payloadClass, Date created, URI url, String note) {
}
