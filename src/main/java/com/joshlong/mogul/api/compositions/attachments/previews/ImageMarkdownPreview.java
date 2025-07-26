package com.joshlong.mogul.api.compositions.attachments.previews;

import com.joshlong.mogul.api.compositions.Attachment;
import com.joshlong.mogul.api.managedfiles.ManagedFileService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ImageMarkdownPreview extends MediaTypeMarkdownPreview {

	private final static String NLS = System.lineSeparator() + System.lineSeparator();

	private final ManagedFileService managedFileService;

	ImageMarkdownPreview(ManagedFileService managedFileService) {
		super(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF);
		this.managedFileService = managedFileService;
	}

	@Override
	public String preview(Attachment attachment) {
		var managedFile = attachment.managedFile();
		var publicUrl = this.managedFileService.getPublicUrlForManagedFile(managedFile.id());
		var embedding = StringUtils.hasText(attachment.caption())
				? "![%s](%s)".formatted(attachment.caption(), publicUrl) : publicUrl;
		return NLS + embedding + NLS;
	}

}
