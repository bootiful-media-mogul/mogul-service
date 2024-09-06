package com.joshlong.mogul.api.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CliHealthIndicators {

	@Bean
	CliHealthIndicator magick() {
		return new CliHealthIndicator(new String[] { "magick", "--version" }, "imagemagick");
	}

	@Bean
	CliHealthIndicator ffmpeg() {
		return new CliHealthIndicator(new String[] { "ffmpeg", "-version" }, "ffmpeg");
	}

}
