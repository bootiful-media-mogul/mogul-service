package com.joshlong.mogul.api;

import java.util.Date;

/**
 * represents a note attached to a {@link Notable notable } thing.
 */
public record Note(Long mogulId, Long id, Date created, String url, String note) {
}
