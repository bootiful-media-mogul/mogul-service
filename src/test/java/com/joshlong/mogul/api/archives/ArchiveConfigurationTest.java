package com.joshlong.mogul.api.archives;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@SpringBootTest
class ArchiveConfigurationTest {

	@Test
	void compositeArchiveExtractor( //
			@Value("classpath:/samples/test.zip") Resource zip, //
			@Value("classpath:/samples/test.tar.gz") Resource tgz, //
			@Autowired ArchiveExtractor archiveExtractor) {
		for (var r : new Resource[] { zip, tgz }) {
			Assertions.assertTrue(r.exists(), "the resource " + r.getFilename() + " does not exist");
			Assertions.assertTrue(contains(archiveExtractor, r), "the archive extractor failed for " + r.getFilename());
		}
	}

	private boolean contains(ArchiveExtractor archiveExtractor, Resource resource) {
		var counter = new AtomicBoolean(false);
		try (var in = resource.getInputStream()) {
			archiveExtractor.extract(in, (Consumer<ArchiveFile>) it -> {
				if (it.fileName().equals("test.md"))
					counter.set(true);
			});
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		IO.println("counter: " + counter.get());
		return counter.get();
	}

}