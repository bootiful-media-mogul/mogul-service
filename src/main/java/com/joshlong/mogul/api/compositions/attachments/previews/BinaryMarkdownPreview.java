package com.joshlong.mogul.api.compositions.attachments.previews;

import com.joshlong.mogul.api.compositions.Attachment;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

//@Component
class BinaryMarkdownPreview extends MediaTypeMarkdownPreview {

	private final ManagedFileService managedFileService;

	private final Logger log = LoggerFactory.getLogger(getClass());

	BinaryMarkdownPreview(ManagedFileService managedFileService) {
		super(MediaType.APPLICATION_OCTET_STREAM);
		this.managedFileService = managedFileService;
	}

	@Override
	public String preview(Attachment payload) {
		this.log.info("previewing {} as a {}", payload.managedFile().id(),
				this.mediaTypes.length > 0 ? this.mediaTypes[0].toString() : null);
		return this.managedFileService.getPublicUrlForManagedFile(payload.managedFile().id());
	}

}
