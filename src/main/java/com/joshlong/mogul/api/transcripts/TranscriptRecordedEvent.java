package com.joshlong.mogul.api.transcripts;

/**
 * signals that the transcript has been written and therefore interested parties should
 * invalidate the caches of the aggregates that contain the objects.
 */
public record TranscriptRecordedEvent(Long mogulId, Long transcribableId, Class<?> type) {
}
