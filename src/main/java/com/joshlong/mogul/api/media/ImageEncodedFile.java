package com.joshlong.mogul.api.media;

import java.io.File;
import java.util.Map;

public record ImageEncodedFile(File file) implements EncodedFile {

	@Override
	public Map<String, Object> context() {
		return Map.of();
	}

}
