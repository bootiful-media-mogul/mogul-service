package com.joshlong.mogul.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * the two subprocess ceilings are the only thing standing between a burst of uploads and
 * a burst of {@code ffmpeg} processes inside the pod's memory limit. a binding that
 * quietly produced {@code 0} would not fail the context -- it would hand
 * {@code concurrencyLimit(0)} to the channel and stall every transcription and
 * normalization instead, so it is worth asserting they arrive.
 */
@SpringBootTest
class ConcurrencyPropertiesTest {

	@Autowired
	private ApiProperties properties;

	@Test
	void subprocessCeilingsAreBoundAndPositive() {
		assertThat(this.properties.transcripts().concurrency()).isPositive();
		assertThat(this.properties.media().normalization().concurrency()).isPositive();
	}

	@Test
	void subprocessCeilingsDefaultToFour() {
		assertThat(this.properties.transcripts().concurrency()).isEqualTo(4);
		assertThat(this.properties.media().normalization().concurrency()).isEqualTo(4);
	}

}
