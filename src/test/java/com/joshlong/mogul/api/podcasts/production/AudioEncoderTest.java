package com.joshlong.mogul.api.podcasts.production;

import com.joshlong.mogul.api.media.AudioEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;

class AudioEncoderTest {

	private final AudioEncoder encoder = new AudioEncoder();

	@Test
	void transcodeWavToMp3s() throws Exception {
		var output = (File) null;
		try {
			var input = new ClassPathResource("/samples/input.wav");
			var wav = Files.createTempFile("input", ".wav").toFile();
			FileCopyUtils.copy(input.getInputStream(), new FileOutputStream(wav));
			Assert.state(wav.exists(), "the .wav file exists");
			output = encoder.encode(wav);
			Assert.state(output.length() < wav.length(), "the new file should be a _lot_ smaller than the original!");
			Assert.state(output.getName().endsWith(".mp3"), "the new file should be an .mp3");
		}
		finally {
			if (null != output)
				output.delete();
		}

	}

}