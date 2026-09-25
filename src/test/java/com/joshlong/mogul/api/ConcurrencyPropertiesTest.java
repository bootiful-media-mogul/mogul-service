package com.joshlong.mogul.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * the subprocess ceiling is the only thing standing between a burst of uploads and a
 * burst of {@code ffmpeg} processes inside the pod's memory limit. a binding that quietly
 * produced {@code 0} would not fail the context -- it would hand
 * {@code concurrencyLimit(0)} to the channel and stall every transcription instead, so it
 * is worth asserting it arrives. normalization's ceiling moved out with the work, and is
 * now the processors module's JobRunr worker count.
 */
@SpringBootTest
class ConcurrencyPropertiesTest {

	@Autowired
	private ApiProperties properties;

	@Test
	void subprocessCeilingsAreBoundAndPositive() {
		assertThat(this.properties.transcripts().concurrency()).isPositive();
	}

	@Test
	void subprocessCeilingsDefaultToFour() {
		assertThat(this.properties.transcripts().concurrency()).isEqualTo(4);
	}

}
