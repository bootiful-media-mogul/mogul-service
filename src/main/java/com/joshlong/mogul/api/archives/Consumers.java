package com.joshlong.mogul.api.archives;

import java.util.function.Consumer;

public abstract class Consumers {

	public static Consumer<ArchiveFile> readableTextOnly(Consumer<ArchiveFile> target) {
		return new ReadableTextOnlyConsumer(target);
	}

}
