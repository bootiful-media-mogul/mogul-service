package com.joshlong.mogul.api.compositions.attachments.previews;

import com.joshlong.mogul.api.compositions.Attachment;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

abstract class MediaTypeMarkdownPreview implements MarkdownPreview {

	protected final MediaType[] mediaTypes;

	MediaTypeMarkdownPreview(MediaType... mediaTypes) {
		this.mediaTypes = mediaTypes;
	}

	@Override
	public boolean supports(Attachment payload) {
		var contentType = payload.managedFile().contentType();
		Assert.hasText(contentType, "the content type must be non-null");
		var mt = MediaType.parseMediaType(payload.managedFile().contentType());
		for (var supported : this.mediaTypes)
			if (supported.isCompatibleWith(mt))
				return true;
		return false;
	}

	@Override
	public abstract String preview(Attachment payload);

}
