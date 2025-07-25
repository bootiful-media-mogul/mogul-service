package com.joshlong.mogul.api.media;

import com.joshlong.mogul.api.utils.FileUtils;
import com.joshlong.mogul.api.utils.ProcessUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Component
public class ImageEncoder implements Encoder {

	public final static DataSize MAX_SIZE = DataSize.ofMegabytes(1);

	private final Logger log = LoggerFactory.getLogger(ImageEncoder.class);

	@Override
	public File encode(File path) {
		try {
			var output = isValidImage(path) ? Files
				.copy(path.toPath(), new File(path.getParentFile(), "copy-" + UUID.randomUUID() + ".jpg").toPath())
				.toFile() : scale(convertFileToJpeg(path));
			Assert.state(isValidSize(output),
					"the stdout image [" + path.getAbsolutePath() + "] must be of the right file size");
			log.debug("in: {}{}out: {}{}", path.getAbsolutePath(), System.lineSeparator(), output.getAbsolutePath(),
					System.lineSeparator());
			return output;
		} //
		catch (Throwable throwable) {
			this.log.error(throwable.getMessage(), throwable);
			throw new RuntimeException(throwable);
		}
	}

	private boolean isValidSize(File in) {
		return (in.length() <= MAX_SIZE.toBytes());
	}

	private File convertFileToJpeg(File in) throws Exception {
		if (isValidType(in))
			return in;
		var converted = FileUtils.createRelativeTempFile(in, ".jpg");
		var convert = new ProcessBuilder()
			.command("magick", "convert", in.getAbsolutePath(), converted.getAbsolutePath())
			.start();
		Assert.state(convert.waitFor() == 0, "the process should exit normally");
		return converted;
	}

	private boolean isValidType(File in) {
		return in.getName().toLowerCase(Locale.ROOT).endsWith(".jpg");
	}

	private File scale(File file) throws Exception {
		var original = file.getAbsolutePath();
		var dest = FileUtils.createRelativeTempFile(file);
		var output = dest.getAbsolutePath();
		var quality = 100;
		var size = 0L;
		do {
			var convert = ProcessUtils.runCommand("magick", "convert", original, "-quality", String.valueOf(quality),
					output);
			Assert.state(convert == 0, "the convert command failed to run.");
			size = Files.size(Paths.get(output));
			quality -= 5;
		}
		while (size > MAX_SIZE.toBytes() && quality > 0);

		return dest;
	}

	private boolean isValidImage(File f) {
		return isValidSize(f) && isValidType(f);
	}

}
