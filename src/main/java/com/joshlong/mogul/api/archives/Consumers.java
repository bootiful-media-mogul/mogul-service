package com.joshlong.mogul.api.archives;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Consumer;

public abstract class Consumers {

	public static Consumer<ArchiveFile> readableTextOnly(Consumer<ArchiveFile> target) {
		var textPrefix = "text";
		return archiveFile -> {
			try (var in = new ByteArrayInputStream(archiveFile.content())) {
				var mediaType = CommonMediaTypes.guess(in, archiveFile.fileName());
				if (mediaType.getType().toLowerCase(Locale.ROOT).equals(textPrefix))
					target.accept(archiveFile);
			} //
			catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
	}

}
