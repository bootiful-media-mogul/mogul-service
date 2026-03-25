package com.joshlong.mogul.api.archives;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;

@Configuration
class ArchiveConfiguration {

	@Tgz
	@Bean
	TgzArchiveExtractor tgzArchiveExtractor() {
		return new TgzArchiveExtractor();
	}

	@Zip
	@Bean
	ZipArchiveExtractor zipArchiveExtractor() {
		return new ZipArchiveExtractor();
	}

	@Primary
	@Bean
	CompositeArchiveExtractor compositeArchiveExtractor(@Tgz ArchiveExtractor tgz, @Zip ArchiveExtractor zip) {
		return new CompositeArchiveExtractor(tgz, zip);
	}

}

class CompositeArchiveExtractor implements ArchiveExtractor {

	private final ArchiveExtractor tgz;

	private final ArchiveExtractor zip;

	CompositeArchiveExtractor(ArchiveExtractor tgz, ArchiveExtractor zip) {
		this.tgz = tgz;
		this.zip = zip;
	}

	@Override
	public void extract(InputStream stream, Consumer<ArchiveFile> zipFileConsumer) throws Exception {
		var mt = CommonMediaTypes.guess(stream);
		var handlers = Map.of(CommonMediaTypes.TGZ, tgz, CommonMediaTypes.ZIP, zip);
		for (var handler : handlers.entrySet()) {
			var mediaType = handler.getKey();
			var archiveExtractor = handler.getValue();
			if (mt.isCompatibleWith(mediaType)) {
				archiveExtractor.extract(stream, zipFileConsumer);
			}
		}
	}

}