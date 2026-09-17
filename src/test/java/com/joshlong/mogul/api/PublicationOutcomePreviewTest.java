package com.joshlong.mogul.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * the previews an outcome carries have to fit on one line, in one column, next to an icon
 * and a link. these are the cases where that goes wrong.
 */
class PublicationOutcomePreviewTest {

	private static final URI URI_1 = URI.create("https://example.com/status/1");

	private static String previewOf(String text) {
		var pc = PublisherPlugin.PublishContext.of(1L, new Object(), Map.of());
		pc.success("twitter", URI_1, text);
		return pc.outcomes().getFirst().preview();
	}

	@Test
	void shortTextSurvivesIntact() {
		assertEquals("hello, world", previewOf("hello, world"));
	}

	@Test
	void noPreviewGivenStaysNull() {
		var pc = PublisherPlugin.PublishContext.of(1L, new Object(), Map.of());
		pc.success("twitter", URI_1);
		assertNull(pc.outcomes().getFirst().preview());
		assertNull(previewOf(null));
		assertNull(previewOf("   "));
	}

	@Test
	void newlinesAndRunsOfWhitespaceCollapse() {
		// a tweet with paragraph breaks would otherwise preview as mostly blank.
		assertEquals("first line second line", previewOf("  first line\n\n\tsecond line  "));
	}

	@Test
	void longTextIsCutAtAWordBoundaryAndEllipsized() {
		var word = "spring";
		var text = (word + " ").repeat(40);
		var preview = previewOf(text);
		assertTrue(preview.length() <= PublisherPlugin.PublishContext.PREVIEW_LENGTH + 1,
				"the preview is at most PREVIEW_LENGTH plus the ellipsis, was " + preview.length());
		assertTrue(preview.endsWith("…"), "a truncated preview ends in an ellipsis");
		assertTrue(preview.startsWith(word + " " + word), "the preview keeps the front of the text");
		assertFalse(preview.contains("sprin…"), "the cut lands between words, not inside one");
	}

	@Test
	void oneUnbrokenWordIsCutMidWordRatherThanKeptWhole() {
		// a URL with no spaces in it: there's no word boundary to prefer, and the whole
		// thing is exactly what must not reach the column.
		var url = "https://example.com/" + "x".repeat(300);
		var preview = previewOf(url);
		assertTrue(preview.length() <= PublisherPlugin.PublishContext.PREVIEW_LENGTH + 1);
		assertTrue(preview.endsWith("…"));
	}

	@Test
	void textExactlyAtTheLimitIsNotEllipsized() {
		var text = "a".repeat(PublisherPlugin.PublishContext.PREVIEW_LENGTH);
		assertEquals(text, previewOf(text));
	}

	@Test
	void failuresCanCarryAPreviewToo() {
		var pc = PublisherPlugin.PublishContext.of(1L, new Object(), Map.of());
		pc.failure("twitter", "no oauth credentials", "the tweet that didn't go out");
		var outcome = pc.outcomes().getFirst();
		assertFalse(outcome.success());
		assertEquals("the tweet that didn't go out", outcome.preview());
	}

}
