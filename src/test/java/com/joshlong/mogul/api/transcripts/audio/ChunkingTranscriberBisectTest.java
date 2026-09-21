package com.joshlong.mogul.api.transcripts.audio;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ChunkingTranscriberBisectTest {

	private static final String SAMPLE = "/samples/sample-segment-1.mp3";

	private static final Duration SAMPLE_DURATION = Duration.ofSeconds(734);

	private static File sample;

	@BeforeAll
	static void ffmpegIsInstalled() throws Exception {
		assumeTrue(commandExists("ffmpeg"), "ffmpeg is not on the PATH");
		assumeTrue(commandExists("ffprobe"), "ffprobe is not on the PATH");
		var resource = new ClassPathResource(SAMPLE);
		assumeTrue(resource.exists(), SAMPLE + " is not on the classpath");
		// copy out of the classpath once: the resource may live inside a jar, and
		// ffmpeg needs a real path on disk.
		sample = File.createTempFile("sample-segment", ".mp3");
		sample.deleteOnExit();
		try (var in = resource.getInputStream(); var out = new FileOutputStream(sample)) {
			in.transferTo(out);
		}
	}

	private static double durationOf(File file) {
		try {
			var process = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of",
					"csv=p=0", file.getAbsolutePath())
				.redirectErrorStream(true)
				.start();
			String out;
			try (var stdout = process.getInputStream()) {
				out = new String(stdout.readAllBytes(), StandardCharsets.UTF_8).trim();
			}
			assertThat(process.waitFor()).isZero();
			return Double.parseDouble(out);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static boolean commandExists(String command) throws Exception {
		var process = new ProcessBuilder(command, "-version").redirectErrorStream(true).start();
		try (var stdout = process.getInputStream()) {
			stdout.readAllBytes();
		}
		return process.waitFor() == 0;
	}

	@Test
	void bisectExtractsTheRequestedRange(@TempDir Path tmp) {
		var destination = tmp.resolve("segment.mp3").toFile();
		assertTimeoutPreemptively(Duration.ofMinutes(2), () -> ChunkingTranscriber.bisect(sample, destination,
				TimeUnit.SECONDS.toMillis(60), TimeUnit.SECONDS.toMillis(90)));
		assertThat(destination).exists().isNotEmpty();
		// -c copy cuts on frame boundaries, so the segment lands near, not exactly on,
		// the requested 30 seconds.
		assertThat(durationOf(destination)).isCloseTo(30d, org.assertj.core.data.Offset.offset(1.5d));
	}

	/**
	 * cutting from the tail forces ffmpeg to seek right through the whole input.
	 */
	@Test
	void bisectCutsFromTheEndOfALongInput(@TempDir Path tmp) {
		var destination = tmp.resolve("late-segment.mp3").toFile();
		var start = SAMPLE_DURATION.minusSeconds(120).toMillis();
		var stop = SAMPLE_DURATION.minusSeconds(60).toMillis();
		assertTimeoutPreemptively(Duration.ofMinutes(2),
				() -> ChunkingTranscriber.bisect(sample, destination, start, stop));
		assertThat(destination).exists().isNotEmpty();
		assertThat(durationOf(destination)).isCloseTo(60d, org.assertj.core.data.Offset.offset(1.5d));
	}

	@Test
	void everySegmentOfAFullPassIsProduced(@TempDir Path tmp) {
		// walk the file the way transcription does -- several consecutive cuts in one
		// run -- so a subprocess that leaks or blocks shows up on the second or third
		// call rather than the first.
		var chunk = TimeUnit.MINUTES.toMillis(2);
		assertTimeoutPreemptively(Duration.ofMinutes(5), () -> {
			for (var i = 0; i < 4; i++) {
				var destination = tmp.resolve("part-" + i + ".mp3").toFile();
				ChunkingTranscriber.bisect(sample, destination, i * chunk, (i + 1) * chunk);
				assertThat(destination).exists().isNotEmpty();
			}
		});
	}

	@Test
	void aFailingBisectExplainsItself(@TempDir Path tmp) {
		var missing = tmp.resolve("nope.mp3").toFile();
		var destination = tmp.resolve("out.mp3").toFile();
		// the one assertion here that fails against the pre-fix implementation, which
		// reported "[254]" and nothing else.
		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> ChunkingTranscriber.bisect(missing, destination, 0, 1_000))
			.withMessageContaining("zero exit code")
			.withMessageContaining("No such file or directory");
	}

}
