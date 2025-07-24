package com.joshlong.mogul.api.managedfiles;

import com.joshlong.mogul.api.utils.FileUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.Date;

import static com.joshlong.mogul.api.managedfiles.DefaultManagedFileService.visibleBucketFor;

public record ManagedFile(Long mogulId, Long id, String bucket, String storageFilename, String folder, String filename,
		Date created, boolean written, long size, String contentType, boolean visible) {

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

	public String visibleBucket() {
		return visibleBucketFor(this.bucket());
	}

}