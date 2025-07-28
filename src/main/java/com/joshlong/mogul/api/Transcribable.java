package com.joshlong.mogul.api;

import org.springframework.core.io.Resource;

/**
 * Describes something that could be transcribed.
 */
public interface Transcribable {

	Long transcriptionKey();

	Resource source();

}
