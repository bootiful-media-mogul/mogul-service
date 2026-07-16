package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

import java.io.*;

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
			var durationMs = this.durationInMilliseconds(mp3AbsolutePath);
			return new AudioEncodedFile(mp3, durationMs);
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private float durationInMilliseconds(String file) throws Exception {
		var pb = new ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
				file)
			.redirectErrorStream(true)
			.start();
		var exitCode = pb.waitFor();
		Assert.state(exitCode == 0, "ffprobe failed");
		try (var i = new BufferedReader(new InputStreamReader(pb.getInputStream())); var o = new StringWriter()) {
			FileCopyUtils.copy(i, o);
			return Float.parseFloat(o.toString()) * 1000;// milliseconds
		}
	}

}
