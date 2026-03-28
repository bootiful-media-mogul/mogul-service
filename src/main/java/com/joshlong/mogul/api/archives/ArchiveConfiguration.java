package com.joshlong.mogul.api.archives;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
class ArchiveConfiguration {

	@Primary
	@Bean
	CompositeArchiveReader compositeArchiveExtractor(@Tgz ArchiveReader tgz, @Zip ArchiveReader zip) {
		return new CompositeArchiveReader(tgz, zip);
	}

	@Tgz
	@Bean
	TgzArchiveReader tgzArchiveExtractor() {
		return new TgzArchiveReader();
	}

	@Zip
	@Bean
	ZipArchiveReader zipArchiveExtractor() {
		return new ZipArchiveReader();
	}

}
