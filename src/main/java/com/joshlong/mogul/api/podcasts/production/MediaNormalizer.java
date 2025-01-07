package com.joshlong.mogul.api.podcasts.production;

import com.joshlong.mogul.api.managedfiles.CommonMediaTypes;
import com.joshlong.mogul.api.managedfiles.ManagedFile;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import com.joshlong.mogul.api.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.function.Function;

@Component
public class MediaNormalizer {

	private final Logger log = LoggerFactory.getLogger(getClass());

	private final ImageEncoder imageEncoder;

	private final AudioEncoder audioEncoder;

	private final ManagedFileService managedFileService;

	MediaNormalizer(ImageEncoder imageEncoder, AudioEncoder audioEncoder, ManagedFileService managedFileService) {
		this.imageEncoder = imageEncoder;
		this.audioEncoder = audioEncoder;
		this.managedFileService = managedFileService;
	}

	public void normalize(ManagedFile input, ManagedFile output) throws Exception {

		var imgMediaType = CommonMediaTypes.IMAGE;
		var parseMediaType = MediaType.parseMediaType(input.contentType());
		var isImage = imgMediaType.isCompatibleWith(parseMediaType);
		var ext = isImage ? CommonMediaTypes.JPG : CommonMediaTypes.MP3;
		var encodingFunction = isImage ? (Function<File, File>) this.imageEncoder::encode
				: (Function<File, File>) this.audioEncoder::encode;
		var filesToDelete = new HashSet<File>(); // does this fix
		try {
			var localFile = input.uniqueLocalFile();
			filesToDelete.add(localFile);
			var resource = this.managedFileService.read(input.id());
			FileCopyUtils.copy(resource.getInputStream(), new FileOutputStream(localFile));
			var newFile = encodingFunction.apply(localFile);
			filesToDelete.add(newFile);
			this.managedFileService.write(output.id(), output.filename(), ext, new FileSystemResource(newFile));
		} //
		finally {
			for (var f : filesToDelete) {
				FileUtils.delete(f);
				if (f != null) {
					this.log.debug("deleting {} after file normalization.", f.getAbsolutePath());
				}
			}
		}
	}

}