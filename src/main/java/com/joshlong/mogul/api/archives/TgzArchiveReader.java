package com.joshlong.mogul.api.archives;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.function.Consumer;

class TgzArchiveReader extends AbstractArchiveReader implements ArchiveReader {

	@Override
	public void read(InputStream stream, Consumer<ArchiveFile> zipFileConsumer) throws Exception {

		stream = this.validateMagicBytes(stream, new byte[] { (byte) 0x1f, (byte) 0x8b });

		var totalSize = 0L;
		var entryCount = 0;

		try (var gzi = new GzipCompressorInputStream(stream); var tar = new TarArchiveInputStream(gzi)) {
			var entry = (TarArchiveEntry) null;
			while ((entry = tar.getNextEntry()) != null) {
				if (++entryCount > this.getMaxEntries())
					throw new SecurityException("Too many entries in archive");

				this.shouldExitOnInsecureFile(entry.getName());

				try (var baos = new ByteArrayOutputStream()) {
					StreamUtils.copy(tar, baos);
					var bytes = baos.toByteArray();
					if (bytes.length > this.getMaxFileSize())
						throw new SecurityException("Entry too large: " + entry.getName());
					totalSize += bytes.length;
					if (totalSize > this.getMaxTotalSize())
						throw new SecurityException("Archive exceeds max uncompressed size");
					zipFileConsumer.accept(new ArchiveFile(entry.getName(), bytes));
				}
			}
		}
	}

}
