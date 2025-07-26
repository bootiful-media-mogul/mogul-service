package com.joshlong.mogul.api.compositions.attachments.previews;

import com.joshlong.mogul.api.compositions.Attachment;

/**
 * previews a {@link com.joshlong.mogul.api.managedfiles.ManagedFile managed file as
 * Markdown}. I don't love this hierarchy name to be honest.
 */
public interface MarkdownPreview {

	boolean supports(Attachment payload);

	String preview(Attachment payload);

}
