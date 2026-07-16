package com.joshlong.mogul.api.media;

import java.io.File;
import java.util.Map;

public class ImageEncodedFile implements EncodedFile {

	private final File file;

	public ImageEncodedFile(File file) {
		this.file = file;
	}

	@Override
	public File file() {
		return this.file;
	}

	@Override
	public Map<String, Object> context() {
		return Map.of();
	}

}
