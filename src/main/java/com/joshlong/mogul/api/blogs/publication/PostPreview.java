package com.joshlong.mogul.api.blogs.publication;

import com.joshlong.mogul.api.blogs.Post;
import com.joshlong.mogul.api.managedfiles.ManagedFile;

/**
 * the user might want to preview the contents of their blog post
 */
public record PostPreview( Long publicationId,Long id, Post post, ManagedFile managedFile) {
}
