package com.joshlong.mogul.api.managedfiles;

import com.joshlong.mogul.api.utils.FileUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URI;
import java.util.Date;

/**
 * represents a persistent, managed file stored on cloud storage
 */
public record ManagedFile(Long mogulId, Long id, String bucket, String storageFilename, String folder, String filename,
		Date created, boolean written, long size, String contentType) {

	public File uniqueLocalFile() {
		var extension = "";
		var periodIndex = filename().lastIndexOf('.');
		if (periodIndex != -1) {
			extension = filename().substring(periodIndex);
			if (StringUtils.hasText(extension) && extension.startsWith("."))
				extension = extension.substring(1);
		}
		return FileUtils.tempFile("managed-files-" + id, extension);
	}

	public URI s3Uri() {
		return URI.create("s3://" + this.bucket() + "/" + this.folder() + '/' + this.storageFilename());
	}
}
