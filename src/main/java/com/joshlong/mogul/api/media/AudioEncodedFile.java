package com.joshlong.mogul.api.media;

import java.io.File;
import java.util.Map;

public record AudioEncodedFile(File file, float millisecondsDuration) implements EncodedFile {

	@Override
	public Map<String, Object> context() {
		return Map.of("duration", this.millisecondsDuration);
	}

}
