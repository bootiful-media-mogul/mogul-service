package com.joshlong.mogul.api.managedfiles;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

class CommonMediaTypesTest {

	@Test
	void testAudio() {
		this.test(this.audio(), CommonMediaTypes::isAudio);
	}

	@Test
	void testImage() {
		this.test(this.image(), CommonMediaTypes::isImage);
	}

	@Test
	void testText() {
		this.test(this.text(), CommonMediaTypes::isText);
	}

	private void test(InputStream resource, Predicate<MediaType> predicate) {
		var mediaType = CommonMediaTypes.guess(resource);
		var testResult = predicate.test(mediaType);
		Assertions.assertTrue(testResult);
	}

	private InputStream text() {
		var text = "# hello world! \n\nthis is a new paragraph".getBytes(StandardCharsets.UTF_8);
		return new ByteArrayInputStream(text); // supports marking as well
	}

	private InputStream image() {
		return from(new ClassPathResource("/samples/sample-picture.png"));
	}

	private InputStream audio() {
		return from(new ClassPathResource("/samples/input.wav"));
	}

	private InputStream from(Resource resource) {
		try {
			return new BufferedInputStream(resource.getInputStream()); // supports marking
			// which tika will
			// honor
		} //
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}