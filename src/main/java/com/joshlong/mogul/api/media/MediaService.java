package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;

import java.util.Map;

public interface MediaService {

	/**
	 * asks the {@code processors} module to transcode {@code input} into {@code output},
	 * and returns. the work itself happens in another process, on another machine, and
	 * announces itself when it is done by way of a {@link MediaNormalizedEvent} -- so
	 * nothing downstream of a call to this should assume {@code output} has been written
	 * by the time the call returns. wait for the event.
	 * @param input the {@link ManagedFile} on which to base the operations
	 * @param output the {@link ManagedFile} to which to write the resulting processing
	 * @param context whatever the caller will need in order to make sense of the result
	 * when it eventually arrives. it is held here, not sent to the processor, and comes
	 * back out on the {@link MediaNormalizedEvent}
	 */
	void normalize(ManagedFile input, ManagedFile output, Map<String, Object> context);

}
