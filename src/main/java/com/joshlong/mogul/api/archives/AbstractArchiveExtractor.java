package com.joshlong.mogul.api.archives;

import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;

abstract class AbstractArchiveExtractor implements ArchiveExtractor {

	protected static final long MAX_TOTAL_SIZE = 1_000 * 1024 * 1024;

	protected static final long MAX_FILE_SIZE = 1_00 * 1024 * 1024;

	protected static final int MAX_ENTRIES = 100_000;

	private final int maxEntries;

	private final long maxFileSize;

	private final long maxTotalSize;

	AbstractArchiveExtractor() {
		this(MAX_ENTRIES, MAX_FILE_SIZE, MAX_TOTAL_SIZE);
	}

	AbstractArchiveExtractor(int maxEntries, long maxFileSize, long maxTotalSize) {
		this.maxEntries = maxEntries;
		this.maxFileSize = maxFileSize;
		this.maxTotalSize = maxTotalSize;
	}

	protected int getMaxEntries() {
		return maxEntries;
	}

	protected long getMaxFileSize() {
		return maxFileSize;
	}

	protected long getMaxTotalSize() {
		return maxTotalSize;
	}

	protected InputStream validateMagicBytes(InputStream stream, byte[] expected) throws IOException {
		var pis = new PushbackInputStream(stream, expected.length);
		var header = new byte[expected.length];
		pis.read(header);
		if (!Arrays.equals(header, expected))
			throw new IllegalArgumentException("Invalid archive format");
		pis.unread(header); // put the bytes back so the downstream reader sees them
		return pis;
	}

	protected void shouldExitOnInsecureFile(String name) {
		Assert.state(!name.contains(".."), "the file " + name + " contains a path traversal");
	}

}
