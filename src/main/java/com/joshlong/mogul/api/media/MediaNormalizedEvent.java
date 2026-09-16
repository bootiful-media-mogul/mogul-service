package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;

import java.util.Map;

public record MediaNormalizedEvent(ManagedFile in, ManagedFile out, Map<String, Object> context) {

	/**
	 * the key under which {@code context} carries an audio file's duration. only present
	 * for audio; an image normalization has no such thing.
	 */
	public static final String DURATION_IN_MILLISECONDS = "durationInMilliseconds";

}
