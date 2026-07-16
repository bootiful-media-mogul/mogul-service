package com.joshlong.mogul.api.media;

import java.io.File;
import java.util.Map;

public class AudioEncodedFile implements EncodedFile {

	private final File file;

	private final long millisecondsDuration;

	public AudioEncodedFile(File file, long millisecondsDuration) {
		this.millisecondsDuration = millisecondsDuration;
		this.file = file;
	}

	public long millisecondsDuration() {
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
