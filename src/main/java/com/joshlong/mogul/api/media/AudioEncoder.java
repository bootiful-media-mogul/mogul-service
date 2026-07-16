package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

// todo refactor so that this type can be package private.
@Component
public class AudioEncoder implements Encoder<AudioEncodedFile> {

	private final Logger log = LoggerFactory.getLogger(getClass());

	@Override
	public AudioEncodedFile encode(File input) {
		try {
			var inputAbsolutePath = input.getAbsolutePath();
			this.log.debug("absolute path of audio file to encode: {}", inputAbsolutePath);
			Assert.state(input.exists() && input.isFile(),
					"the input ['" + inputAbsolutePath + "'] must be a valid, existing file");
			var mp3Ext = "mp3";
			// if (inputAbsolutePath.toLowerCase().endsWith(mp3Ext))
			// return input;
			var mp3 = FileUtils.createRelativeTempFile(input, "." + mp3Ext);
			var mp3AbsolutePath = mp3.getAbsolutePath();
			this.log.debug("mp3: {}", mp3AbsolutePath);
			// this fixed #113
			var exit = Runtime.getRuntime()
				.exec(new String[] { "ffmpeg", "-i", inputAbsolutePath, "-ar", "48000", "-ac", "2", "-c:a",
						"libmp3lame", "-b:a", "192k", mp3AbsolutePath })
				.waitFor();
			Assert.state(exit == 0, "the ffmpeg command ran successfully");
			var durationMs = this.getMp3DurationMs(mp3AbsolutePath);
			return new AudioEncodedFile(mp3, durationMs);
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private long getMp3DurationMs(String filePath) throws IOException, InterruptedException {
		var pb = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of",
				"default=noprint_wrappers=1:nokey=1:csv_strict=1", filePath)
			.redirectErrorStream(true);

		var process = pb.start();
		// Read the output
		try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var output = reader.readLine();
			reader.close();

			// Wait for process to complete
			var exitCode = process.waitFor();

			if (exitCode != 0) {
				throw new IOException("ffprobe command failed with exit code: " + exitCode);
			}

			if (output == null || output.trim().isEmpty()) {
				throw new IOException("No duration output from ffprobe");
			}
			// Parse the duration (in seconds) and convert to milliseconds
			try {
				var durationSeconds = Double.parseDouble(output.trim());
				return Math.round(durationSeconds * 1000);
			}
			catch (NumberFormatException e) {
				throw new IOException("Failed to parse duration: " + output, e);
			}
		}
	}

}
