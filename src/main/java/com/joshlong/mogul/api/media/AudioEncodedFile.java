package com.joshlong.mogul.api.media;

import java.io.File;
import java.util.Map;

public class AudioEncodedFile implements EncodedFile {

	private final File file;

	private final float millisecondsDuration;

	public AudioEncodedFile(File file, float millisecondsDuration) {
		this.millisecondsDuration = millisecondsDuration;
		this.file = file;
	}

	public float millisecondsDuration() {
		return this.millisecondsDuration;
	}

	@Override
	public File file() {
		return this.file;
	}

	@Override
	public Map<String, Object> context() {
		return Map.of("duration", this.millisecondsDuration);
	}

}
