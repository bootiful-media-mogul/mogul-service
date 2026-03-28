package com.joshlong.mogul.api.archives;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;

class CompositeArchiveReader implements ArchiveReader {

	private final ArchiveReader tgz;

	private final ArchiveReader zip;

	CompositeArchiveReader(ArchiveReader tgz, ArchiveReader zip) {
		this.tgz = tgz;
		this.zip = zip;
	}

	@Override
	public void read(InputStream stream, Consumer<ArchiveFile> zipFileConsumer) throws Exception {
		try (var inputStream = !stream.markSupported() ? new BufferedInputStream(stream) : stream) {
			var guessedMediaType = CommonMediaTypes.guess(inputStream);
			var handlers = Map.of(//
					CommonMediaTypes.TGZ, tgz, //
					CommonMediaTypes.ZIP, zip //
			);
			for (var handler : handlers.entrySet()) {
				var mediaType = handler.getKey();
				var archiveExtractor = handler.getValue();
				if (guessedMediaType.isCompatibleWith(mediaType)) {
					archiveExtractor.read(inputStream, zipFileConsumer);
				}
			}
		}
	}

}
