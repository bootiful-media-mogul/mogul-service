package com.joshlong.mogul.api.archives;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.function.Consumer;

public interface ArchiveExtractor {

	void extract(InputStream stream, Consumer<ArchiveFile> zipFileConsumer) throws Exception;

}
