package com.joshlong.mogul.api.archives;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

class ReadableTextOnlyConsumer implements Consumer<ArchiveFile> {

	private final Consumer<ArchiveFile> target;

	public ReadableTextOnlyConsumer(Consumer<ArchiveFile> target) {
		this.target = target;
	}

	private boolean isNonReadableText(byte[] bytes) {
		var content = new String(bytes, StandardCharsets.UTF_8);
		var nonPrintable = content.chars().filter(c -> c < 32 && c != '\n' && c != '\r' && c != '\t').count();
		return !(nonPrintable < bytes.length * 0.01);
	}

	@Override
	public void accept(ArchiveFile archiveFile) {
		if (isNonReadableText(archiveFile.content()))
			return;
		this.target.accept(archiveFile);
	}

}
