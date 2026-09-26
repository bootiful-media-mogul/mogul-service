package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;

import java.util.Map;

/**
 * the end of a {@link MediaService#produce produce}: several audio files went out and one
 * came back. the {@code context} is the caller's own -- the map it handed to
 * {@code produce}, returned to it verbatim -- which is how whoever asked for this render
 * knows which of its episodes just finished.
 *
 * @param error why it failed, if it did. null when {@code success}
 */
public record AudioProducedEvent(ManagedFile out, boolean success, String error, Map<String, Object> context) {

	public static final String DURATION_IN_MILLISECONDS = "durationInMilliseconds";

}
