package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;

import java.util.Map;

public interface MediaService {

	/**
	 * an asynchronous operation that publishes a {@link MediaNormalizedEvent} on success.
	 * @param input the {@link ManagedFile} on which to base the operations
	 * @param output the {@link ManagedFile} to which to write the resulting processing
	 */
	void normalize(ManagedFile input, ManagedFile output, Map<String, Object> context);

}
