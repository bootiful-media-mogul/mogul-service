package com.joshlong.mogul.api.transcripts.audio;

import org.springframework.core.io.Resource;

/**
 * given an audio file, return a textual transcript of that audio file
 */
public interface Transcriber {

	String transcribe(Resource audio);

}
