package com.joshlong.mogul.api.podcasts;

import com.joshlong.mogul.api.Searchable;
import com.joshlong.mogul.api.Transcribable;
import com.joshlong.mogul.api.managedfiles.ManagedFile;

/**
 * represents an arbitrary segment of a podcast episode.
 *
 * @param id the episode segment's ID
 * @param audio the raw source audio for a segment
 * @param producedAudio the produced, normalized, audio for a segment
 * @param crossFadeDuration the duration of the crossfade, if any. Default is 0.
 * @param name the name of the segment
 * @param order the relative order of the segment
 */
public record Segment(Long episodeId, Long id, ManagedFile audio, ManagedFile producedAudio, long crossFadeDuration,
		String name, int order) implements Transcribable, Searchable {

	@Override
	public Long transcribableId() {
		return this.id();
	}

	@Override
	public Long searchableId() {
		return this.id();
	}

}