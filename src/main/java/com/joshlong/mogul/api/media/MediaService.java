package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.managedfiles.ManagedFile;

import java.util.List;
import java.util.Map;

/**
 * hands the expensive parts of dealing with media -- {@code ffmpeg}, {@code magick} -- to
 * the {@code processors} module. both of these return the moment the request is on the
 * wire; the work happens in another process and announces itself later with an event
 * carrying the {@code context} the caller sent.
 */
public interface MediaService {

	void normalize(ManagedFile input, ManagedFile output, Map<String, Object> context);

	/**
	 * joins {@code inputs}, in order, into one mp3 written over {@code output}. finishes
	 * with an {@link AudioProducedEvent}.
	 */
	void produce(List<ManagedFile> inputs, ManagedFile output, Map<String, Object> context);

}
