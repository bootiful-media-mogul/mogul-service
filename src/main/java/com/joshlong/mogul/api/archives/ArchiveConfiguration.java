package com.joshlong.mogul.api.archives;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

}
