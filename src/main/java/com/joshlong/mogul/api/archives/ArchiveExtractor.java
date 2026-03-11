package com.joshlong.mogul.api.archives;

import java.io.InputStream;
import java.util.function.Consumer;

public interface ArchiveExtractor {

	void extract(InputStream stream, Consumer<ArchiveFile> zipFileConsumer) throws Exception;

}
