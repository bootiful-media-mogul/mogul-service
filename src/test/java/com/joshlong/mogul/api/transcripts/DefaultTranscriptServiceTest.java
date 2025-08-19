package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.ApiApplication;
import com.joshlong.mogul.api.podcasts.Segment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ApiApplication.class)
class DefaultTranscriptServiceTest {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Test
	void transcribable_should_return_transcript(@Autowired TranscriptService transcriptService) throws Exception {

		var segment = transcriptService.transcribable(1586L, Segment.class);
		Assertions.assertNotNull(segment);

		var text = transcriptService.readTranscript(segment.audio().mogulId(), segment);
		Assertions.assertNotNull(text);
		Assertions.assertFalse(text.isBlank(), "the text of the transcript should not be null or empty");

		this.log.debug("transcript text: {}", text);

	}

}