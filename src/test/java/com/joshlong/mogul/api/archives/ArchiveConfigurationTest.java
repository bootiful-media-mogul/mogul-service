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
			@Autowired ArchiveReader archiveReader) {
		for (var r : new Resource[] { zip, tgz }) {
			var extractedSuccessfully = this.extract(archiveReader, r);
			Assertions.assertTrue(extractedSuccessfully, "the archive extractor failed for " + r.getFilename());
		}
	}

	private boolean extract(ArchiveReader archiveReader, Resource resource) {
		Assertions.assertTrue(resource.exists(), "the resource " + resource.getFilename() + " does not exist");
		var counter = new AtomicBoolean(false);
		try (var in = resource.getInputStream()) {
			archiveReader.read(in, (Consumer<ArchiveFile>) it -> {
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