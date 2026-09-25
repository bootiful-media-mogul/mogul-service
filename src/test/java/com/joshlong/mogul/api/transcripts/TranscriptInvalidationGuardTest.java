package com.joshlong.mogul.api.transcripts;

import com.joshlong.mogul.api.Transcript;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * media normalization happens over an at-least-once queue now, so a reply can arrive
 * twice and the completion gets replayed. everything else downstream of that converges on
 * its own; transcription is the one thing that doesn't, and it is the most expensive
 * thing in the system to do by accident.
 */
class TranscriptInvalidationGuardTest {

	private static Transcript transcriptOf(String sourceEtag) {
		return new Transcript(1L, 1L, null, null, "1", null, "some text", sourceEtag);
	}

	@Test
	void theSameAudioAnnouncedTwiceIsNotTranscribedTwice() {
		assertThat(DefaultTranscriptService.alreadyDispatchedFor("\"abc123\"", transcriptOf("\"abc123\""))).isTrue();
	}

	@Test
	void replacedAudioIsAlwaysTranscribedAgain() {
		// the case the guard must not break: a mogul re-uploads, the bytes change, and a
		// transcript of the old recording is worse than none.
		assertThat(DefaultTranscriptService.alreadyDispatchedFor("\"def456\"", transcriptOf("\"abc123\""))).isFalse();
	}

	@Test
	void aTranscriptWeKnowNothingAboutIsTranscribed() {
		// every row that predates the etag column, and every file whose HEAD we couldn't
		// read. the old behaviour, which is to just do the work.
		assertThat(DefaultTranscriptService.alreadyDispatchedFor("\"abc123\"", transcriptOf(null))).isFalse();
	}

	@Test
	void anExplicitRequestIsNeverGuardedAway() {
		// a null etag is the caller saying "regardless" -- and must not NPE on its way to
		// saying so.
		assertThat(DefaultTranscriptService.alreadyDispatchedFor(null, transcriptOf("\"abc123\""))).isFalse();
		assertThat(DefaultTranscriptService.alreadyDispatchedFor(null, transcriptOf(null))).isFalse();
		assertThat(DefaultTranscriptService.alreadyDispatchedFor("\"abc123\"", null)).isFalse();
	}

}
